package com.example.serverprovision.execution.engine.setting.step;

import com.example.serverprovision.execution.engine.firmware.BmcIdentity;
import com.example.serverprovision.execution.engine.firmware.FlashTimeoutPolicy;
import com.example.serverprovision.execution.engine.setting.SettingAxis;
import com.example.serverprovision.execution.engine.setting.SettingCursor;
import com.example.serverprovision.execution.engine.setting.SettingLedger;
import com.example.serverprovision.execution.entity.ProvisioningHistory;
import com.example.serverprovision.execution.enums.ProvisioningStatus;
import com.example.serverprovision.execution.service.BmcIdentityProbe;
import com.example.serverprovision.global.redfish.RedfishBiosService;
import com.example.serverprovision.global.redfish.RedfishRequestException;
import com.example.serverprovision.global.redfish.RedfishTarget;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 1행 — 재부팅을 걸어 둔 행의 결과를 거둔다(E3-1 D-4). 복귀 신호는 <b>게스트 접촉 시각</b>(rebootAt 이후) —
 * BMC 의 Bios 리소스가 POST 후 언제 갱신되는지는 미실측이라 BMC 폴링만으로는 이르게 읽을 수 있다.
 * readback 직전에 신원을 대조하고(D-6), 대조 기준은 원장에 적어 둔 목표다(F-1 교훈 — 컨텍스트가 아니라 원장).
 *
 * <p>HF24 — 미도착 재무장을 없앴다. 설정 적용의 내부 재시작이 끝날 때까지 끊지 않고 복귀 시한까지 기다린다. 우리가 믿는
 * 신호는 {@code /boot} 도착 하나다(F-4 교훈 — 재부팅 중 Redfish 되읽기는 이르게 읽힌다).</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ReturnReadbackStep implements SettingStep {

    private final SettingLedger ledger;
    private final BmcIdentityProbe identityProbe;
    private final RedfishBiosService biosService;
    private final FlashTimeoutPolicy timeoutPolicy;
    private final SettingCursor settingCursor;
    private final SettingFailure failure;

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
        // 복귀 증거 = 재부팅 발행 뒤의 /boot 도착(HF15-1 · 실기 3호 F-4) — 진단 리눅스의 체크인은 접촉이지 재부팅이 아니다.
        // 설정 단계 동안 게스트는 iPXE 대기 루프에서 30초마다 /boot 를 부른다. 재시작 명령 전의 그 /boot 가 복귀로 읽히지
        // 않도록 rebootAt 은 재시작 명령이 성공한 뒤의 시각이다(HF25 — BeginSettingStep). 재시작 뒤 POST 동안에는 /boot 가 없다.
        LocalDateTime lastBoot = context.server().getLastBootAt();
        boolean returned = lastBoot != null && lastBoot.isAfter(rebootAt);
        if (!returned) {
            if (timeoutPolicy.isExpired(rebootAt, timeoutPolicy.returnLimit(), context.now())) {
                failure.close(context, row, SettingLedger.RETURN_TIMEOUT, "재부팅 뒤 시한 안에 게스트가 돌아오지 않았습니다");
            }
            return;
        }
        RedfishTarget target = context.redfishTarget();
        BmcIdentity identity = identityProbe.probe(context.provider(), target,
                context.detail().getBoardSerial(), context.detail(), "setting");
        if (identity == BmcIdentity.MISMATCHED) {
            failure.close(context, row, SettingLedger.IDENTITY_MISMATCH, "응답한 장비의 보드 시리얼이 이 서버와 다릅니다");
            return;
        }
        if (identity == BmcIdentity.UNREACHABLE) {
            if (timeoutPolicy.isExpired(rebootAt, timeoutPolicy.returnLimit(), context.now())) {
                failure.close(context, row, SettingLedger.BMC_UNREACHABLE, "BMC 에 닿지 못했고 새 주소도 찾지 못했습니다");
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
        failure.close(context, row, SettingLedger.READBACK_MISMATCH, "반영되지 않은 속성: " + String.join(", ", mismatched));
    }
}
