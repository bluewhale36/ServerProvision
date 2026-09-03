package com.example.serverprovision.execution.engine.windows;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/** E4-1-a-3 CP4 — 설치 시한 · 재진입 상한(CP1 결정 60분 · 5회)의 만료 · 잔여 계산. */
class WindowsInstallTimeoutPolicyTest {

    private static final LocalDateTime SERVED = LocalDateTime.of(2026, 9, 3, 10, 0);
    private final WindowsInstallTimeoutPolicy policy = new WindowsInstallTimeoutPolicy(Duration.ofMinutes(60), 5, Duration.ofMinutes(30));

    @Test
    @DisplayName("만료 — 서빙 + 60분을 지나야 만료, 정확히 60분은 아직")
    void isExpired_boundary() {
        assertThat(policy.isExpired(SERVED, SERVED.plusMinutes(60))).isFalse();
        assertThat(policy.isExpired(SERVED, SERVED.plusMinutes(60).plusSeconds(1))).isTrue();
        assertThat(policy.isExpired(null, SERVED)).isFalse();
    }

    @Test
    @DisplayName("잔여 분 — 60 - 경과, 지났으면 0(화면이 음수를 그리지 않는다), 서빙 전 0")
    void remainingMinutes() {
        assertThat(policy.remainingMinutes(SERVED, SERVED.plusMinutes(18))).isEqualTo(42L);
        assertThat(policy.remainingMinutes(SERVED, SERVED.plusMinutes(95))).isZero();
        assertThat(policy.remainingMinutes(null, SERVED)).isZero();
        assertThat(policy.maxReentries()).isEqualTo(5);
        assertThat(policy.installTimeout()).isEqualTo(Duration.ofMinutes(60));
    }

    @Test
    @DisplayName("isSweepDue(E4-1-a-4) — 서빙 + 시한 + 유예(90분) 경계: 89분 false · 91분 true · served null false · sweepGrace 30분")
    void sweepDue_boundary() {
        assertThat(policy.sweepGrace()).isEqualTo(Duration.ofMinutes(30));
        assertThat(policy.isSweepDue(SERVED, SERVED.plusMinutes(89))).isFalse();
        assertThat(policy.isSweepDue(SERVED, SERVED.plusMinutes(90))).isFalse();
        assertThat(policy.isSweepDue(SERVED, SERVED.plusMinutes(91))).isTrue();
        assertThat(policy.isSweepDue(null, SERVED)).isFalse();
        assertThat(policy.isExpired(SERVED, SERVED.plusMinutes(61))).isTrue();   // 실행기의 시한 판정은 유예를 모른다
    }
}
