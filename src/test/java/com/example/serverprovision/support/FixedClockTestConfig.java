package com.example.serverprovision.support;

import org.springframework.boot.test.context.TestConfiguration;
import com.example.serverprovision.global.web.list.ListDateRangeDefaults;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

/**
 * 웹 슬라이스 테스트용 고정 시계 — {@code @WebMvcTest} 는 운영 {@code ClockConfig} 를 올리지 않는다.
 * 목록 기간 조건의 기본 범위(S8-4 · S8-5)처럼 '오늘'에 기대는 화면을 날짜와 무관하게 검증한다.
 * 기본 범위를 모델에 싣는 {@link ListDateRangeDefaults} 도 함께 올린다(웹 슬라이스는 @Component 를 스캔하지 않는다).
 */
@TestConfiguration
@Import(ListDateRangeDefaults.class)
public class FixedClockTestConfig {

    public static final ZoneId ZONE = ZoneId.of("Asia/Seoul");
    public static final LocalDate TODAY = LocalDate.of(2026, 10, 11);

    @Bean
    Clock clock() {
        return Clock.fixed(Instant.parse("2026-10-11T03:00:00Z"), ZONE);
    }
}
