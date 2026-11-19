package com.example.serverprovision.management.os.enums;

import com.example.serverprovision.management.os.vo.WindowsBuildNumber;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** S23-1 — Windows Server 버전 상수: OS 상수와의 대응과 install.wim 빌드 대조. */
class WindowsServerReleaseTest {

    @Test
    @DisplayName("WINDOWS_SERVER_2025 는 W2025(빌드 26100)에 속하고, 미지원 WINDOWS_SERVER 와 다른 계열은 속하지 않는다")
    void of() {
        assertThat(WindowsServerRelease.of(OSName.WINDOWS_SERVER_2025)).contains(WindowsServerRelease.W2025);
        assertThat(WindowsServerRelease.W2025.build()).isEqualTo(WindowsBuildNumber.of(26100));
        assertThat(WindowsServerRelease.of(OSName.WINDOWS_SERVER)).isEmpty();
        assertThat(WindowsServerRelease.of(OSName.ROCKY_LINUX_9)).isEmpty();
    }

    @Test
    @DisplayName("빌드 대조: 10.0.26100.x 는 W2025, 10.0.20348.x(2022) · 빈 값 · 형식 오류는 맞지 않는다")
    void matchesBuild() {
        assertThat(WindowsServerRelease.W2025.matchesBuild("10.0.26100.1742")).isTrue();
        assertThat(WindowsServerRelease.W2025.matchesBuild("10.0.20348.587")).isFalse();
        assertThat(WindowsServerRelease.W2025.matchesBuild("")).isFalse();
        assertThat(WindowsServerRelease.ofBuild("10.0.26100.1")).contains(WindowsServerRelease.W2025);
        assertThat(WindowsServerRelease.ofBuild("10.0.20348.587")).isEmpty();
    }
}
