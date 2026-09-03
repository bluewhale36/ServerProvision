package com.example.serverprovision.execution.engine.windows;

import java.nio.file.Path;

/**
 * 설치 소스 루트에서 산출한 정적 자산 셋의 경로와 존재 여부 — 준비도 판정(존재)과 번들 조립(경로)이 같은 관측을 읽는다.
 * 소스 미설정이면 {@link #none()}.
 */
public record WindowsInstallAssets(
        Path wimboot, boolean wimbootPresent,
        Path bootWim, boolean bootWimPresent,
        Path setupExe, boolean setupExePresent
) {

    public static WindowsInstallAssets none() {
        return new WindowsInstallAssets(null, false, null, false, null, false);
    }
}
