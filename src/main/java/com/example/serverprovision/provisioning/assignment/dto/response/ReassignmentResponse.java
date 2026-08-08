package com.example.serverprovision.provisioning.assignment.dto.response;

import com.example.serverprovision.execution.enums.ProvisioningPhase;

import java.util.List;

/**
 * 재할당 결과 응답(XHR) — 새로 만든 활성 스냅샷 + 논리 종료(supersede)된 이전 스냅샷 식별자.
 *
 * <p>{@code assignmentId} 는 재할당 시점의 현재 정의서로 새로 derive-then-freeze 한 활성 스냅샷,
 * {@code supersededAssignmentId} 는 이력으로 접힌 직전 활성 스냅샷이다. {@code ownedPhases} 는 새 스냅샷에서
 * 다시 도출한 소유 phase(선언 순)다.</p>
 */
public record ReassignmentResponse(
        Long assignmentId,
        Long supersededAssignmentId,
        Long definitionId,
        String definitionName,
        List<ProvisioningPhase> ownedPhases
) {
}
