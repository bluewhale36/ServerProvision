package com.example.serverprovision.execution.engine.rhel.ossetting;

import com.example.serverprovision.execution.engine.phase.PhaseCursorAdvancer;
import com.example.serverprovision.execution.engine.rhel.RhelInstallationResolutionProvider;
import com.example.serverprovision.execution.entity.GuestServer;
import com.example.serverprovision.execution.entity.ProvisioningProgress;
import com.example.serverprovision.execution.enums.ProvisioningPhaseStep;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/** E4-1-b-7 D-5 — 커서가 OS 후처리에 섰을 때만 판정하고, 성공이면 전진 · 실패면 실패 신호. */
@ExtendWith(MockitoExtension.class)
class RhelOsSettingCloserTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 21, 18, 0);
    private static final RhelOsSettingPlan PLAN = new RhelOsSettingPlan(SelinuxMode.ENFORCING, List.of(), List.of(),
            List.of(new UserScript("a.sh", "true")));

    @Mock RhelInstallationResolutionProvider resolutionProvider;
    @Mock OsSettingLedger ledger;
    @Mock PhaseCursorAdvancer cursorAdvancer;

    private final GuestServer server = GuestServer.builder().id(UUID.randomUUID()).build();
    private RhelOsSettingCloser closer;

    @BeforeEach
    void setUp() {
        closer = new RhelOsSettingCloser(resolutionProvider, ledger, cursorAdvancer);
    }

    private ProvisioningProgress at(ProvisioningPhaseStep step) {
        ProvisioningProgress p = ProvisioningProgress.builder().id(UUID.randomUUID()).guestServer(server)
                .currentStep(step).lastTransitionAt(NOW).build();
        p.start(NOW);
        p.positionAt(step, NOW);
        return p;
    }

    private static Optional<OsSettingObservation> exitCodes(Integer code) {
        return Optional.of(new OsSettingObservation("enforcing", Map.of(), Map.of(), java.util.Arrays.asList(code)));
    }

    @Test
    @DisplayName("성공 판정 → SUCCEEDED 기록 후 커서 전진")
    void passed_advances() {
        ProvisioningProgress progress = at(ProvisioningPhaseStep.OS_SETTING);
        given(resolutionProvider.osSettingFor(server.getId())).willReturn(Optional.of(PLAN));

        closer.closeIfReached(server, progress, exitCodes(0), NOW);

        ArgumentCaptor<OsSettingVerdict> verdict = ArgumentCaptor.forClass(OsSettingVerdict.class);
        verify(ledger).record(eq(server), verdict.capture(), eq(NOW));
        assertThat(verdict.getValue().passed()).isTrue();
        verify(cursorAdvancer).advanceOrComplete(progress, server.getId(), NOW);
        assertThat(progress.isFailed()).isFalse();
    }

    @Test
    @DisplayName("실패 판정 → FAILED 기록, 커서는 OS 후처리에 둔 채 실패 신호")
    void failed_marksFailed() {
        ProvisioningProgress progress = at(ProvisioningPhaseStep.OS_SETTING);
        given(resolutionProvider.osSettingFor(server.getId())).willReturn(Optional.of(PLAN));

        closer.closeIfReached(server, progress, exitCodes(2), NOW);

        verify(ledger).record(eq(server), any(), eq(NOW));
        verify(cursorAdvancer, never()).advanceOrComplete(any(), any(), any());
        assertThat(progress.isFailed()).isTrue();
        assertThat(progress.getCurrentStep()).isEqualTo(ProvisioningPhaseStep.OS_SETTING);
    }

    @Test
    @DisplayName("커서가 OS 후처리가 아니면(정의서에 그 단계 없음) 아무것도 하지 않는다")
    void notReached_noop() {
        closer.closeIfReached(server, at(ProvisioningPhaseStep.OS_INSTALLING), exitCodes(0), NOW);

        verifyNoInteractions(resolutionProvider, ledger, cursorAdvancer);
    }

    @Test
    @DisplayName("커서가 OS 후처리인데 목표가 없으면 데이터 손상(500)")
    void reachedWithoutPlan_throws() {
        ProvisioningProgress progress = at(ProvisioningPhaseStep.OS_SETTING);
        given(resolutionProvider.osSettingFor(server.getId())).willReturn(Optional.empty());

        assertThatThrownBy(() -> closer.closeIfReached(server, progress, exitCodes(0), NOW))
                .isInstanceOf(IllegalStateException.class);
    }
}
