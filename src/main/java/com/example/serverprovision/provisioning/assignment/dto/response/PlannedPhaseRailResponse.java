package com.example.serverprovision.provisioning.assignment.dto.response;

import com.example.serverprovision.execution.enums.ProvisioningPhase;
import com.example.serverprovision.provisioning.assignment.enums.AssignmentState;
import com.example.serverprovision.provisioning.assignment.view.AssignmentStateView;

import java.util.ArrayList;
import java.util.List;

/**
 * 상세 화면 '계획 phase rail' 뷰 모델 — 계획(assigned)에 실제 진행(started) 커서를 겹친 결과.
 *
 * <p>계획은 {@link AssignmentPlanResponse#plannedPhases()}(할당 스냅샷의 소유 phase), 실제 커서는
 * {@code ProvisioningProgress.currentPhase}(개시된 경우만)다. 두 관심사를 겹쳐 각 phase 를 done/current/pending
 * 으로 표기하되 '계획 vs 실제' 를 라벨로 구분한다 — ES-1 미배선이라 실제 전진이 아직 안 보이는 것을 '고장'으로
 * 오인하지 않게 하기 위함이다. 순수 함수 {@link #of}(DB 불요)라 단위 테스트로 겹침 규칙을 검증한다.</p>
 */
public record PlannedPhaseRailResponse(
        boolean assigned,
        String definitionName,
        AssignmentState state,
        String badgeClass,
        String reassignBlockReason,
        /** 지금 든 스냅샷이 이 서버의 하드웨어와 맞지 않으면 그 사유(U3-5-a) — 경고 배너의 입력. */
        String hardwareMismatchReason,
        List<Entry> entries
) {

    /** rail 한 칸 — phase · 표시명 · 실행 진행 상태. */
    public record Entry(ProvisioningPhase phase, String description, RunState runState) {
    }

    /** 계획 phase 의 실제 진행 상태. */
    public enum RunState {
        /** 커서보다 앞 — 완료(실제). */
        DONE,
        /** 커서 위치 — 현재(실제). */
        CURRENT,
        /** 커서보다 뒤 또는 미개시 — 예정(계획). */
        PENDING
    }

    /**
     * @param plan         활성 할당 계획(미할당이면 빈 rail)
     * @param currentPhase 진행 커서(미개시면 무시)
     * @param started      개시 여부 — 미개시면 전부 PENDING(계획)
     */
    public static PlannedPhaseRailResponse of(AssignmentPlanResponse plan, ProvisioningPhase currentPhase, boolean started) {
        ProvisioningPhase cursor = started ? currentPhase : null;
        List<Entry> entries = new ArrayList<>();
        for (ProvisioningPhase phase : plan.plannedPhases()) {
            entries.add(new Entry(phase, phase.getDescription(), runStateOf(phase, cursor)));
        }
        String badgeClass = plan.state() != null ? AssignmentStateView.badgeClass(plan.state()) : null;
        return new PlannedPhaseRailResponse(plan.assigned(), plan.definitionName(), plan.state(),
                badgeClass, plan.reassignBlockReason(), plan.hardwareMismatchReason(), List.copyOf(entries));
    }

    private static RunState runStateOf(ProvisioningPhase phase, ProvisioningPhase cursor) {
        if (cursor == null) {
            return RunState.PENDING;
        }
        if (phase.ordinal() < cursor.ordinal()) {
            return RunState.DONE;
        }
        return phase == cursor ? RunState.CURRENT : RunState.PENDING;
    }
}
