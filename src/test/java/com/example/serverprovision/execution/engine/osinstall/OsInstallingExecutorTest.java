package com.example.serverprovision.execution.engine.osinstall;

import com.example.serverprovision.execution.engine.phase.PhaseReadiness;
import com.example.serverprovision.execution.engine.phase.ReadinessGrade;
import com.example.serverprovision.execution.entity.GuestServer;
import com.example.serverprovision.execution.entity.ProvisioningHistory;
import com.example.serverprovision.execution.entity.ProvisioningProgress;
import com.example.serverprovision.execution.enums.ProvisioningPhase;
import com.example.serverprovision.execution.enums.ProvisioningPhaseStep;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/** E4-1-b-4-1 — OS 설치 phase 실행기: 계열 설치기 선택(착수 뒤 = 열린 행 주인, 그 밖 = 정의서를 맡는 첫 설치기)과 미지원 · 창 밖. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class OsInstallingExecutorTest {

    private static final UUID GUEST_ID = UUID.randomUUID();
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 18, 12, 0);

    @Mock OsFamilyInstaller windows;
    @Mock OsFamilyInstaller rhel;
    @Mock OsFamilyNameProvider familyNames;
    @Mock OsInstallOpenRows openRows;

    private final GuestServer guest = GuestServer.builder().id(GUEST_ID).systemUUID(UUID.randomUUID()).build();
    private OsInstallingExecutor executor;

    @BeforeEach
    void setUp() {
        executor = new OsInstallingExecutor(List.of(windows, rhel), familyNames, openRows);
    }

    private static ProvisioningProgress awaitingBoot() {
        ProvisioningProgress p = ProvisioningProgress.builder().id(UUID.randomUUID())
                .currentStep(ProvisioningPhaseStep.OS_INSTALLING).lastTransitionAt(NOW).build();
        p.start(NOW);
        return p;
    }

    private static ProvisioningProgress running() {
        ProvisioningProgress p = awaitingBoot();
        p.positionAt(ProvisioningPhaseStep.OS_INSTALLING, NOW);
        return p;
    }

    private ProvisioningHistory openRow() {
        return ProvisioningHistory.openRunning(guest, ProvisioningPhaseStep.OS_INSTALLING, NOW, "{\"origin\":\"windows-install\"}");
    }

    @Test
    @DisplayName("phase 는 OS_INSTALLING")
    void phase() {
        assertThat(executor.phase()).isEqualTo(ProvisioningPhase.OS_INSTALLING);
    }

    @Test
    @DisplayName("착수 전 — 정의서를 맡는 첫 설치기에 준비도 · 스크립트를 위임한다")
    void beforeServe_delegatesToClaimer() {
        given(windows.claims(GUEST_ID)).willReturn(false);
        given(rhel.claims(GUEST_ID)).willReturn(true);
        given(rhel.readiness(any(), any())).willReturn(PhaseReadiness.ready());
        given(rhel.bootScript(any(), any(), any())).willReturn("#!ipxe rhel");

        ProvisioningProgress p = awaitingBoot();
        assertThat(executor.readiness(guest, p).grade()).isEqualTo(ReadinessGrade.READY);
        assertThat(executor.bootScript(guest, p, "q")).isEqualTo("#!ipxe rhel");
        verify(windows, never()).bootScript(any(), any(), any());
    }

    @Test
    @DisplayName("① 착수 뒤 — 열린 행 주인과 정의서를 맡는 설치기가 달라도 주인이 끝낸다(정의서 교체가 진행 중 설치를 가로채지 않게)")
    void afterServe_ownerWinsOverClaimer() {
        ProvisioningHistory row = openRow();
        given(openRows.latestOpen(GUEST_ID)).willReturn(Optional.of(row));
        given(windows.owns(row)).willReturn(true);
        given(rhel.claims(GUEST_ID)).willReturn(true);
        given(windows.bootScript(any(), any(), any())).willReturn("#!ipxe exit");

        ProvisioningProgress p = running();
        assertThat(executor.bootScript(guest, p, "q")).isEqualTo("#!ipxe exit");
        executor.onBootScriptServed(guest, p, NOW);
        verify(windows).onBootScriptServed(guest, p, NOW);
        verify(rhel, never()).bootScript(any(), any(), any());
        verify(rhel, never()).onBootScriptServed(any(), any(), any());
    }

    @Test
    @DisplayName("② 맡는 설치기 없음 + 계열 이름 있음 → BLOCKED(다음 행동 안내) · 대기 스크립트 wire os family not supported")
    void unclaimed_withFamily_blocked() {
        given(familyNames.familyLabelOf(GUEST_ID)).willReturn(Optional.of("Debian 계열"));

        PhaseReadiness r = executor.readiness(guest, awaitingBoot());
        assertThat(r.isBlocked()).isTrue();
        assertThat(r.wire()).isEqualTo(OsInstallingExecutor.UNSUPPORTED_WIRE);
        assertThat(r.notes()).singleElement().asString()
                .isEqualTo("Debian 계열 설치는 지원하지 않습니다. 정의서의 OS 설치 단계를 지원하는 OS 로 바꿔야 합니다.");
        assertThat(executor.bootScript(guest, awaitingBoot(), "q")).contains(OsInstallingExecutor.UNSUPPORTED_WIRE);
    }

    @Test
    @DisplayName("③ 맡는 설치기 없음 + 계열 이름 없음(OS 설치 단계 없음) → 창 밖 ready · 스크립트는 대상 없음 대기")
    void unclaimed_noFamily_outsideWindow() {
        given(familyNames.familyLabelOf(GUEST_ID)).willReturn(Optional.empty());

        assertThat(executor.readiness(guest, awaitingBoot()).grade()).isEqualTo(ReadinessGrade.READY);
        assertThat(executor.bootScript(guest, awaitingBoot(), "q")).contains(OsInstallingExecutor.TARGET_MISSING_WIRE);
    }

    @Test
    @DisplayName("onOperatorFailed — 열린 행이 있으면 그 주인, 없으면 ④ 정의서를 맡는 설치기에 뒷정리를 맡긴다")
    void operatorFailed_ownerThenClaimer() {
        ProvisioningHistory row = openRow();
        given(openRows.latestOpen(GUEST_ID)).willReturn(Optional.of(row));
        given(windows.owns(row)).willReturn(true);
        ProvisioningProgress p = running();
        executor.onOperatorFailed(guest, p, NOW);
        verify(windows).onOperatorFailed(guest, p, NOW);

        given(openRows.latestOpen(GUEST_ID)).willReturn(Optional.empty());
        given(rhel.claims(GUEST_ID)).willReturn(true);
        executor.onOperatorFailed(guest, p, NOW);
        verify(rhel).onOperatorFailed(guest, p, NOW);
    }

    @Test
    @DisplayName("sweep — 열린 행 주인에게 맡기고, ⑤ 주인 없는 행이면 false(경고만, 손대지 않는다)")
    void sweep_ownerOrFalse() {
        ProvisioningHistory row = openRow();
        ProvisioningProgress p = running();
        given(windows.owns(row)).willReturn(true);
        given(windows.sweep(guest, p, row, NOW)).willReturn(true);
        assertThat(executor.sweep(guest, p, row, NOW)).isTrue();

        ProvisioningHistory orphan = ProvisioningHistory.openRunning(guest, ProvisioningPhaseStep.OS_INSTALLING, NOW, "{\"origin\":\"unknown\"}");
        assertThat(executor.sweep(guest, p, orphan, NOW)).isFalse();
        verify(rhel, never()).sweep(any(), any(), any(), any());
    }

    @Test
    @DisplayName("E4-1-b-4-2 카드 계열 — 마지막 행의 주인이 있으면 그 설치기, 없으면 정의서를 맡는 설치기")
    void installerOf_ownerThenClaimer() {
        ProvisioningHistory row = openRow();
        given(rhel.owns(row)).willReturn(true);
        given(windows.claims(guest.getId())).willReturn(true);

        assertThat(executor.installerOf(guest.getId(), java.util.Optional.of(row))).contains(rhel);
        assertThat(executor.ownerOf(row)).contains(rhel);
        assertThat(executor.installerOf(guest.getId(), java.util.Optional.empty())).contains(windows);
    }
}
