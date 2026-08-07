package com.example.serverprovision.provisioning.assignment.dto.response;

import com.example.serverprovision.execution.enums.ProvisioningPhase;
import com.example.serverprovision.provisioning.assignment.dto.response.PlannedPhaseRailResponse.RunState;
import com.example.serverprovision.provisioning.assignment.enums.AssignmentState;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link PlannedPhaseRailResponse#of} 순수 겹침 규칙 — 계획(assigned)에 실제 커서(started)를 겹쳐
 * done/current/pending 을 계산한다(DB 불요).
 */
class PlannedPhaseRailResponseTest {

    private static AssignmentPlanResponse plan() {
        return new AssignmentPlanResponse(true, "web-standard", AssignmentState.ACTIVE_CONSUMED,
                "이미 개시되어 재할당할 수 없습니다(회수 후 재등록 필요)",
                List.of(ProvisioningPhase.DIAGNOSE_LINUX,
                        ProvisioningPhase.FIRMWARE_UPDATING,
                        ProvisioningPhase.OS_INSTALLING));
    }

    @Test
    @DisplayName("미할당 → 빈 rail")
    void unassigned_emptyRail() {
        PlannedPhaseRailResponse rail = PlannedPhaseRailResponse.of(
                AssignmentPlanResponse.unassigned(), ProvisioningPhase.BOOTSTRAPPING, false);

        assertThat(rail.assigned()).isFalse();
        assertThat(rail.entries()).isEmpty();
    }

    @Test
    @DisplayName("할당 + 미개시 → 전부 PENDING(계획) — 실제 전진 미반영")
    void assignedNotStarted_allPending() {
        PlannedPhaseRailResponse rail = PlannedPhaseRailResponse.of(
                plan(), ProvisioningPhase.DIAGNOSE_LINUX, false);

        assertThat(rail.entries()).extracting(PlannedPhaseRailResponse.Entry::runState)
                .containsExactly(RunState.PENDING, RunState.PENDING, RunState.PENDING);
    }

    @Test
    @DisplayName("of — reassignBlockReason · state 를 계획에서 그대로 전달(뷰 disabled 판정 SSOT)")
    void of_propagatesReassignBlockReason() {
        PlannedPhaseRailResponse rail = PlannedPhaseRailResponse.of(
                plan(), ProvisioningPhase.FIRMWARE_UPDATING, true);

        assertThat(rail.state()).isEqualTo(AssignmentState.ACTIVE_CONSUMED);
        assertThat(rail.reassignBlockReason()).isEqualTo("이미 개시되어 재할당할 수 없습니다(회수 후 재등록 필요)");
    }

    @Test
    @DisplayName("할당 + 개시(커서=FIRMWARE_UPDATING) → 앞 DONE · 커서 CURRENT · 뒤 PENDING")
    void assignedStarted_overlaysCursor() {
        PlannedPhaseRailResponse rail = PlannedPhaseRailResponse.of(
                plan(), ProvisioningPhase.FIRMWARE_UPDATING, true);

        assertThat(rail.entries()).extracting(
                        PlannedPhaseRailResponse.Entry::phase, PlannedPhaseRailResponse.Entry::runState)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(ProvisioningPhase.DIAGNOSE_LINUX, RunState.DONE),
                        org.assertj.core.groups.Tuple.tuple(ProvisioningPhase.FIRMWARE_UPDATING, RunState.CURRENT),
                        org.assertj.core.groups.Tuple.tuple(ProvisioningPhase.OS_INSTALLING, RunState.PENDING));
    }
}
