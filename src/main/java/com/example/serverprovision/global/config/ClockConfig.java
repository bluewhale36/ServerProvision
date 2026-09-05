package com.example.serverprovision.global.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/**
 * 시각 소스 빈(HF13). 운영은 시스템 시계 그대로이고, 시각에 기대는 판정을 가진 서비스가 이 빈을 주입받아
 * 테스트에서는 고정 Clock 으로 바꿔 끼운다 — 두 지점(DriftBulkApplyService · GuestServerQueryService)만 쓴다.
 */
@Configuration
public class ClockConfig {

    @Bean
    public Clock clock() {
        return Clock.systemDefaultZone();
    }
}
