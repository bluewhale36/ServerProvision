package com.example.serverprovision.execution.installtree.vo;

import com.example.serverprovision.management.os.enums.OSName;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/** {@code .disk/info} 첫 줄 해석과 버전 대조 재료. */
class UbuntuDiskInfoTest {

    @Test
    @DisplayName("live-server 줄: 점 버전과 아키텍처를 읽는다(24.04.1 · 22.04.4 · 점 버전 없는 22.04)")
    void parsesServerLines() {
        assertThat(UbuntuDiskInfo.parse("Ubuntu-Server 24.04.1 LTS \"Noble Numbat\" - Release amd64 (20240827)"))
                .contains(new UbuntuDiskInfo("24.04.1", "amd64"));
        assertThat(UbuntuDiskInfo.parse("Ubuntu-Server 22.04.4 LTS \"Jammy Jellyfish\" - Release amd64 (20240216)"))
                .contains(new UbuntuDiskInfo("22.04.4", "amd64"));
        assertThat(UbuntuDiskInfo.parse("Ubuntu-Server 22.04 LTS \"Jammy Jellyfish\" - Release amd64 (20220419)"))
                .contains(new UbuntuDiskInfo("22.04", "amd64"));
    }

    @Test
    @DisplayName("arm64 는 읽지만 설치할 수 없다")
    void arm64NotInstallable() {
        UbuntuDiskInfo arm = UbuntuDiskInfo.parse("Ubuntu-Server 24.04.1 LTS \"Noble Numbat\" - Release arm64 (20240827)").orElseThrow();
        assertThat(arm.installable()).isFalse();
        assertThat(new UbuntuDiskInfo("24.04.1", "amd64").installable()).isTrue();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {
            "Ubuntu 24.04.1 LTS \"Noble Numbat\" - Release amd64 (20240827)",
            "Kubuntu 24.04 LTS - Release amd64",
            "Rocky Linux 9.5",
            "Ubuntu-Server noble - Release amd64",
            "Ubuntu-Server 24.04.1 LTS - Release i386"
    })
    @DisplayName("desktop · 다른 배포판 · 버전 없음 · 모르는 아키텍처 · 빈 줄은 empty")
    void rejectsOthers(String line) {
        assertThat(UbuntuDiskInfo.parse(line)).isEmpty();
    }

    @Test
    @DisplayName("release 는 OS 카탈로그와 같은 배포판 이름 Ubuntu 로 담아 같은 메이저면 맞고 다르면 다르다")
    void releaseMatchesCatalog() {
        TreeRelease release = new UbuntuDiskInfo("24.04.1", "amd64").release();
        assertThat(release).isEqualTo(new TreeRelease("Ubuntu", "24.04.1"));
        assertThat(release.matches(OSName.UBUNTU, "24.04")).isTrue();
        assertThat(release.matches(OSName.UBUNTU, "22.04")).isFalse();
    }

    @Test
    @DisplayName("파일에서는 첫 줄만 읽는다. 파일이 없으면 empty")
    void readsFirstLine(@TempDir Path temp) throws IOException {
        assertThat(UbuntuDiskInfo.read(temp)).isEmpty();
        Files.createDirectories(temp.resolve(".disk"));
        Files.writeString(temp.resolve(".disk/info"), "Ubuntu-Server 24.04.1 LTS - Release amd64 (20240827)\nsecond line\n");
        assertThat(UbuntuDiskInfo.read(temp)).contains(new UbuntuDiskInfo("24.04.1", "amd64"));
    }
}
