package com.example.serverprovision.execution.engine.firmware.step;

import com.example.serverprovision.execution.engine.firmware.BmcIdentityGuard;
import com.example.serverprovision.execution.engine.firmware.FirmwareAxis;
import com.example.serverprovision.execution.engine.firmware.FlashLedger;
import com.example.serverprovision.global.redfish.PowerControlResult;
import com.example.serverprovision.global.redfish.RedfishResetType;
import com.example.serverprovision.global.redfish.RedfishPowerService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 4행 — 집행 착수(E2-2 §5). 커서를 첫 축에 놓고 <b>전원을 끈다.</b>
 *
 * <p>flash 중 게스트 전원은 꺼져 있어야 한다 — 전원 선은 유지되므로 BMC 는 대기 전력으로 살아 있고,
 * 그래서 굽는 일 자체는 계속된다. 전원 왕복은 phase 수준에서 <b>한 번씩</b>이며, 축마다 왕복하면
 * 재부팅이 두 번이 되어 시간과 벽돌 위험 구간이 함께 는다.</p>
 *
 * <p>전원 끄기는 되돌릴 수 없는 조작이므로 신원을 먼저 확인한다(D-11).</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BeginFlashStep implements FlashStep {

    private final BmcIdentityGuard identityGuard;
    private final RedfishPowerService powerService;
    private final FlashLedger ledger;

    @Override
    public int order() {
        return 4;
    }

    @Override
    public boolean matches(FlashContext context) {
        return context.beforeStart();
    }

    @Override
    public void execute(FlashContext context) {
        FirmwareAxis first = FirmwareAxis.values()[0];
        if (!identityGuard.confirm(context, first)) {
            return;
        }
        context.progress().positionAt(first.getStep(), context.now());
        PowerControlResult off = powerService.reset(context.target(), RedfishResetType.FORCE_OFF);
        if (off.kind() == PowerControlResult.Kind.SENT) {
            // 되돌릴 수 없는 일회 사건의 감사 기록(E2-4 Q4). detail 은 엔진 문구다 — 화면 경로의
            // "[상태 조회] …" 안내가 원장에 남으면 운영자가 누른 것처럼 읽힌다(CP5 F-7).
            ledger.instantPower(context.server(), first.getStep(), FlashLedger.POWER_OFF,
                    "전원 차단(ForceOff) — 굽는 동안 꺼진 채 유지됩니다", context.now());
        }
        log.info("[flash] {} — 집행 착수, 전원 차단", context.server().getId());
    }
}
