package com.example.serverprovision.execution.engine.firmware.step;

import com.example.serverprovision.execution.engine.firmware.BmcIdentityGuard;
import com.example.serverprovision.execution.engine.firmware.FirmwareAxis;
import com.example.serverprovision.execution.engine.firmware.FlashLedger;
import com.example.serverprovision.execution.engine.firmware.FlashTimeoutPolicy;
import com.example.serverprovision.global.redfish.NextBoot;
import com.example.serverprovision.global.redfish.PowerControlResult;
import com.example.serverprovision.global.redfish.RedfishPowerService;
import com.example.serverprovision.global.redfish.RedfishPowerState;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 6 · 7행 — 축이 전부 끝났고 게스트가 아직 돌아오지 않았다(E2-2 §5). 전원을 넣고 기다린다.
 *
 * <p>두 행을 한자리에 둔 것은 <b>같은 상황의 두 얼굴</b>이기 때문이다. 전원을 넣었는지 여부를 따로
 * 기록하지 않고 <b>멱등</b>으로 다룬다 — 현재 전원 상태를 먼저 읽어 이미 켜져 있으면 넣지 않는다.
 * 그래서 진행 상태를 담는 컬럼이 하나도 늘지 않는다.</p>
 *
 * <p><b>이 자리가 시퀀스에서 가장 위험하다.</b> 직전 구간이 BMC 를 구운 직후이고, BMC 는 그때 스스로
 * 재기동하며 5~10분 사라졌다 돌아온다 — 이 시퀀스에서 주소가 바뀔 가능성이 가장 높은 순간이다.
 * 전원 투입은 되돌릴 수 없는 조작이므로 신원을 먼저 확인한다(D-11).</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PowerOnStep implements FlashStep {

    private final BmcIdentityGuard identityGuard;
    private final RedfishPowerService powerService;
    private final FlashTimeoutPolicy timeoutPolicy;
    private final FlashLedger ledger;

    @Override
    public int order() {
        return 6;
    }

    @Override
    public boolean matches(FlashContext context) {
        return context.nextUntouchedAxis().isEmpty() && !context.guestReturned();
    }

    @Override
    public void execute(FlashContext context) {
        if (timeoutPolicy.isExpired(context.returnWaitSince(), timeoutPolicy.returnLimit(), context.now())) {
            ledger.failAtCursor(context.server(), context.progress(), FlashLedger.RETURN_TIMEOUT,
                    "전원을 넣은 뒤 시한 안에 돌아오지 않았습니다", context.now());
            return;
        }
        if (!identityGuard.confirm(context, FirmwareAxis.of(context.progress().getCurrentStep()))) {
            return;
        }
        if (powerService.powerState(context.target()).powerState() == RedfishPowerState.ON) {
            return;   // 이미 켜져 있다 — 돌아오기를 기다릴 뿐이다.
        }
        // 전원 투입 직전 다음 부팅을 PXE 로 무장한다(E2.5) — 부트 순서가 디스크 1순위여도 게스트가 돌아온다.
        PowerControlResult result = powerService.powerOnAndVerify(context.target(), NextBoot.PXE_ONCE);
        if (result.kind() == PowerControlResult.Kind.VERIFIED) {
            // 되돌릴 수 없는 일회 사건의 감사 기록(E2-4 Q4) — detail 에 무장(BootSourceOverride) 결과가 실린다.
            ledger.instantPower(context.server(), context.progress().getCurrentStep(),
                    FlashLedger.POWER_ON, result.message(), context.now());
        }
        log.info("[flash] {} — 굽기 완료, 전원 투입 : {}", context.server().getId(), result.message());
    }
}
