package com.example.serverprovision.global.web.list.vo;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class DateRangeTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 11);

    @Test
    @DisplayName("비어 있는 끝만 기본 범위(1 년 전 ~ 오늘)로 채우고, 주어진 끝은 그대로 둔다")
    void withDefault_fillsOnlyEmptyEnds() {
        assertThat(new DateRange(null, null).withDefault(TODAY)).isEqualTo(new DateRange(LocalDate.of(2025, 10, 11), TODAY));
        LocalDate a = LocalDate.of(2020, 1, 1);
        assertThat(new DateRange(a, null).withDefault(TODAY)).isEqualTo(new DateRange(a, TODAY));
        assertThat(new DateRange(null, a).withDefault(TODAY)).isEqualTo(new DateRange(LocalDate.of(2025, 10, 11), a));
    }

    @Test
    @DisplayName("기본 범위와 같으면 조건이 아니다 — 비어 있는 끝도 기본값으로 본다 · 한 끝이라도 다르면 조건")
    void isDefault() {
        assertThat(new DateRange(null, null).isDefault(TODAY)).isTrue();
        assertThat(new DateRange(LocalDate.of(2025, 10, 11), TODAY).isDefault(TODAY)).isTrue();
        assertThat(new DateRange(LocalDate.of(2025, 10, 10), null).isDefault(TODAY)).isFalse();
        assertThat(new DateRange(null, TODAY.minusDays(1)).isDefault(TODAY)).isFalse();
    }

    @Test
    @DisplayName("2 월 29 일의 1 년 전은 2 월 28 일 — 빠른 칩 스크립트도 같은 규칙을 쓴다")
    void defaultFrom_leapDay() {
        assertThat(DateRange.defaultFrom(LocalDate.of(2028, 2, 29))).isEqualTo(LocalDate.of(2027, 2, 28));
    }

    @Test
    @DisplayName("거꾸로 판정 · 경계 — 시작일 0 시 포함, 종료일 하루 전체 포함(다음 날 0 시 제외)")
    void reversedAndBounds() {
        LocalDate a = LocalDate.of(2026, 9, 1), b = LocalDate.of(2026, 9, 30);
        assertThat(new DateRange(b, a).isReversed()).isTrue();
        assertThat(new DateRange(a, b).isReversed()).isFalse();
        assertThat(new DateRange(a, null).isReversed()).isFalse();
        assertThat(new DateRange(a, b).startInclusive()).isEqualTo(LocalDateTime.of(2026, 9, 1, 0, 0));
        assertThat(new DateRange(a, b).endExclusive()).isEqualTo(LocalDateTime.of(2026, 10, 1, 0, 0));
        assertThat(new DateRange(null, null).startInclusive()).isNull();
        assertThat(new DateRange(null, null).endExclusive()).isNull();
    }
}
