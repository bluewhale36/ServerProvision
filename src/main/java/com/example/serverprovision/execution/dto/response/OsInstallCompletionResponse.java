package com.example.serverprovision.execution.dto.response;

import com.example.serverprovision.execution.enums.ProvisioningPhase;

/**
 * OS 설치 완료 보고의 응답(Windows · RHEL 공통). {@code closed} 가 false 면 이미 닫힌 행에 대한 중복 보고다(no-op).
 * {@code nextPhase} 는 커서가 넘어간 다음 소유 phase 이고, 종단이면 null.
 */
public record OsInstallCompletionResponse(
        boolean closed,
        boolean provisioningCompleted,
        ProvisioningPhase nextPhase
) {
}
