package com.example.serverprovision.management.raidcard.vo;

import com.example.serverprovision.management.raidcard.enums.RaidLevel;

/**
 * "이 계열 카드로 이 레벨을 디스크 몇 대로 만들 수 있는가" — 최소 · 최대 · 단위(R17-1).
 *
 * <p>계열이 내고({@code RaidChipFamily.memberCountRule}) 정의서 폼 · 저장 가드 · 계획 엔진이 같은 객체를 본다.
 * R17-1 이전에는 폼 · 저장 가드가 레벨 고정 하한만, 계획이 계열 규칙만 봐서 둘이 서로 다른 표를 봤다.</p>
 *
 * <p>판정은 둘이다 — {@link #blockReasonFor} 는 확정된 멤버 수로 전부 보고, {@link #boundsBlockReasonFor} 는
 * 하한 · 상한만 본다('개 이상' 규칙 — 단위는 계획 시점 그룹 크기의 몫). 어느 쪽을 쓸지는 개수 축이
 * 정한다({@code DiskCountRequirement.memberCountBlockReason}). 반환 규약은 {@code SupportedRaidLevels.blockReasonFor}
 * 와 같다(null = 통과, 문자열 = 화면 안내이자 거절 사유).</p>
 *
 * @param max  상한 — null 이면 없음
 * @param step 멤버 수 단위(1 = 제약 없음)
 * @param span 스팬 구성 규칙이면 그 스팬당 디스크 수(문구에 쓰인다), 아니면 null
 */
public record MemberCountRule(RaidLevel level, int min, Integer max, int step, DrivesPerSpan span) {

    public MemberCountRule {
        if (min < 1 || step < 1 || (max != null && max < min)) {
            throw new IllegalArgumentException("디스크 수 규칙이 모순입니다: " + min + "~" + max + " / " + step);
        }
    }

    public static MemberCountRule of(RaidLevel level, int min, Integer max) {
        return new MemberCountRule(level, min, max, 1, null);
    }

    /** 화면 표기 — "4~16대 · 2대 단위" · "2대" · "3대 이상". 폼 · 거절 문구가 이 한 표기를 쓴다. */
    public String display() {
        String range = max == null ? min + "대 이상"
                : min == max ? min + "대"
                : min + "~" + max + "대";
        return step > 1 ? range + " · " + step + "대 단위" : range;
    }

    /** 확정된 멤버 수 판정 — 계획 시점, 또는 '개' · '개씩' 처럼 n 이 곧 볼륨 멤버 수일 때. */
    public String blockReasonFor(int members) {
        boolean ok = withinBounds(members) && members % step == 0;
        return ok ? null : rejection(members);
    }

    /** 하한 · 상한만 판정 — '개 이상' 규칙의 n(그룹 크기 하한)에 쓴다. */
    public String boundsBlockReasonFor(int n) {
        return withinBounds(n) ? null : rejection(n);
    }

    private boolean withinBounds(int n) {
        return n >= min && (max == null || n <= max);
    }

    private String rejection(int n) {
        String head = span == null
                ? level.getDisplayName() + " 의 구성 대수는 " + display() + "입니다"
                : level.getDisplayName() + " " + level.getObjectParticle() + " 스팬당 " + span.value()
                        + "대로 만들려면 " + display() + "여야 합니다";
        return head + " — " + n + "대는 만들 수 없습니다";
    }
}
