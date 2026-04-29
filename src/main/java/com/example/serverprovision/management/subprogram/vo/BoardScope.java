package com.example.serverprovision.management.subprogram.vo;

/**
 * Subprogram 의 적용 범위(scope).
 * <ul>
 *   <li>공용 — {@link #boardId} 가 {@code null}. URL 토큰 {@code common}.</li>
 *   <li>보드별 — {@link #boardId} 가 BoardModel PK. URL 토큰은 그 숫자.</li>
 * </ul>
 * <p>Primitive Obsession 회피 (D6). Controller PathVariable / Service 시그니처 / 마커 attributes 모두에서
 * 단순 {@code Long} 또는 {@code String} 대신 본 VO 로 의미를 명시한다.</p>
 */
public record BoardScope(Long boardId) {

    private static final String COMMON_TOKEN = "common";

    public static final BoardScope COMMON = new BoardScope(null);

    public static BoardScope ofBoard(Long boardId) {
        if (boardId == null) throw new IllegalArgumentException("boardId 가 null 일 수 없습니다. 공용은 BoardScope.COMMON 을 사용하세요.");
        return new BoardScope(boardId);
    }

    public static BoardScope fromPathToken(String token) {
        if (token == null) throw new IllegalArgumentException("boardScope 토큰이 null 입니다.");
        if (COMMON_TOKEN.equalsIgnoreCase(token)) return COMMON;
        try {
            return new BoardScope(Long.parseLong(token));
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("boardScope 토큰이 올바르지 않습니다 : " + token);
        }
    }

    public boolean isCommon() {
        return boardId == null;
    }

    public String pathToken() {
        return isCommon() ? COMMON_TOKEN : String.valueOf(boardId);
    }
}
