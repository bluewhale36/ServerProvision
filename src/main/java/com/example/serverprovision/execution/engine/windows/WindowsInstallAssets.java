package com.example.serverprovision.execution.engine.windows;

import java.nio.file.Path;

/**
 * 설치 소스 루트에서 산출한 정적 자산 셋의 경로와 존재 여부 — 준비도 판정(존재)과 번들 조립(경로)이 같은 관측을 읽는다.
 * 소스 미설정이면 {@link #none()}. 설치 후 스크립트 둘(E4-1-a-4)은 {@code sources/$OEM$} 아래 — 조립 액션이 만든다.
 */
public record WindowsInstallAssets(
        Path wimboot, boolean wimbootPresent,
        Path bootWim, boolean bootWimPresent,
        Path setupExe, boolean setupExePresent,
        Path oemSetupComplete, boolean oemSetupCompletePresent,
        Path oemReport, boolean oemReportPresent
) {

    public static WindowsInstallAssets none() {
        return new WindowsInstallAssets(null, false, null, false, null, false, null, false, null, false);
    }

    /** 설치 후 스크립트 둘이 모두 있는가 — 준비도 12행의 재료. */
    public boolean oemScriptsPresent() {
        return oemSetupCompletePresent && oemReportPresent;
    }
}
