package com.example.serverprovision.execution.engine.rhel.kickstart;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InstallDiskTest {

    @Test
    @DisplayName("by-id 경로는 disk/by-id/ + 이름")
    void byIdPath() {
        assertThat(new InstallDisk("wwn-0x5000c500a1b2c3d4").byIdPath()).isEqualTo("disk/by-id/wwn-0x5000c500a1b2c3d4");
        assertThat(new InstallDisk("nvme-eui.0025385b71b0a2d2").byIdPath()).isEqualTo("disk/by-id/nvme-eui.0025385b71b0a2d2");
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "sda b", "../sda", "wwn-0x1\nzerombr", "a/b"})
    @DisplayName("공백 · 경로 구분 · 개행이 든 이름은 거절")
    void rejectsUnsafe(String name) {
        assertThatThrownBy(() -> new InstallDisk(name)).isInstanceOf(IllegalArgumentException.class);
    }
}
