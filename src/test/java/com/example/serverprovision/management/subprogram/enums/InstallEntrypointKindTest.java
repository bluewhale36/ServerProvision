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

    @Test
    @DisplayName("HF23 — 끝이 / (또는 \\) 이면 FOLDER — 폴더 이름의 점(QAT2.0…)은 확장자로 읽지 않는다")
    void fromPath_folder() {
        assertThat(InstallEntrypointKind.fromPath("PRO1000/Winx64/WS2025/")).contains(InstallEntrypointKind.FOLDER);
        assertThat(InstallEntrypointKind.fromPath("PROXGB\\Winx64\\WS2025\\")).contains(InstallEntrypointKind.FOLDER);
        assertThat(InstallEntrypointKind.fromPath("QAT2.0.W.2.2.0-0018/")).contains(InstallEntrypointKind.FOLDER);
        assertThat(InstallEntrypointKind.fromPath("PRO1000/Winx64/WS2025")).isEmpty();   // 슬래시 없는 폴더는 저장 시 서비스가 / 를 붙인다
    }
}
