package com.example.serverprovision.execution.engine.raid;

/**
 * 계획 전체 거절(E3.5-2) — 한계 위반 시 일부만 집행하면 의도와 다른 상태가 실물에 남으므로 볼륨 하나를
 * 빼는 대신 통째로 거절한다. 코드 문자열은 집행(E3.5-3)의 원장 사유로 재사용된다.
 */
public record RaidPlanRejection(
        String code,
        String detail
) implements RaidPlanOutcome {

    /** 보존 정책인데 카드에 기존 볼륨이 남아 있다(결정 D-7). */
    public static final String EXISTING_CONFIG = "EXISTING_CONFIG";
    /** 계획 볼륨 수가 칩 계열의 한계를 넘는다. */
    public static final String VOLUME_LIMIT = "VOLUME_LIMIT";
    /** 볼륨의 멤버 수가 칩 계열의 레벨별 제약에 맞지 않는다. */
    public static final String MEMBER_COUNT = "MEMBER_COUNT";
}
