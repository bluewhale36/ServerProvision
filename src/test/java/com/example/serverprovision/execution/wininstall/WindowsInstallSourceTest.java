package com.example.serverprovision.execution.wininstall;

import com.example.serverprovision.execution.engine.windows.WindowsInstallAssets;
import com.example.serverprovision.execution.wininstall.config.WindowsInstallProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/** E4-1-a-3 CP4 — 소스 루트의 자산 경로 · 존재 · wimboot SHA-256(런북 해시와 대조하는 chip 재료). */
class WindowsInstallSourceTest {

    @TempDir Path root;

    private WindowsInstallSource source(String rootValue) {
        return new WindowsInstallSource(new WindowsInstallProperties(rootValue, null, null, null, null, null));
    }

    @Test
    @DisplayName("미설정 — none(): 경로 null · 존재 false · 해시 empty")
    void notConfigured() {
        WindowsInstallAssets assets = source("").assets();
        assertThat(assets.wimbootPresent()).isFalse();
        assertThat(assets.bootWim()).isNull();
        assertThat(source("").wimbootSha256()).isEmpty();
    }

    @Test
    @DisplayName("구성 — 슬롯 enum 의 경로(루트 wimboot · sources/boot.wim · sources/setup.exe)와 존재 여부 · SHA-256 은 알려진 값")
    void configured_pathsAndDigest() throws IOException {
        Files.createDirectories(root.resolve("sources"));
        Files.writeString(root.resolve("wimboot"), "abc");
        Files.writeString(root.resolve("sources/boot.wim"), "x");
        WindowsInstallSource source = source(root.toString());

        WindowsInstallAssets assets = source.assets();

        assertThat(assets.wimboot()).isEqualTo(root.toAbsolutePath().normalize().resolve("wimboot"));
        assertThat(assets.wimbootPresent()).isTrue();
        assertThat(assets.bootWimPresent()).isTrue();
        assertThat(assets.setupExe()).isEqualTo(root.toAbsolutePath().normalize().resolve("sources/setup.exe"));
        assertThat(assets.setupExePresent()).isFalse();
        assertThat(source.wimbootSha256()).contains("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
    }
}
