package com.example.serverprovision.execution.engine.raid;

/**
 * RAID 없음 규칙이 남기는 단독 디스크 보장 명령(E3.5-2) — 볼륨이 아니지만 역할 · 우선순위 판정에는 참여한다.
 */
public record PlannedPassthrough(
        String slot,
        long usableBytes,
        PlannedVolumeRole role,
        /** 정의서 규칙 순번(1-based) — {@code raid_volume.rule_no} 기록의 원천(E3.5-3). */
        int ruleNo
) {
}
