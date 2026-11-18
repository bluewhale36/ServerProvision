package com.example.serverprovision.management.os.enums;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** E4-1-b-2 D-2 — 지원 메이저 판정(버전 문자열의 첫 '.' 앞 숫자). 지원 목록의 정본. */
class RhelReleaseTest {

    @Test
    @DisplayName("Rocky Linux 8 · 9 · 10 의 세부 버전은 상수로, 그 밖 메이저 · 다른 OS · 형식 오류는 empty")
    void of() {
        assertThat(RhelRelease.of(OSName.ROCKY_LINUX, "8.10")).contains(RhelRelease.ROCKY_8);
        assertThat(RhelRelease.of(OSName.ROCKY_LINUX, "9.6")).contains(RhelRelease.ROCKY_9);
        assertThat(RhelRelease.of(OSName.ROCKY_LINUX, "10.0")).contains(RhelRelease.ROCKY_10);
        assertThat(RhelRelease.of(OSName.ROCKY_LINUX, "10")).contains(RhelRelease.ROCKY_10);
        assertThat(RhelRelease.of(OSName.ROCKY_LINUX, " 9.6 ")).contains(RhelRelease.ROCKY_9);
        assertThat(RhelRelease.of(OSName.ROCKY_LINUX, "7.9")).isEmpty();
        assertThat(RhelRelease.of(OSName.ROCKY_LINUX, "11.0")).isEmpty();
        assertThat(RhelRelease.of(OSName.ROCKY_LINUX, "10-beta")).isEmpty();
        assertThat(RhelRelease.of(OSName.ROCKY_LINUX, "abc")).isEmpty();
        assertThat(RhelRelease.of(OSName.ROCKY_LINUX, null)).isEmpty();
        assertThat(RhelRelease.of(OSName.CENTOS, "9.0")).isEmpty();
    }

    @Test
    @DisplayName("root SSH 플래그는 10 만 · covers 는 같은 메이저의 세부 버전만 · 지원 목록 문장")
    void constantBehaviour() {
        assertThat(RhelRelease.ROCKY_10.supportsRootSshFlag()).isTrue();
        assertThat(RhelRelease.ROCKY_9.supportsRootSshFlag()).isFalse();
        assertThat(RhelRelease.ROCKY_8.supportsRootSshFlag()).isFalse();
        assertThat(RhelRelease.ROCKY_9.covers("9.6")).isTrue();
        assertThat(RhelRelease.ROCKY_9.covers("10.0")).isFalse();
        assertThat(RhelRelease.ROCKY_9.covers("9")).isTrue();
        assertThat(RhelRelease.ROCKY_9.displayName()).isEqualTo("Rocky Linux 9");
        assertThat(RhelRelease.supportedMajors(OSName.ROCKY_LINUX)).isEqualTo("8, 9, 10");
        assertThat(RhelRelease.supportedMajors(OSName.UBUNTU)).isEmpty();
    }
}
