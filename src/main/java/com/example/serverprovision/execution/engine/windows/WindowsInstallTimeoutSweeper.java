package com.example.serverprovision.execution.engine.windows;

import com.example.serverprovision.execution.entity.GuestServer;
import com.example.serverprovision.execution.entity.ProvisioningHistory;
import com.example.serverprovision.execution.entity.ProvisioningProgress;
import com.example.serverprovision.execution.enums.ProvisioningMotion;
import com.example.serverprovision.execution.enums.ProvisioningPhaseStep;
import com.example.serverprovision.execution.event.GuestServerChangedEvent;
import com.example.serverprovision.execution.repository.ProvisioningProgressRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.Set;

/**
 * 재PXE 없는 게스트의 설치 시한을 닫는 스윕(E4-1-a-4 D-7) — 부트 순서가 디스크 우선이면 Setup 의 재부팅이 /boot 로
 * 돌아오지 않아 실행기의 시한 판정이 영원히 일어나지 않는다(-3 D-8 공백). 서빙 시각 + 설치 시한 + 유예가 지난 설치 중
 * 게스트를 INSTALL_TIMEOUT 으로 실패 전환하고 토큰을 회수한다. 유예를 두는 이유: 시한 직후에는 첫 로그온 보고가 아직
 * 오는 중일 수 있다(실측 3호 11.5분 · 대형 드라이버 여유). 하트비트는 두지 않는다 — 로그만.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WindowsInstallTimeoutSweeper {

    private final ProvisioningProgressRepository progressRepository;
    private final WindowsInstallLedger ledger;
    private final WindowsInstallTimeoutPolicy timeoutPolicy;
    private final WindowsInstallTokenRegistry tokenRegistry;
    private final ApplicationEventPublisher eventPublisher;

    /** @return 이번 스윕이 실패 전환한 게스트 수 */
    @Scheduled(fixedDelayString = "${provision.windows-install.sweep-interval:5m}")
    @Transactional
    public int sweep() {
        LocalDateTime now = LocalDateTime.now();
        int failed = 0;
        for (ProvisioningProgress progress : progressRepository
                .findAllByCurrentStepInAndFailedAtIsNullAndCompletedAtIsNull(Set.of(ProvisioningPhaseStep.OS_INSTALLING))) {
            if (progress.getMotion() != ProvisioningMotion.STEP_RUNNING) {
                continue;                                       // 서빙 전(AWAITING_BOOT · HOLD)은 시한이 흐르지 않는다
            }
            GuestServer server = progress.getGuestServer();
            Optional<ProvisioningHistory> row = ledger.latestRunning(server.getId());
            if (row.isEmpty()) {
                continue;
            }
            LocalDateTime served = ledger.servedAtOf(row.get());
            if (!timeoutPolicy.isSweepDue(served, now)) {
                continue;
            }
            long elapsed = Duration.between(served, now).toMinutes();
            ledger.failRunning(server, progress, row.get(), WindowsInstallLedger.INSTALL_TIMEOUT,
                    "서빙 후 " + elapsed + "분 — 재진입 · 완료 보고 없음, 스윕(시한 " + timeoutPolicy.installTimeout().toMinutes()
                            + "분 + 유예 " + timeoutPolicy.sweepGrace().toMinutes() + "분)", now);
            tokenRegistry.revoke(server.getId());
            eventPublisher.publishEvent(new GuestServerChangedEvent(server.getId()));
            failed++;
            log.warn("[wininstall] {} — 스윕 실패 전환 : served={}, elapsed={}분", server.getId(), served, elapsed);
        }
        return failed;
    }
}
