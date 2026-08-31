package com.example.serverprovision.execution.engine.raid;

import java.util.List;

/**
 * 카드에 이미 존재하는 볼륨 1개(E3.5-1) — 멱등 · 기존 구성 정책(E3.5-3, 0-3 결정 D-7)의 입력이다.
 *
 * @param name 카드 메타데이터의 볼륨 이름 — 실측상 빈 값이 보통이고 IR display 는 이름을 내지 않는다(null)
 */
public record RaidExistingVolume(
        String id,
        String level,
        String size,
        String state,
        String name,
        List<String> memberSlots
) {

    public RaidExistingVolume {
        memberSlots = memberSlots == null ? List.of() : List.copyOf(memberSlots);
    }
}
