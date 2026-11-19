package com.example.serverprovision.management.os.enums;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

/** S23-1 — RHEL 계열 메이저 상수: 배포판 상수가 정확히 한 메이저에 속하고, 메이저 능력은 메이저 번호로 판정한다. */
class RhelReleaseTest {

    @Test
    @DisplayName("불변식: 지원하는 RHEL 계열 OS 상수는 정확히 한 메이저에 속하고, 그 메이저 번호가 상수의 메이저와 같다")
    void everyRhelOsNameBelongsToExactlyOneRelease() {
        for (OSName name : OSName.registrableValues()) {
            long owners = Arrays.stream(RhelRelease.values()).filter(r -> r.osNames().contains(name)).count();
            if (name.getFamily() == OSFamily.RHEL_BASED) {
                assertThat(owners).as(name.name()).isEqualTo(1);
                assertThat(RhelRelease.of(name).orElseThrow().majorVersion()).as(name.name()).isEqualTo(name.getMajor());
            } else {
                assertThat(owners).as(name.name()).isZero();
            }
        }
    }

    @Test
    @DisplayName("of(OSName): Rocky 9 와 RHEL 9 는 R9, 미지원 상수 · Windows · Ubuntu 는 empty")
    void ofOsName() {
        assertThat(RhelRelease.of(OSName.ROCKY_LINUX_9)).contains(RhelRelease.R9);
        assertThat(RhelRelease.of(OSName.RHEL_9)).contains(RhelRelease.R9);
        assertThat(RhelRelease.of(OSName.RHEL_10)).contains(RhelRelease.R10);
        assertThat(RhelRelease.of(OSName.ROCKY_LINUX)).isEmpty();
        assertThat(RhelRelease.of(OSName.CENTOS)).isEmpty();
        assertThat(RhelRelease.of(OSName.WINDOWS_SERVER_2025)).isEmpty();
        assertThat(RhelRelease.of(OSName.UBUNTU)).isEmpty();
    }

    @Test
    @DisplayName("of(OSName, 버전): 버전이 상수의 메이저에 맞을 때만 돌려준다(UE 계약 유지)")
    void ofOsNameAndVersion() {
        assertThat(RhelRelease.of(OSName.ROCKY_LINUX_9, "9.6")).contains(RhelRelease.R9);
        assertThat(RhelRelease.of(OSName.ROCKY_LINUX_9, " 9 ")).contains(RhelRelease.R9);
        assertThat(RhelRelease.of(OSName.ROCKY_LINUX_9, "10.0")).isEmpty();
        assertThat(RhelRelease.of(OSName.ROCKY_LINUX_9, "abc")).isEmpty();
        assertThat(RhelRelease.of(OSName.ROCKY_LINUX_9, null)).isEmpty();
        assertThat(RhelRelease.of(OSName.ROCKY_LINUX, "9.6")).isEmpty();   // 미지원 상수
        assertThat(RhelRelease.of(OSName.CENTOS, "9.0")).isEmpty();
    }

    @Test
    @DisplayName("root SSH 플래그는 메이저 10 이상, covers 는 같은 메이저의 버전만, majorOf 는 첫 '.' 앞 숫자")
    void majorCapabilities() {
        assertThat(RhelRelease.R10.supportsRootSshFlag()).isTrue();
        assertThat(RhelRelease.R9.supportsRootSshFlag()).isFalse();
        assertThat(RhelRelease.R8.supportsRootSshFlag()).isFalse();
        assertThat(RhelRelease.R9.covers("9.6")).isTrue();
        assertThat(RhelRelease.R9.covers("9")).isTrue();
        assertThat(RhelRelease.R9.covers("10.0")).isFalse();
        assertThat(RhelRelease.majorOf("10.0")).contains(10);
        assertThat(RhelRelease.majorOf("10-beta")).isEmpty();
    }
}
