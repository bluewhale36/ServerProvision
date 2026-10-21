package com.example.serverprovision.global.web.list.vo;

import java.time.LocalDate;

/**
 * 기간 조건을 가진 목록 조회 record 의 공통 면(S8-5). record 가 URL 파라미터 {@code from} · {@code to} 를 그대로 갖고,
 * 규칙은 {@link DateRange} 가 맡는다. 조각 {@code fragments/ui/date-range-row} 는 이 면만 읽는다.
 */
public interface DateRangeQuery {

    LocalDate from();

    LocalDate to();

    default DateRange range() {
        return new DateRange(from(), to());
    }

    default boolean isRangeReversed() {
        return range().isReversed();
    }
}
