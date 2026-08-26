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
        LocalDateTime lastSeen = context.server().getLastSeenAt();
        boolean returned = lastSeen != null && lastSeen.isAfter(rebootAt);
        if (!returned) {
            if (timeoutPolicy.isExpired(rebootAt, timeoutPolicy.returnLimit(), context.now())) {
                fail(context, row, SettingLedger.RETURN_TIMEOUT, "재부팅 뒤 시한 안에 게스트가 돌아오지 않았습니다");
            }
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

    private void fail(SettingContext context, ProvisioningHistory row, String reason, String detail) {
        ledger.close(row, ProvisioningStatus.FAILED, reason, detail, context.now());
        context.progress().markFailed(context.now());
        log.warn("[setting] {} — {} : {}", context.server().getId(), reason, detail);
    }
}
