package com.example.serverprovision.global.web.list;

/**
 * 검색어 → LIKE 패턴(S8-1). 사용자가 친 {@code %} · {@code _} · {@code \} 는 와일드카드가 아니라 글자 그대로 찾아야 하므로
 * 이스케이프하고, 술어는 {@link #ESCAPE} 를 {@code ESCAPE} 문자로 함께 넘긴다. 대소문자는 술어가 양쪽을 {@code lower()}
 * 로 맞춘다 — MariaDB 의 콜레이션에 기대지 않는다.
 */
public final class LikePattern {

    public static final char ESCAPE = '\\';

    private LikePattern() {
    }

    /** 부분 일치 패턴 {@code %검색어%} — 소문자로 내리고 와일드카드를 이스케이프한다. */
    public static String contains(String raw) {
        String escaped = raw.toLowerCase()
                .replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
        return "%" + escaped + "%";
    }
}
