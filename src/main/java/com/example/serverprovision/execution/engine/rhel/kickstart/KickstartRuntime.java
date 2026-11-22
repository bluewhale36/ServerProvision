package com.example.serverprovision.execution.engine.rhel.kickstart;

import java.util.Objects;

/**
 * 실행 때만 아는 kickstart 재료 — 설치 원본 URL(b-4 의 토큰 경로), 대상 디스크(b-6), 첫 부팅 완료 보고 재료(b-5).
 * 디스크는 필수다: 디스크 없이 {@code clearpart --all} 만 나가면 데이터 볼륨까지 지운다.
 */
public record KickstartRuntime(String installSourceUrl, InstallDisk disk, FirstBootReport report) {

    public KickstartRuntime {
        Objects.requireNonNull(installSourceUrl, "installSourceUrl");
        Objects.requireNonNull(disk, "disk");
        Objects.requireNonNull(report, "report");
    }
}
