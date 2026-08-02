package com.example.serverprovision.provisioning.assignment.dto.response;

import com.example.serverprovision.execution.enums.ProvisioningPhase;
import com.example.serverprovision.provisioning.assignment.enums.AssignmentState;

import java.util.List;

/**
 * 게스트의 활성 할당 <b>계획</b>(actual 진행 미반영) — 상세 rail 조립의 입력.
 *
 * <p>{@code plannedPhases} 는 밟을 예정 phase 를 {@code ProvisioningPhase} 선언 순으로 담고
 * {@code DIAGNOSE_LINUX}(정의서 소비 없는 무조건 phase)를 포함한다. 활성 할당이 없으면
 * {@link #unassigned()}({@code plannedPhases} 빈 목록) — 할당 없이도 진단까지는 진행되지만 계획 rail 은 비운다.</p>
 */
public record AssignmentPlanResponse(
        boolean assigned,
        String definitionName,
        AssignmentState state,
        List<ProvisioningPhase> plannedPhases
) {

    public static AssignmentPlanResponse unassigned() {
        return new AssignmentPlanResponse(false, null, null, List.of());
    }
}
