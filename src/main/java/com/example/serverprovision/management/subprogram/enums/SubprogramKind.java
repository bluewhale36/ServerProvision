package com.example.serverprovision.management.subprogram.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Subprogram 자원의 종류.
 * <ul>
 *   <li>{@code DRIVER} — 메인보드/장치 드라이버 패키지</li>
 *   <li>{@code UTILITY} — RAID utility, 진단/테스트 프로그램 등 운영자 공용 보조 도구</li>
 * </ul>
 * <p>두 종류가 동일 라이프사이클(번들 등록 → 마커 → 검증 → soft delete → MK1 편입)을 공유하므로
 * {@link com.example.serverprovision.management.subprogram.entity.Subprogram} 엔티티 한 벌로 흡수한다.</p>
 */
@Getter
@RequiredArgsConstructor
public enum SubprogramKind {
    DRIVER("드라이버"),
    UTILITY("유틸리티");

    private final String displayName;

    /**
     * URL path variable 의 소문자 표기 ({@code driver} / {@code utility}) 를 enum 으로 변환.
     */
    public static SubprogramKind fromPathToken(String token) {
        if (token == null) throw new IllegalArgumentException("kind 토큰이 null 입니다.");
        return switch (token.toLowerCase()) {
            case "driver" -> DRIVER;
            case "utility" -> UTILITY;
            default -> throw new IllegalArgumentException("알 수 없는 subprogram kind 토큰 : " + token);
        };
    }

    /**
     * URL / 마커 attributes 에 쓰이는 소문자 토큰.
     */
    public String pathToken() {
        return name().toLowerCase();
    }
}
