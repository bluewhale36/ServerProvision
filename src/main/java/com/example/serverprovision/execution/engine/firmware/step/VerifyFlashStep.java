package com.example.serverprovision.execution.engine.firmware.step;

import com.example.serverprovision.execution.engine.firmware.BmcIdentityGuard;
import com.example.serverprovision.execution.engine.firmware.FirmwareAxis;
import com.example.serverprovision.execution.engine.firmware.FlashLedger;
import com.example.serverprovision.execution.engine.phase.PhaseCursorAdvancer;
import com.example.serverprovision.execution.entity.ProvisioningHistory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * 8행 — 게스트가 돌아왔다. 축마다 반영을 확인하고 종결한다(E2-2 §5).
 *
 * <p>게스트의 재진입이 곧 <b>POST 를 지났다</b>는 신호다 — BIOS 는 재부팅해야 새 버전을 보고하므로
 * 그 신호를 기다려야 한다.</p>
 *
 * <p>이 읽기는 <b>종결 판정의 근거</b>이므로 신원을 먼저 확인한다(D-11). 잘못된 대상의 버전이 우연히
 * 목표와 같으면 phase 를 성공으로 닫고 커서를 전진시키는데, 그러면 굽지 않았거나 남의 장비를 굽고서
 * "문제없이 지났다" 고 원장에 남는다.</p>
 *
 * <p>반영이 어긋나면 그 축에 <b>두 번째 행</b>을 남긴다 — 앞 행을 고쳐 쓰면 "전송은 완료됐다" 는 사실이
 * 사라지고, 원장은 append-only 다(DEC-3).</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class VerifyFlashStep implements FlashStep {

    private final BmcIdentityGuard identityGuard;
    private final PhaseCursorAdvancer phaseCursorAdvancer;
    private final FlashLedger ledger;

    @Override
    public int order() {
        return 8;
    }

    @Override
    public boolean matches(FlashContext context) {
        return context.nextUntouchedAxis().isEmpty() && context.guestReturned();
    }

    @Override
    public void execute(FlashContext context) {
        if (!identityGuard.confirm(context, FirmwareAxis.of(context.progress().getCurrentStep()))) {
            return;
        }
        for (FirmwareAxis axis : FirmwareAxis.values()) {
            String expected = expectedVersionOf(context, axis);
            if (expected == null) {
                continue;   // 굽지 않은 축은 대조할 것이 없다.
            }
            Optional<String> actual = context.provider().readVersion(context.target(), axis);
            if (actual.isEmpty() || !FlashAxisStep.sameVersion(actual.get(), expected)) {
                context.progress().positionAt(axis.getStep(), context.now());
                ledger.failAxis(context.server(), context.progress(), axis, FlashLedger.VERIFY_MISMATCH,
                        "목표 " + expected + " · 확인 " + actual.orElse("읽지 못함"), context.now());
                return;
            }
        }
        log.info("[flash] {} — 반영 확인 완료", context.server().getId());
        // 전진 · 종단 판정은 진단 완주와 같은 지점을 쓴다 — 소유 phase 를 읽어 다음으로 가거나 종단한다(ES-1).
        phaseCursorAdvancer.advanceOrComplete(context.progress(), context.server().getId(), context.now());
    }

    /** 그 축에 무엇을 구우라고 적었는가 — 확인 기준은 지금 판정이 아니라 <b>원장의 기록</b>이다(D-4). */
    private static String expectedVersionOf(FlashContext context, FirmwareAxis axis) {
        return context.history().stream()
                .filter(row -> row.getStepCode() == axis.getStep())
                .map(ProvisioningHistory::flashTargetVersion)
                .filter(java.util.Objects::nonNull)
                .reduce((first, second) -> second)
                .orElse(null);
    }
}
