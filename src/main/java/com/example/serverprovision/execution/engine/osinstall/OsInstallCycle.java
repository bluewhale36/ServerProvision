package com.example.serverprovision.execution.engine.osinstall;

import com.example.serverprovision.execution.engine.boot.IpxeScripts;
import com.example.serverprovision.execution.entity.GuestServer;
import com.example.serverprovision.execution.entity.ProvisioningHistory;
import com.example.serverprovision.execution.entity.ProvisioningProgress;
import com.example.serverprovision.execution.enums.ProvisioningMotion;
import lombok.extern.slf4j.Slf4j;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

/**
 * 설치가 시작된 뒤의 판정을 계열 공통으로 한다(E4-1-b-4-2). 설치 중 재PXE 로 돌아오면 시한과 재진입 상한을 보고 실패로 닫거나
 * 로컬 디스크로 넘긴다. 재PXE 가 없는 게스트는 스윕이 시한 + 유예 뒤에 닫고, 운영자가 실패로 바꾸면 열린 행을 닫는다.
 * 행을 닫은 뒤의 뒷정리(토큰 회수 · 설치 트리 반납)는 계열마다 달라 {@link Cleanup} 으로 받는다.
 */
@Slf4j
public final class OsInstallCycle {

    /** 열린 행을 실패로 닫은 직후 계열이 할 뒷정리. */
    @FunctionalInterface
    public interface Cleanup {
        /** @param closedRow 닫은 행 — 열린 행 없이 실패한 경우(운영자 전환 등)는 empty */
        void afterClosed(GuestServer server, Optional<ProvisioningHistory> closedRow);
    }

    private final OsInstallLedger ledger;
    private final OsInstallTimeoutPolicy policy;
    private final Cleanup cleanup;

    public OsInstallCycle(OsInstallLedger ledger, OsInstallTimeoutPolicy policy, Cleanup cleanup) {
        this.ledger = ledger;
        this.policy = policy;
        this.cleanup = cleanup;
    }

    /** 설치 중(STEP_RUNNING)일 때 이 계열의 열린 행. 서빙 전이면 empty. */
    public Optional<ProvisioningHistory> runningRow(UUID guestServerId, ProvisioningProgress progress) {
        return progress.getMotion() == ProvisioningMotion.STEP_RUNNING
                ? ledger.latestRunning(guestServerId) : Optional.empty();
    }

    /** 설치 중 재진입 — 시한이나 상한을 넘으면 실패로 닫고 실패 안내를, 아니면 로컬 부팅 스크립트를 낸다(E4-1-a-3 D-2). */
    public String reentry(GuestServer server, ProvisioningProgress progress, ProvisioningHistory row,
                          String reentryUrl, LocalDateTime now) {
        LocalDateTime served = ledger.servedAtOf(row);
        int reentries = ledger.reentriesOf(row);
        int max = policy.maxReentries();
        if (policy.isExpired(served, now)) {
            long elapsed = Duration.between(served, now).toMinutes();
            fail(server, progress, row, OsInstallFailure.INSTALL_TIMEOUT,
                    "서빙 후 " + elapsed + "분 — 설치 시한 " + policy.installTimeout().toMinutes() + "분 초과", now);
            log.warn("[{}] {} — 설치 시한 초과, 실패 전환 : served={}, reentries={}", tag(), server.getId(), served, reentries);
            return failedScript(OsInstallFailure.INSTALL_TIMEOUT, reentryUrl);
        }
        if (reentries + 1 > max) {
            fail(server, progress, row, OsInstallFailure.REPXE_LOOP,
                    "재진입 " + (reentries + 1) + "회 — 상한 " + max + "회 초과", now);
            log.warn("[{}] {} — 재진입 상한 초과, 실패 전환 : reentries={}", tag(), server.getId(), reentries + 1);
            return failedScript(OsInstallFailure.REPXE_LOOP, reentryUrl);
        }
        return IpxeScripts.localBootFallthrough(reentries + 1, max);
    }

    /** 재진입 스크립트를 내준 직후 — 같은 행의 재진입 수를 올린다. */
    public void recordReentry(GuestServer server, ProvisioningHistory row, LocalDateTime now) {
        int n = ledger.bumpReentry(row, now);
        log.info("[{}] {} — 설치 중 재진입 {}회 (exit 로 로컬 부팅)", tag(), server.getId(), n);
    }

    /**
     * 새 서빙이 시작되는데 옛 행이 아직 열려 있으면 닫는다 — 열린 행은 언제나 하나다. 뒷정리는 하지 않는다: 이 시점의 토큰 ·
     * 트리 보유는 방금 시작한 새 서빙의 것이다. 닫은 옛 행을 돌려주므로 계열이 옛 서빙에만 묶인 자원을 따로 정리한다.
     */
    public Optional<ProvisioningHistory> supersedeStale(GuestServer server, LocalDateTime now) {
        Optional<ProvisioningHistory> stale = ledger.latestRunning(server.getId());
        stale.ifPresent(row -> ledger.abortRunning(row, OsInstallFailure.SUPERSEDED, "새 서빙으로 대체 — 열린 채 남아 있던 행", now));
        return stale;
    }

    /** 운영자 수동 실패 전환의 뒷정리 — 진행 신호는 호출자가 이미 실패로 바꿨다. 게스트가 보고하지 않아 서버가 닫지 않으면 행이 남는다. */
    public void operatorFailed(GuestServer server, LocalDateTime now) {
        Optional<ProvisioningHistory> row = ledger.latestRunning(server.getId());
        row.ifPresent(open -> ledger.abortRunning(open, OsInstallFailure.OPERATOR, "운영자 수동 실패 전환", now));
        cleanup.afterClosed(server, row);
    }

    /** 재PXE 없는 게스트의 시한 스윕(E4-1-a-4 D-7) — 서빙 + 시한 + 유예가 지났으면 INSTALL_TIMEOUT 으로 닫는다. */
    public boolean sweep(GuestServer server, ProvisioningProgress progress, ProvisioningHistory openRow, LocalDateTime now) {
        LocalDateTime served = ledger.servedAtOf(openRow);
        if (!policy.isSweepDue(served, now)) {
            return false;
        }
        long elapsed = Duration.between(served, now).toMinutes();
        fail(server, progress, openRow, OsInstallFailure.INSTALL_TIMEOUT,
                "서빙 후 " + elapsed + "분 — 재진입 · 완료 보고 없음, 스윕(시한 " + policy.installTimeout().toMinutes()
                        + "분 + 유예 " + policy.sweepGrace().toMinutes() + "분)", now);
        log.warn("[{}] {} — 스윕 실패 전환 : served={}, elapsed={}분", tag(), server.getId(), served, elapsed);
        return true;
    }

    /** 설치 단계 실패를 게스트 콘솔에 알리는 스크립트 — 다음 폴링부터는 실패 대기 스크립트가 받는다. */
    public String failedScript(OsInstallFailureReason reason, String reentryUrl) {
        return IpxeScripts.osInstallFailed(ledger.origin().consoleLabel(), reason.code(), reentryUrl);
    }

    private void fail(GuestServer server, ProvisioningProgress progress, ProvisioningHistory row,
                      OsInstallFailureReason reason, String detail, LocalDateTime now) {
        ledger.failRunning(server, progress, row, reason, detail, now);
        cleanup.afterClosed(server, Optional.of(row));
    }

    private String tag() {
        return ledger.origin().logTag();
    }
}
