package com.example.serverprovision.execution.engine.setting.step;

import com.example.serverprovision.execution.engine.firmware.BmcIdentity;
import com.example.serverprovision.execution.engine.firmware.FlashTimeoutPolicy;
import com.example.serverprovision.execution.engine.setting.SettingAxis;
import com.example.serverprovision.execution.engine.setting.BiosRegistryCapturePort;
import com.example.serverprovision.execution.engine.setting.RegistryCheck;
import com.example.serverprovision.execution.engine.setting.SettingLedger;
import com.example.serverprovision.execution.entity.ProvisioningHistory;
import com.example.serverprovision.execution.enums.ProvisioningStatus;
import com.example.serverprovision.execution.service.BmcIdentityProbe;
import com.example.serverprovision.global.redfish.NextBoot;
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
    private final BiosRegistryCapturePort registryPort;

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
                return;
            }
            // 침묵 대기의 흔적(E2-4 R6) — 원장 행 없이 기다리는 구간이 로그에도 안 남아 20분이 통째로 어두웠다.
            log.debug("[setting] {} — BMC 신원 확인 대기(응답 없음) — 복귀 시한까지 다음 주기 재시도",
                    context.server().getId());
            return;
        }

        ProvisioningHistory row = resumed.orElseGet(() -> ledger.open(context.server(), context.target(), context.now()));
        Map<String, Object> attributes = context.target().attributes();
        // 실제 BIOS 버전의 레지스트리로 먼저 대조한다(E3-3 R6) — 불허면 PATCH 없이 도메인 언어로 닫는다. 채집 불가는
        // 판정 없음이지 정합이 아니므로 종전 경로(PATCH → BMC 400 사유)로 간다(Q2).
        RegistryCheck check = registryPort.captureAndCheck(context.server().getId(), target, attributes);
        if (check.hasViolations()) {
            String detail = "BIOS " + check.biosVersion() + " 레지스트리 허용값 밖 : " + String.join(" / ", check.violations());
            ledger.close(row, ProvisioningStatus.FAILED, SettingLedger.VALUE_NOT_IN_REGISTRY, detail, context.now());
            context.progress().markFailed(context.now());
            log.warn("[setting] {} — 레지스트리 대조 실패, PATCH 생략 : {}", context.server().getId(), detail);
            return;
        }
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
        // 재부팅 직전 다음 부팅을 PXE 로 무장한다(E2.5) — OS 가 남은 디스크로 이탈하면 readback 이 오지 않는다.
        PowerControlResult result = powerService.reset(target, reset, NextBoot.PXE_ONCE);
        if (result.kind() == PowerControlResult.Kind.FAILED) {
            log.info("[setting] {} — 재부팅 명령 실패, 다음 주기 재시도 : {}", context.server().getId(), result.message());
            return;   // 행은 rebootAt 없이 남는다 — 다음 주기가 이 행을 이어 다시 한다
        }
        ledger.markRebooted(row, context.now(), result.message());   // 무장 결과를 같은 행 meta 가 든다(E2-4 Q4)
        log.info("[setting] {} — BIOS 설정 {}개 PATCH(pending {}), {} 발행 : {}", context.server().getId(),
                attributes.size(), pendingSeen ? "확인" : "미확인", reset.name(), result.message());
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
