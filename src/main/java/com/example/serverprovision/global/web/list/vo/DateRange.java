package com.example.serverprovision.global.web.list.vo;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Period;

/**
 * 목록 조회의 기간 조건(S8-5) — 양 끝 포함 · 날짜 단위. 비어 있는 끝은 기본 범위(1 년 전 ~ 오늘)로 채운다.
 *
 * <p>세 목록(세팅 정의서 · 서버 그룹 · 게스트 서버)의 조건 record 가 URL 호환을 위해 {@code from} · {@code to} 를
 * 그대로 두고 이 값에 규칙을 위임한다. 기본 범위는 URL 에 싣지 않으므로 날짜가 바뀌면 같은 주소가 새 범위로 그려진다.</p>
 *
 * @param from 시작일(포함) · 비어 있으면 기본값
 * @param to   종료일(포함) · 비어 있으면 오늘
 */
public record DateRange(LocalDate from, LocalDate to) {

    public static final Period DEFAULT_SPAN = Period.ofYears(1);

    public static LocalDate defaultFrom(LocalDate today) {
        return today.minus(DEFAULT_SPAN);
    }

    /** 비어 있는 끝만 기본 범위로 채운다. */
    public DateRange withDefault(LocalDate today) {
        return new DateRange(from == null ? defaultFrom(today) : from, to == null ? today : to);
    }

    /** 기본 범위와 같은가 — 비어 있는 끝은 기본값으로 본다. 같으면 조건이 아니다(늘 걸려 있는 출발점). */
    public boolean isDefault(LocalDate today) {
        return (from == null || from.equals(defaultFrom(today))) && (to == null || to.equals(today));
    }

    /** 시작일이 종료일보다 늦은가 — 결과가 비고 화면이 안내한다(UI 는 날짜 입력의 min/max 로 먼저 막는다). */
    public boolean isReversed() {
        return from != null && to != null && from.isAfter(to);
    }

    /** 시작일 0 시(포함) · 비어 있으면 null. */
    public LocalDateTime startInclusive() {
        return from == null ? null : from.atStartOfDay();
    }

    /** 종료일 다음 날 0 시(제외) — 종료일 하루 전체를 포함한다 · 비어 있으면 null. */
    public LocalDateTime endExclusive() {
        return to == null ? null : to.plusDays(1).atStartOfDay();
    }
}
