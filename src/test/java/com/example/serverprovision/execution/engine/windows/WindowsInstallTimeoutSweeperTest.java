package com.example.serverprovision.execution.engine.windows;

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

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * E4-1-a-4 CP4 — 시한 스윕(D-7): 재PXE 가 없는 설치 중 게스트를 서빙 + 시한 + 유예 뒤에 INSTALL_TIMEOUT 으로 닫고 토큰을 회수한다.
 * 유예 전 · 서빙 전(motion 이 STEP_RUNNING 이 아님) · 열린 행 없음은 건드리지 않는다.
 */
@ExtendWith(MockitoExtension.class)
class WindowsInstallTimeoutSweeperTest {

    private static final LocalDateTime SERVED = LocalDateTime.of(2026, 9, 3, 10, 0);

    @Mock ProvisioningProgressRepository progressRepository;
    @Mock WindowsInstallLedger ledger;
    @Mock WindowsInstallTokenRegistry tokenRegistry;
    @Mock ApplicationEventPublisher eventPublisher;

    private final WindowsInstallTimeoutPolicy policy = new WindowsInstallTimeoutPolicy(Duration.ofMinutes(60), 5, Duration.ofMinutes(30));
    private final GuestServer guest = GuestServer.builder().id(UUID.randomUUID()).systemUUID(UUID.randomUUID()).build();

    private WindowsInstallTimeoutSweeper sweeper() {
        return new WindowsInstallTimeoutSweeper(progressRepository, ledger, policy, tokenRegistry, eventPublisher);
    }

    private ProvisioningProgress running(LocalDateTime at) {
        ProvisioningProgress p = ProvisioningProgress.builder().id(UUID.randomUUID()).guestServer(guest)
                .currentStep(ProvisioningPhaseStep.OS_INSTALLING).lastTransitionAt(at).build();
        p.start(at);
        p.positionAt(ProvisioningPhaseStep.OS_INSTALLING, at);   // STEP_RUNNING
        return p;
    }

    private ProvisioningHistory openRow() {
        return ProvisioningHistory.openRunning(guest, ProvisioningPhaseStep.OS_INSTALLING, SERVED, "{\"origin\":\"windows-install\"}");
    }

    @Test
    @DisplayName("서빙 + 60분 + 30분이 지난 설치 중 게스트 → failRunning(INSTALL_TIMEOUT · 스윕 사유) + 토큰 회수 + 이벤트")
    void due_failsAndRevokes() {
        ProvisioningProgress p = running(SERVED);
        ProvisioningHistory row = openRow();
        given(progressRepository.findAllByCurrentStepInAndFailedAtIsNullAndCompletedAtIsNull(Set.of(ProvisioningPhaseStep.OS_INSTALLING)))
                .willReturn(List.of(p));
        given(ledger.latestRunning(guest.getId())).willReturn(Optional.of(row));
        given(ledger.servedAtOf(row)).willReturn(LocalDateTime.now().minusMinutes(91));

        int failed = sweeper().sweep();

        assertThat(failed).isEqualTo(1);
        verify(ledger).failRunning(eq(guest), eq(p), eq(row), eq(WindowsInstallLedger.INSTALL_TIMEOUT),
                org.mockito.ArgumentMatchers.contains("스윕"), any());
        verify(tokenRegistry).revoke(guest.getId());
        verify(eventPublisher).publishEvent(new GuestServerChangedEvent(guest.getId()));
    }

    @Test
    @DisplayName("유예 전(서빙 후 89분) — 아무것도 하지 않는다(실행기의 시한 판정과 겹치지 않게 유예를 둔다)")
    void withinGrace_noop() {
        ProvisioningProgress p = running(SERVED);
        ProvisioningHistory row = openRow();
        given(progressRepository.findAllByCurrentStepInAndFailedAtIsNullAndCompletedAtIsNull(any())).willReturn(List.of(p));
        given(ledger.latestRunning(guest.getId())).willReturn(Optional.of(row));
        given(ledger.servedAtOf(row)).willReturn(LocalDateTime.now().minusMinutes(89));

        assertThat(sweeper().sweep()).isZero();
        verify(ledger, never()).failRunning(any(), any(), any(), anyString(), anyString(), any());
        verify(tokenRegistry, never()).revoke(any());
    }

    @Test
    @DisplayName("서빙 전(AWAITING_BOOT) 게스트는 시한이 흐르지 않는다 — 원장을 묻지도 않는다")
    void awaitingBoot_skipped() {
        ProvisioningProgress p = ProvisioningProgress.builder().id(UUID.randomUUID()).guestServer(guest)
                .currentStep(ProvisioningPhaseStep.OS_INSTALLING).lastTransitionAt(SERVED).build();
        p.start(SERVED);   // motion = AWAITING_BOOT
        given(progressRepository.findAllByCurrentStepInAndFailedAtIsNullAndCompletedAtIsNull(any())).willReturn(List.of(p));

        assertThat(sweeper().sweep()).isZero();
        verify(ledger, never()).latestRunning(any());
    }

    @Test
    @DisplayName("STEP_RUNNING 인데 열린 행이 없으면(정정 전 데이터) 건너뛴다 — 스윕은 원장 사실 위에서만 판정한다")
    void noOpenRow_skipped() {
        ProvisioningProgress p = running(SERVED);
        given(progressRepository.findAllByCurrentStepInAndFailedAtIsNullAndCompletedAtIsNull(any())).willReturn(List.of(p));
        given(ledger.latestRunning(guest.getId())).willReturn(Optional.empty());

        assertThat(sweeper().sweep()).isZero();
        verify(tokenRegistry, never()).revoke(any());
    }
}
