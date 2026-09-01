package com.example.serverprovision.execution.engine.firmware.step;

import com.example.serverprovision.execution.engine.firmware.FirmwareAxis;
import com.example.serverprovision.execution.engine.firmware.FlashLedger;
import com.example.serverprovision.execution.engine.firmware.FlashTaskState;
import com.example.serverprovision.execution.engine.firmware.FlashTimeoutPolicy;
import com.example.serverprovision.execution.entity.ProvisioningHistory;
import com.example.serverprovision.execution.enums.ProvisioningStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * 2행 — 진행 중인 굽기의 상태를 읽는다(E2-2 §5).
 *
 * <p><b>가장 위에 두는 데는 이유가 둘</b> 있다. 아래에 두면 한 축의 Task 가 떠 있는 상태에서 다음 축의
 * 굽기를 동시에 걸게 되고, 준비도 판정보다 아래에 두면 집행 도중 자원이 무너졌을 때 워커가 그 게스트를
 * 통째로 건너뛰어 굽는 중인 Task 를 아무도 보지 않게 된다.</p>
 *
 * <p>이것은 진행 관측이라 신원 확인을 앞세우지 않는다 — 잘못된 대상이면 그 Task 가 없거나 다른 것이
 * 나오고, 어느 쪽이든 다음 확인 지점이 잡는다.</p>
 */
@Component
@RequiredArgsConstructor
public class PollFlashTaskStep implements FlashStep {

    private final FlashTimeoutPolicy timeoutPolicy;
    private final FlashLedger ledger;
    private final com.example.serverprovision.execution.service.FirmwareImageTokenRegistry tokenRegistry;
    private final com.example.serverprovision.execution.engine.WorkerObservations observations;

    @Override
    public int order() {
        return 2;
    }

    @Override
    public boolean matches(FlashContext context) {
        return runningAxis(context).isPresent();
    }

    @Override
    public void execute(FlashContext context) {
        FirmwareAxis axis = runningAxis(context).orElseThrow();
        ProvisioningHistory row = context.openRowOf(axis).orElseThrow();

        FlashTaskState state = context.provider().pollTask(context.target(), row.flashTaskPath());
        // 관측값은 저장하지 않는다(E2-4 Q2) — 마지막 관측만 인메모리에 남겨 화면이 "돌고 있음" 을 안다.
        observations.note(context.server().getId(), "BMC Task 확인 — " + state.getUserDetail(), context.now());
        if (state.isTerminal()) {
            // 굽기가 끝났으니 파일을 더 열어 둘 이유가 없다(CP5 F-3).
            tokenRegistry.revoke(context.server().getId(), axis);
            ledger.close(row, state.getTerminalStatus(), state.getReasonCode(),
                    state.getUserDetail(), context.now());
            if (state.getTerminalStatus() == ProvisioningStatus.FAILED) {
                context.progress().markFailed(context.now());
            }
            return;
        }
        // 아직 굽는 중이거나 BMC 가 재기동 중이다 — 시한이 그 창을 덮는다.
        if (timeoutPolicy.isExpired(row.getStartedAt(), timeoutPolicy.limitFor(axis), context.now())) {
            tokenRegistry.revoke(context.server().getId(), axis);
            ledger.close(row, ProvisioningStatus.FAILED, state.getReasonCode(),
                    "시한 " + timeoutPolicy.limitFor(axis) + " 초과", context.now());
            context.progress().markFailed(context.now());
        }
    }

    private static Optional<FirmwareAxis> runningAxis(FlashContext context) {
        for (FirmwareAxis axis : FirmwareAxis.values()) {
            Optional<ProvisioningHistory> row = context.openRowOf(axis);
            if (row.isPresent()) {
                String taskPath = row.get().flashTaskPath();
                if (taskPath != null && !taskPath.isBlank()) {
                    return Optional.of(axis);
                }
            }
        }
        return Optional.empty();
    }
}
