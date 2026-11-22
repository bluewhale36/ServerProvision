package com.example.serverprovision.execution.engine.osinstall;

import com.example.serverprovision.execution.entity.GuestServer;
import com.example.serverprovision.execution.entity.ProvisioningHistory;
import com.example.serverprovision.execution.entity.ProvisioningProgress;
import com.example.serverprovision.execution.enums.ProvisioningMotion;
import com.example.serverprovision.execution.enums.ProvisioningPhaseStep;
import com.example.serverprovision.execution.event.GuestServerChangedEvent;
import com.example.serverprovision.execution.repository.ProvisioningProgressRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.Set;

/**
 * 재PXE 없는 게스트의 설치 시한 스윕(E4-1-a-4 D-7, E4-1-b-4-1 에서 계열 공통으로 옮김) — 부트 순서가 디스크 우선이면
 * 설치기의 재부팅이 /boot 로 돌아오지 않아 재진입 판정이 일어나지 않는다. 설치 중 게스트마다 열린 행을 적은 계열 설치기에
 * 판정을 맡긴다(계열마다 시한이 다르고, 남의 행을 집지 않게).
 */
@Component
@RequiredArgsConstructor
public class OsInstallTimeoutSweeper {

    private final ProvisioningProgressRepository progressRepository;
    private final OsInstallOpenRows openRows;
    private final OsInstallingExecutor executor;
    private final ApplicationEventPublisher eventPublisher;

    /** @return 이번 스윕이 실패 전환한 게스트 수 */
    @Scheduled(fixedDelayString = "${provision.os-install.sweep-interval:5m}")
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
            Optional<ProvisioningHistory> row = openRows.latestOpen(server.getId());
            if (row.isPresent() && executor.sweep(server, progress, row.get(), now)) {
                eventPublisher.publishEvent(new GuestServerChangedEvent(server.getId()));
                failed++;
            }
        }
        return failed;
    }
}
