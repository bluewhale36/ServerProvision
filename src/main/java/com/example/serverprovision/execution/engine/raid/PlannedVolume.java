package com.example.serverprovision.execution.engine.raid;

import com.example.serverprovision.management.raidcard.enums.RaidLevel;

import java.util.List;

/**
 * 계획된 볼륨 1개(E3.5-2) — 중립 생성 명령. 이름 {@code spvR{규칙}V{순번}} 은 멱등 · E4 인계의 열쇠(결정 D-7 · D-8 — 하이픈 미지원 카드 대비 영숫자만, E3.5-3 CP1 개정).
 *
 * @param usableBytes 유효 용량 = {@code RaidLevel.usableDisks(n) × 최소 멤버 바이트}
 */
public record PlannedVolume(
        String name,
        RaidLevel level,
        List<String> memberSlots,
        long usableBytes,
        PlannedVolumeRole role,
        /** 정의서 규칙 순번(1-based) — {@code raid_volume.rule_no} 기록의 원천(E3.5-3). */
        int ruleNo
) {

    public PlannedVolume {
        memberSlots = memberSlots == null ? List.of() : List.copyOf(memberSlots);
    }
}
