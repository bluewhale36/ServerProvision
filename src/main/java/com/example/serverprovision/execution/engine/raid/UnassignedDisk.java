package com.example.serverprovision.execution.engine.raid;

/**
 * 계획에서 어느 볼륨에도 배정되지 않은 디스크(E3.5-2) — 잔여는 그대로 둔다(결정 D-5). 사유는 표시용.
 */
public record UnassignedDisk(
        String slot,
        String size,
        String reason
) {
}
