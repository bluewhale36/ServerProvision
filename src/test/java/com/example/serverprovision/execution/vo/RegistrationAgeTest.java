package com.example.serverprovision.execution.vo;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 상대 시간 묶음 (U3-3 · 2026-08-11 세분화).
 * 눈금이 경과에 따라 달라지고 <b>내림</b>으로 묶이는 것을 못박는다.
 */
class RegistrationAgeTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 8, 11, 12, 0, 0);

    private static RegistrationAge ageOf(long secondsAgo) {
        return RegistrationAge.of(NOW.minusSeconds(secondsAgo), NOW);
    }

    @Test
    @DisplayName("1분 안은 10초 눈금으로 내림한다")
    void withinMinuteUsesTenSecondSteps() {
        assertThat(ageOf(0).getDescription()).isEqualTo("방금");
        assertThat(ageOf(9).getDescription()).isEqualTo("방금");     // 10초 눈금의 첫 칸
        assertThat(ageOf(10).getDescription()).isEqualTo("10초 전");
        assertThat(ageOf(38).getDescription()).isEqualTo("30초 전");  // 내림
        assertThat(ageOf(59).getDescription()).isEqualTo("50초 전");
    }

    @Test
    @DisplayName("1시간 안은 분 단위로 내림한다 — 초는 버린다")
    void withinHourUsesFlooredMinutes() {
        assertThat(ageOf(60).getDescription()).isEqualTo("1분 전");
        assertThat(ageOf(119).getDescription()).isEqualTo("1분 전");   // 1분 59초 → 1분
        assertThat(ageOf(120).getDescription()).isEqualTo("2분 전");
        assertThat(ageOf(3599).getDescription()).isEqualTo("59분 전");
    }

    @Test
    @DisplayName("24시간 안은 시간 단위로 내림한다")
    void withinDayUsesFlooredHours() {
        assertThat(ageOf(3600).getDescription()).isEqualTo("1시간 전");
        assertThat(ageOf(7199).getDescription()).isEqualTo("1시간 전");
        assertThat(ageOf(7200).getDescription()).isEqualTo("2시간 전");
        assertThat(ageOf(86_399).getDescription()).isEqualTo("23시간 전");
    }

    @Test
    @DisplayName("24시간을 넘기면 1일 단위, 7일을 넘기면 1주 단위로 묶는다")
    void beyondDayUsesDaysThenWeeks() {
        assertThat(ageOf(86_400).getDescription()).isEqualTo("1일 전");
        assertThat(ageOf(6L * 86_400 + 3600).getDescription()).isEqualTo("6일 전");
        assertThat(ageOf(7L * 86_400).getDescription()).isEqualTo("1주 전");
        assertThat(ageOf(20L * 86_400).getDescription()).isEqualTo("2주 전");   // 내림
    }

    @Test
    @DisplayName("같은 눈금 값이면 같은 묶음이다 — 내림이 그룹 키를 만든다")
    void flooringGroupsTogether() {
        assertThat(ageOf(125)).isEqualTo(ageOf(155));       // 둘 다 2분 전
        assertThat(ageOf(125)).isNotEqualTo(ageOf(185));    // 2분 전 · 3분 전
        assertThat(ageOf(31)).isEqualTo(ageOf(38));         // 둘 다 30초 전 (10초 눈금)
        assertThat(ageOf(31)).isNotEqualTo(ageOf(41));      // 30초 전 · 40초 전
    }

    @Test
    @DisplayName("최근일수록 앞에 온다 — 목록 정렬 기준")
    void ordersNewestFirst() {
        assertThat(ageOf(10)).isLessThan(ageOf(70));
        assertThat(ageOf(70)).isLessThan(ageOf(3700));
        assertThat(ageOf(3700)).isLessThan(ageOf(2L * 86_400));
        assertThat(ageOf(2L * 86_400)).isLessThan(ageOf(21L * 86_400));
    }

    @Test
    @DisplayName("미래 시각(시계 어긋남)은 0초로 흡수한다")
    void futureTimestampAbsorbed() {
        assertThat(RegistrationAge.of(NOW.plusMinutes(5), NOW).getDescription()).isEqualTo("방금");
    }
}
