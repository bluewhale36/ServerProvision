package com.example.serverprovision.execution.engine.osinstall;

import com.example.serverprovision.execution.entity.GuestServer;
import com.example.serverprovision.execution.entity.ProvisioningHistory;
import com.example.serverprovision.execution.entity.ProvisioningProgress;
import com.example.serverprovision.execution.enums.ProvisioningPhaseStep;
import com.example.serverprovision.execution.event.GuestServerChangedEvent;
import com.example.serverprovision.execution.repository.ProvisioningProgressRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * E4-1-a-4 D-7 · E4-1-b-4-1 — 계열 공통 시한 스윕: 설치 중(STEP_RUNNING) 게스트의 열린 OS 설치 행을 실행기(→ 행을 적은 설치기)에
 * 맡기고, 실패 전환이면 화면 갱신 이벤트를 낸다. 서빙 전 · 열린 행 없음은 건드리지 않는다.
 */
@ExtendWith(MockitoExtension.class)
class OsInstallTimeoutSweeperTest {

    private static final LocalDateTime SERVED = LocalDateTime.of(2026, 9, 3, 10, 0);

    @Mock ProvisioningProgressRepository progressRepository;
    @Mock OsInstallOpenRows openRows;
    @Mock OsInstallingExecutor executor;
    @Mock ApplicationEventPublisher eventPublisher;

    private final GuestServer guest = GuestServer.builder().id(UUID.randomUUID()).systemUUID(UUID.randomUUID()).build();

    private OsInstallTimeoutSweeper sweeper() {
        return new OsInstallTimeoutSweeper(progressRepository, openRows, executor, eventPublisher);
    }

    private ProvisioningProgress running() {
        ProvisioningProgress p = ProvisioningProgress.builder().id(UUID.randomUUID()).guestServer(guest)
                .currentStep(ProvisioningPhaseStep.OS_INSTALLING).lastTransitionAt(SERVED).build();
        p.start(SERVED);
        p.positionAt(ProvisioningPhaseStep.OS_INSTALLING, SERVED);   // STEP_RUNNING
        return p;
    }

    private ProvisioningHistory openRow() {
        return ProvisioningHistory.openRunning(guest, ProvisioningPhaseStep.OS_INSTALLING, SERVED, "{\"origin\":\"windows-install\"}");
    }

    @Test
    @DisplayName("설치기가 실패로 닫으면 1 을 세고 화면 갱신 이벤트를 낸다")
    void installerFails_countsAndPublishes() {
        ProvisioningProgress p = running();
        ProvisioningHistory row = openRow();
        given(progressRepository.findAllByCurrentStepInAndFailedAtIsNullAndCompletedAtIsNull(Set.of(ProvisioningPhaseStep.OS_INSTALLING)))
                .willReturn(List.of(p));
        given(openRows.latestOpen(guest.getId())).willReturn(Optional.of(row));
        given(executor.sweep(eq(guest), eq(p), eq(row), any())).willReturn(true);

        assertThat(sweeper().sweep()).isEqualTo(1);
        verify(eventPublisher).publishEvent(new GuestServerChangedEvent(guest.getId()));
    }

    @Test
    @DisplayName("설치기가 아직 시한 전이라 두면 0 · 이벤트 없음")
    void installerKeeps_noop() {
        ProvisioningProgress p = running();
        ProvisioningHistory row = openRow();
        given(progressRepository.findAllByCurrentStepInAndFailedAtIsNullAndCompletedAtIsNull(any())).willReturn(List.of(p));
        given(openRows.latestOpen(guest.getId())).willReturn(Optional.of(row));
        given(executor.sweep(eq(guest), eq(p), eq(row), any())).willReturn(false);

        assertThat(sweeper().sweep()).isZero();
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    @DisplayName("서빙 전(AWAITING_BOOT) 게스트는 시한이 흐르지 않는다 — 열린 행을 묻지도 않는다")
    void awaitingBoot_skipped() {
        ProvisioningProgress p = ProvisioningProgress.builder().id(UUID.randomUUID()).guestServer(guest)
                .currentStep(ProvisioningPhaseStep.OS_INSTALLING).lastTransitionAt(SERVED).build();
        p.start(SERVED);   // motion = AWAITING_BOOT
        given(progressRepository.findAllByCurrentStepInAndFailedAtIsNullAndCompletedAtIsNull(any())).willReturn(List.of(p));

        assertThat(sweeper().sweep()).isZero();
        verify(openRows, never()).latestOpen(any());
    }

    @Test
    @DisplayName("STEP_RUNNING 인데 열린 행이 없으면 건너뛴다 — 스윕은 원장 사실 위에서만 판정한다")
    void noOpenRow_skipped() {
        ProvisioningProgress p = running();
        given(progressRepository.findAllByCurrentStepInAndFailedAtIsNullAndCompletedAtIsNull(any())).willReturn(List.of(p));
        given(openRows.latestOpen(guest.getId())).willReturn(Optional.empty());

        assertThat(sweeper().sweep()).isZero();
        verify(executor, never()).sweep(any(), any(), any(), any());
    }
}
