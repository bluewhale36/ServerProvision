package com.example.serverprovision.management.subprogram.enums;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** R15-1 D-4 — 진입점 확장자가 설치 방식을 정한다. */
class InstallEntrypointKindTest {

    @Test
    @DisplayName("확장자(대소문자 무시)로 INF · MSI · EXE 를 가르고, 그 외 · 확장자 없음 · 끝이 점 · null 은 empty")
    void fromPath() {
        assertThat(InstallEntrypointKind.fromPath("WIN2025/astgrp.inf")).contains(InstallEntrypointKind.INF);
        assertThat(InstallEntrypointKind.fromPath("WDDM Installer/Win2025.MSI")).contains(InstallEntrypointKind.MSI);
        assertThat(InstallEntrypointKind.fromPath("setup\\Setup.exe")).contains(InstallEntrypointKind.EXE);
        assertThat(InstallEntrypointKind.fromPath("readme.txt")).isEmpty();
        assertThat(InstallEntrypointKind.fromPath("noext")).isEmpty();
        assertThat(InstallEntrypointKind.fromPath("dir.msi/file")).isEmpty();   // 디렉토리 이름의 점은 무시
        assertThat(InstallEntrypointKind.fromPath("x.")).isEmpty();
        assertThat(InstallEntrypointKind.fromPath(null)).isEmpty();
    }
}
