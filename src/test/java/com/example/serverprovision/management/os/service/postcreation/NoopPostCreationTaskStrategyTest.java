package com.example.serverprovision.management.os.service.postcreation;

import com.example.serverprovision.management.os.entity.ISO;
import com.example.serverprovision.management.os.entity.OSMetadata;
import com.example.serverprovision.management.os.enums.OSFamily;
import com.example.serverprovision.management.os.enums.OSName;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * S5-7 — Ubuntu / Windows ISO 등록 시 자동 작업 noop 검증.
 */
class NoopPostCreationTaskStrategyTest {

    private final NoopPostCreationTaskStrategy strategy = new NoopPostCreationTaskStrategy();

    @Test
    @DisplayName("supports : DEBIAN_BASED / WINDOWS_BASED 모두 처리")
    void supportsDebianAndWindows() {
        assertThat(strategy.supports(OSFamily.DEBIAN_BASED)).isTrue();
        assertThat(strategy.supports(OSFamily.WINDOWS_BASED)).isTrue();
        assertThat(strategy.supports(OSFamily.RHEL_BASED)).isFalse();
    }

    @Test
    @DisplayName("trigger : Ubuntu 도 null 반환 (자동 작업 없음)")
    void triggerUbuntuReturnsNull() {
        OSMetadata osMetadata = OSMetadata.builder().osName(OSName.UBUNTU).osVersion("22.04").build();
        ISO iso = ISO.builder().osMetadata(osMetadata).isoPath("/opt/iso/ubuntu/22.04/dvd.iso").build();

        assertThat(strategy.trigger(osMetadata, iso)).isNull();
    }

    @Test
    @DisplayName("trigger : Windows 도 null 반환")
    void triggerWindowsReturnsNull() {
        OSMetadata osMetadata = OSMetadata.builder().osName(OSName.WINDOWS).osVersion("11").build();
        ISO iso = ISO.builder().osMetadata(osMetadata).isoPath("/opt/iso/windows/11/setup.iso").build();

        assertThat(strategy.trigger(osMetadata, iso)).isNull();
    }
}
