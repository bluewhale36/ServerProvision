package com.example.serverprovision.execution.engine.windows;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;

/**
 * Windows 설치의 두 눈금(E4-1-a-3 D-2) — 서빙 시각부터의 설치 시한과 재진입 상한. {@code HoldTtlPolicy} 와 동형으로
 * 값과 그 값을 쓰는 계산(만료 · 잔여 분)을 한곳에 둔다. 실행기(실패 전환)와 화면(잔여 표시)이 같은 객체를 본다.
 * 기본값 60분 · 5회는 실측 3호(첫 로그온까지 11.5분 · 정상 재부팅 2~3회)에 여유를 둔 값이다(CP1 결정).
 * 스윕 유예 30분(E4-1-a-4)은 재PXE 없는 게스트에게만 쓰인다 — 실행기의 재진입 판정은 시한만 본다.
 */
@Component
public class WindowsInstallTimeoutPolicy {

    private final Duration installTimeout;
    private final int maxReentries;
    private final Duration sweepGrace;

    public WindowsInstallTimeoutPolicy(
            @Value("${provision.windows-install.install-timeout:60m}") Duration installTimeout,
            @Value("${provision.windows-install.max-reentries:5}") int maxReentries,
            @Value("${provision.windows-install.sweep-grace:30m}") Duration sweepGrace) {
        this.installTimeout = installTimeout;
        this.maxReentries = maxReentries;
        this.sweepGrace = sweepGrace;
    }

    public Duration installTimeout() {
        return installTimeout;
    }

    public int maxReentries() {
        return maxReentries;
    }

    /** 스윕 유예(E4-1-a-4 D-7 · OQ-1) — 시한 뒤 이만큼 더 기다린 뒤에야 재PXE 없는 게스트를 실패로 본다. */
    public Duration sweepGrace() {
        return sweepGrace;
    }

    public boolean isExpired(LocalDateTime servedAt, LocalDateTime now) {
        return servedAt != null && servedAt.plus(installTimeout).isBefore(now);
    }

    /** 스윕이 실패 전환할 때가 됐는가 — 서빙 + 시한 + 유예 < now. */
    public boolean isSweepDue(LocalDateTime servedAt, LocalDateTime now) {
        return servedAt != null && servedAt.plus(installTimeout).plus(sweepGrace).isBefore(now);
    }

    /** 시한까지 남은 분 — 이미 지났으면 0(화면이 음수를 그리지 않게). */
    public long remainingMinutes(LocalDateTime servedAt, LocalDateTime now) {
        if (servedAt == null) {
            return 0L;
        }
        long elapsed = Duration.between(servedAt, now).toMinutes();
        return Math.max(0L, installTimeout.toMinutes() - elapsed);
    }
}
