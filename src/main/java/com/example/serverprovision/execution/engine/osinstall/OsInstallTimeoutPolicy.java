package com.example.serverprovision.execution.engine.osinstall;

import java.time.Duration;
import java.time.LocalDateTime;

/**
 * OS 설치의 두 눈금과 스윕 유예(E4-1-a-3 D-2, E4-1-b-4-2 에서 계열 공통으로 옮김). 서빙 시각부터의 설치 시한과 재진입 상한,
 * 재PXE 없는 게스트에게만 쓰는 스윕 유예를 한곳에 두고, 그 값으로 하는 계산(만료 · 잔여 분)도 여기서 한다. 설치기(실패 전환)와
 * 화면(잔여 표시)이 같은 객체를 본다. 계열마다 설정 키가 달라 계열마다 하위 빈 하나를 둔다.
 */
public abstract class OsInstallTimeoutPolicy {

    private final Duration installTimeout;
    private final int maxReentries;
    private final Duration sweepGrace;

    protected OsInstallTimeoutPolicy(Duration installTimeout, int maxReentries, Duration sweepGrace) {
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

    /** 시한 뒤 이만큼 더 기다린 뒤에야 재PXE 없는 게스트를 실패로 본다. */
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
