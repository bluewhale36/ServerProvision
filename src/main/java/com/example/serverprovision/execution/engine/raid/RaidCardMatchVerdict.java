package com.example.serverprovision.execution.engine.raid;

/**
 * 지정 카드(정의서) 와 관측 카드(인벤토리)의 대조 판정(E3.5-5-a D4) — 집행의 실패 승격과 화면의 개시 전 예고가
 * 같은 값을 본다. 순서는 판정 우선순위가 아니라 표기 편의다.
 */
public enum RaidCardMatchVerdict {
    /** 할당이 없거나 정의서가 카드를 지정하지 않았다 — 대조할 대상이 없다. */
    NOT_APPLICABLE,
    /** 지정 카드 자원에 Subsystem 이 확정되지 않았다(소프트참조 소실 포함) — 정본이 없어 대조할 수 없다. */
    UNVERIFIABLE,
    /** 지정했는데 게스트에서 RAID 카드(Subsystem)를 감지하지 못했다. */
    NOT_DETECTED,
    /** 지정 카드와 감지 카드의 Subsystem 이 다르다. */
    MISMATCH,
    /** 일치. */
    MATCH
}
