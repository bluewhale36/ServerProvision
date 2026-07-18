package com.example.serverprovision.execution.vo;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** E1-0b CP4 — 게스트 토큰 발급 형식·대조. */
class GuestTokenTest {

    @Test
    @DisplayName("issue — 대시 없는 32자 16진 + 매 발급 상이")
    void issue_format() {
        GuestToken a = GuestToken.issue();
        GuestToken b = GuestToken.issue();
        assertThat(a.value()).matches("[0-9a-f]{32}");
        assertThat(a.value()).isNotEqualTo(b.value());
    }

    @Test
    @DisplayName("matches — 대조 단일 지점")
    void matches() {
        GuestToken t = new GuestToken("a3f9d2c8b41e4f7a9c0d5e6f7a8b9c1d");
        assertThat(t.matches("a3f9d2c8b41e4f7a9c0d5e6f7a8b9c1d")).isTrue();
        assertThat(t.matches("deadbeef")).isFalse();
    }

    @Test
    @DisplayName("빈 값 거부 — 무의미 토큰의 표현 불가")
    void blank_rejected() {
        assertThatThrownBy(() -> new GuestToken(" ")).isInstanceOf(IllegalArgumentException.class);
    }
}
