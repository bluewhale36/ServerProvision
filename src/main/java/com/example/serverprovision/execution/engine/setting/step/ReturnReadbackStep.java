package com.example.serverprovision.execution.engine.setting.step;

import com.example.serverprovision.execution.engine.firmware.BmcIdentity;
import com.example.serverprovision.execution.engine.firmware.FlashTimeoutPolicy;
import com.example.serverprovision.execution.engine.setting.SettingAxis;
import com.example.serverprovision.execution.engine.setting.SettingCursor;
import com.example.serverprovision.execution.engine.setting.SettingLedger;
import com.example.serverprovision.execution.entity.ProvisioningHistory;
import com.example.serverprovision.execution.enums.ProvisioningStatus;
import com.example.serverprovision.execution.service.BmcIdentityProbe;
import com.example.serverprovision.global.redfish.PowerControlResult;
import com.example.serverprovision.global.redfish.RedfishBiosService;
import com.example.serverprovision.global.redfish.RedfishPowerService;
import com.example.serverprovision.global.redfish.RedfishRequestException;
import com.example.serverprovision.global.redfish.RedfishTarget;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 1행 — 재부팅을 걸어 둔 행의 결과를 거둔다(E3-1 D-4). 복귀 신호는 <b>게스트 접촉 시각</b>(rebootAt 이후) —
 * BMC 의 Bios 리소스가 POST 후 언제 갱신되는지는 미실측이라 BMC 폴링만으로는 이르게 읽을 수 있다.
 * readback 직전에 신원을 대조하고(D-6), 대조 기준은 원장에 적어 둔 목표다(F-1 교훈 — 컨텍스트가 아니라 원장).
 *
 * <p>HF17 — 설정 적용의 내부 재시작이 Once 무장을 첫 POST 에서 소비하면(실기 3호 F-2) 두 번째 POST 는 부트 순서대로
 * 부팅해 게스트가 돌아오지 않는다. 그래서 재부팅 발행 뒤 {@link FlashTimeoutPolicy#pxeRearmDelay()} 안에 {@code /boot} 가
 * 없으면 Once 를 다시 세워 한 번 더 켠다 — 행마다 한 번, 이후는 복귀 시한까지 기다린다. 우리가 믿는 신호는 {@code /boot}
 * 도착 하나이므로(F-4 교훈 — 재부팅 중 Redfish 되읽기는 이르게 읽힌다) 두 번째 POST 를 따로 관측하지 않는다.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ReturnReadbackStep implements SettingStep {

    private final SettingLedger ledger;
    private final BmcIdentityProbe identityProbe;
    private final RedfishBiosService biosService;
    private final RedfishPowerService powerService;
    private final FlashTimeoutPolicy timeoutPolicy;
    private final SettingCursor settingCursor;

    @Override
    public int order() {
        return 1;
    }

    @Override
    public boolean matches(SettingContext context) {
        return context.axis() == SettingAxis.BIOS
                && context.runningRow().map(row -> ledger.rebootAtOf(row) != null).orElse(false);
    }

    @Override
    public void execute(SettingContext context) {
        ProvisioningHistory row = context.runningRow().orElseThrow();
        LocalDateTime rebootAt = ledger.rebootAtOf(row);
        // 복귀 증거 = 재부팅 발행 뒤의 /boot 도착(HF15-1 · 실기 3호 F-4). 진단 리눅스의 30초 체크인 폴링은 접촉이지 재부팅이
        // 아니다 — BMC 리셋이 늦은 서버에서 그 폴링을 복귀로 읽어 미반영 판독(READBACK_MISMATCH)을 냈다.
        LocalDateTime lastBoot = context.server().getLastBootAt();
        boolean returned = lastBoot != null && lastBoot.isAfter(rebootAt);
        if (!returned) {
            if (timeoutPolicy.isExpired(rebootAt, timeoutPolicy.returnLimit(), context.now())) {
                fail(context, row, SettingLedger.RETURN_TIMEOUT, "재부팅 뒤 시한 안에 게스트가 돌아오지 않았습니다");
                return;
            }
            rearmIfOverdue(context, row, rebootAt);
            return;
        }
        RedfishTarget target = context.redfishTarget();
        BmcIdentity identity = identityProbe.probe(context.provider(), target,
                context.detail().getBoardSerial(), context.detail(), "setting");
        if (identity == BmcIdentity.MISMATCHED) {
            fail(context, row, SettingLedger.IDENTITY_MISMATCH, "응답한 장비의 보드 시리얼이 이 서버와 다릅니다");
            return;
        }
        if (identity == BmcIdentity.UNREACHABLE) {
            if (timeoutPolicy.isExpired(rebootAt, timeoutPolicy.returnLimit(), context.now())) {
                fail(context, row, SettingLedger.BMC_UNREACHABLE, "BMC 에 닿지 못했고 새 주소도 찾지 못했습니다");
            }
            return;
        }
        JsonNode attributes;
        try {
            attributes = biosService.bios(target).body().path("Attributes");
        } catch (RedfishRequestException e) {
            log.info("[setting] {} — readback 응답 없음, 다음 주기 재시도 : {}", context.server().getId(), e.getMessage());
            return;
        }
        Map<String, Object> expected = ledger.targetOf(row);
        List<String> mismatched = expected.entrySet().stream()
                .filter(e -> !ReadbackValues.same(attributes.get(e.getKey()), e.getValue()))
                .map(Map.Entry::getKey)
                .toList();
        if (mismatched.isEmpty()) {
            ledger.close(row, ProvisioningStatus.SUCCEEDED, SettingLedger.APPLIED,
                    expected.size() + "개 속성 반영 확인", context.now());
            settingCursor.afterAxis(SettingAxis.BIOS, context.progress(), context.server().getId(), context.now());
            log.info("[setting] {} — BIOS 설정 {}개 반영 확인, 다음 축으로", context.server().getId(), expected.size());
            return;
        }
        fail(context, row, SettingLedger.READBACK_MISMATCH, "반영되지 않은 속성: " + String.join(", ", mismatched));
    }

    /**
     * 미도착 재무장(HF17) — rebootAt 뒤 재무장 지연이 지났고 이 행이 아직 재무장하지 않았으면 Once 를 다시 세워 켠다.
     * 전원을 움직이는 조작이라 신원을 먼저 본다(D-6). 명령 실패는 다음 주기가 다시 시도하고, 성공만 행에 적는다(한 번 규칙).
     */
    private void rearmIfOverdue(SettingContext context, ProvisioningHistory row, LocalDateTime rebootAt) {
        if (ledger.rearmAtOf(row) != null) {
            return;
        }
        Duration delay = timeoutPolicy.pxeRearmDelay();
        if (!timeoutPolicy.isExpired(rebootAt, delay, context.now())) {
            return;
        }
        RedfishTarget target = context.redfishTarget();
        BmcIdentity identity = identityProbe.probe(context.provider(), target,
                context.detail().getBoardSerial(), context.detail(), "setting");
        if (identity == BmcIdentity.MISMATCHED) {
            fail(context, row, SettingLedger.IDENTITY_MISMATCH, "응답한 장비의 보드 시리얼이 이 서버와 다릅니다");
            return;
        }
        if (identity == BmcIdentity.UNREACHABLE) {
            log.info("[setting] {} — PXE 미도착 재무장 보류(BMC 에 닿지 못함) — 다음 주기", context.server().getId());
            return;
        }
        PowerControlResult result = powerService.networkBoot(target);
        if (result.kind() == PowerControlResult.Kind.FAILED) {
            log.info("[setting] {} — PXE 미도착 재무장 실패, 다음 주기 재시도 : {}", context.server().getId(), result.message());
            return;
        }
        ledger.markRearmed(row, context.now(), result.message());
        log.info("[setting] {} — 재부팅 뒤 {}분 동안 PXE 미도착 → Once 재무장 + 재시작(F-2 이중 POST 대비) : {}",
                context.server().getId(), delay.toMinutes(), result.message());
    }

    private void fail(SettingContext context, ProvisioningHistory row, String reason, String detail) {
        ledger.close(row, ProvisioningStatus.FAILED, reason, detail, context.now());
        context.progress().markFailed(context.now());
        log.warn("[setting] {} — {} : {}", context.server().getId(), reason, detail);
    }
}
