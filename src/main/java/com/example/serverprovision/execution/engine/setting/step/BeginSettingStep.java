package com.example.serverprovision.execution.engine.setting.step;

import com.example.serverprovision.execution.engine.firmware.BmcIdentity;
import com.example.serverprovision.execution.engine.firmware.FlashTimeoutPolicy;
import com.example.serverprovision.execution.engine.setting.SettingAxis;
import com.example.serverprovision.execution.engine.setting.SettingLedger;
import com.example.serverprovision.execution.entity.ProvisioningHistory;
import com.example.serverprovision.execution.enums.ProvisioningStatus;
import com.example.serverprovision.execution.service.BmcIdentityProbe;
import com.example.serverprovision.global.redfish.PowerControlResult;
import com.example.serverprovision.global.redfish.RedfishBiosService;
import com.example.serverprovision.global.redfish.RedfishPowerService;
import com.example.serverprovision.global.redfish.RedfishPowerState;
import com.example.serverprovision.global.redfish.RedfishRequestException;
import com.example.serverprovision.global.redfish.RedfishResetType;
import com.example.serverprovision.global.redfish.RedfishTarget;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

import java.util.Map;
import java.util.Optional;

/**
 * 6행 — BIOS 착수(E3-1 D-4): ① 신원 대조 → ② 원장 열기(되돌리기 어려운 조작보다 먼저) → ③ 목표 전체를 PATCH
 * (현재값과 같아도 다시 쓴다 — 생략 판단 없음) → ④ pending 관찰(실패 판정 아님) → ⑤ 재부팅 → rebootAt.
 * 열린 행이 rebootAt 없이 남아 있으면(직전 주기가 재부팅 뒤에 죽음) 그 행을 이어 ①③④⑤ 를 다시 한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BeginSettingStep implements SettingStep {

    private final SettingLedger ledger;
    private final BmcIdentityProbe identityProbe;
    private final RedfishBiosService biosService;
    private final RedfishPowerService powerService;
    private final FlashTimeoutPolicy timeoutPolicy;

    @Override
    public int order() {
        return 6;
    }

    @Override
    public boolean matches(SettingContext context) {
        return context.axis() == SettingAxis.BIOS && context.target() != null && !context.target().isEmpty()
                && context.bmcDetected();
    }

    @Override
    public void execute(SettingContext context) {
        Optional<ProvisioningHistory> resumed = context.runningRow();
        RedfishTarget target = context.redfishTarget();

        BmcIdentity identity = identityProbe.probe(context.provider(), target,
                context.detail().getBoardSerial(), context.detail(), "setting");
        if (identity == BmcIdentity.MISMATCHED) {
            fail(context, resumed, SettingLedger.IDENTITY_MISMATCH, "응답한 장비의 보드 시리얼이 이 서버와 다릅니다");
            return;
        }
        if (identity == BmcIdentity.UNREACHABLE) {
            if (timeoutPolicy.isExpired(context.progress().getLastTransitionAt(), timeoutPolicy.returnLimit(),
                    context.now())) {
                fail(context, resumed, SettingLedger.BMC_UNREACHABLE, "BMC 에 닿지 못했고 새 주소도 찾지 못했습니다");
            }
            return;
        }

        ProvisioningHistory row = resumed.orElseGet(() -> ledger.open(context.server(), context.target(), context.now()));
        Map<String, Object> attributes = context.target().attributes();
        try {
            biosService.patchPending(target, attributes);
        } catch (RedfishRequestException e) {
            ledger.close(row, ProvisioningStatus.FAILED, SettingLedger.PATCH_REJECTED, e.getMessage(), context.now());
            context.progress().markFailed(context.now());
            log.warn("[setting] {} — PATCH 거절 : {}", context.server().getId(), e.getMessage());
            return;
        }
        boolean pendingSeen = biosService.pending(target)
                .map(pending -> covers(pending.path("Attributes"), attributes))
                .orElse(false);
        ledger.markPending(row, pendingSeen);

        RedfishResetType reset = powerService.powerState(target).powerState() == RedfishPowerState.OFF
                ? RedfishResetType.ON : RedfishResetType.FORCE_RESTART;
        PowerControlResult result = powerService.reset(target, reset);
        if (result.kind() == PowerControlResult.Kind.FAILED) {
            log.info("[setting] {} — 재부팅 명령 실패, 다음 주기 재시도 : {}", context.server().getId(), result.message());
            return;   // 행은 rebootAt 없이 남는다 — 다음 주기가 이 행을 이어 다시 한다
        }
        ledger.markRebooted(row, context.now());
        log.info("[setting] {} — BIOS 설정 {}개 PATCH(pending {}), {} 발행", context.server().getId(),
                attributes.size(), pendingSeen ? "확인" : "미확인", reset.name());
    }

    private static boolean covers(JsonNode pending, Map<String, Object> attributes) {
        return attributes.entrySet().stream().allMatch(e -> ReadbackValues.same(pending.get(e.getKey()), e.getValue()));
    }

    private void fail(SettingContext context, Optional<ProvisioningHistory> row, String reason, String detail) {
        row.ifPresentOrElse(
                r -> { ledger.close(r, ProvisioningStatus.FAILED, reason, detail, context.now());
                       context.progress().markFailed(context.now()); },
                () -> ledger.failAtCursor(context.server(), context.progress(), reason, detail, context.now()));
        log.warn("[setting] {} — {} : {}", context.server().getId(), reason, detail);
    }
}
