package com.example.serverprovision.execution.dto.response;

import com.example.serverprovision.execution.enums.ProvisioningPhase;

/**
 * 완료 보고의 응답(E4-1-a-4 R4) — {@code closed} 가 false 면 이미 닫힌 행에 대한 중복 보고(no-op · 멱등).
 * {@code nextPhase} 는 종단이 아닐 때 커서가 넘어간 다음 소유 phase, 종단이면 null.
 */
public record WindowsInstallCompletionResponse(
        boolean closed,
        boolean provisioningCompleted,
        ProvisioningPhase nextPhase
) {
}
