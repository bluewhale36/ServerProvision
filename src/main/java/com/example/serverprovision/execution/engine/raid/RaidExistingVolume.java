package com.example.serverprovision.execution.engine.raid;

import java.util.List;

/**
 * 카드에 이미 존재하는 볼륨 1개(E3.5-1) — 멱등 · 기존 구성 정책(E3.5-3, 0-3 결정 D-7)의 입력이다.
 *
 * @param name 카드 메타데이터의 볼륨 이름 — MegaRAID 는 Name 열, IR 은 Volume Name 행(실기 2026-09-01: 노출 실증)
 */
public record RaidExistingVolume(
        String id,
        String level,
        String size,
        String state,
        String name,
        List<String> memberSlots,
        /** 볼륨 WWN — MegaRAID {@code SCSI NAA Id} · IR {@code Volume wwid}. 미노출은 null(E3.5-4 증보). */
        String wwn
) {

    public RaidExistingVolume {
        memberSlots = memberSlots == null ? List.of() : List.copyOf(memberSlots);
    }

    /**
     * 우리 이름 규약({@code spvR} 접두)의 볼륨인가 — 잔여 판별의 SSOT(E3.5-4 Q1 · Q2 확정).
     * planner(보존 거절 범위) · 실행기(보류 판정) · 미리보기(갈래 분기)가 이 한 곳을 공유한다.
     */
    public boolean isProvisionOwned() {
        return name != null && name.trim().startsWith("spvR");
    }
}
