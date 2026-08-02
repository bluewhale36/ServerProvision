package com.example.serverprovision.provisioning.assignment.dto.response;

import com.example.serverprovision.execution.enums.ProvisioningPhase;

import java.util.List;

/**
 * 할당 결과 응답(XHR) — 생성된 스냅샷 식별자 + 도출된 {@code ownedPhases}(선언 순).
 */
public record AssignmentResponse(
        Long assignmentId,
        Long definitionId,
        String definitionName,
        List<ProvisioningPhase> ownedPhases
) {
}
