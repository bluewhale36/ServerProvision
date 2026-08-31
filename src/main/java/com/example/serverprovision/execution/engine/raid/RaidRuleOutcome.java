package com.example.serverprovision.execution.engine.raid;

/**
 * 규칙 하나의 소비 결과(E3.5-2) — 소비 0 규칙을 미리보기에 드러내는 재료다(사각 규칙의 런타임 가시화,
 * 결정 7 — 정적 차단은 E3.5-4 소관).
 */
public record RaidRuleOutcome(
        int ruleNo,
        String ruleLabel,
        int matchedDisks,
        int consumedDisks,
        int volumeCount
) {

    /** 매칭이 있었는데도 아무것도 소비하지 못했는가 — 사각 규칙 의심 표시의 판정. */
    public boolean consumedNothing() {
        return consumedDisks == 0;
    }
}
