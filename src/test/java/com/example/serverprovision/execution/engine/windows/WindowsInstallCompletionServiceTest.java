package com.example.serverprovision.execution.engine.windows;

import com.example.serverprovision.execution.dto.request.WindowsInstallCompletionRequest;
import com.example.serverprovision.execution.dto.response.WindowsInstallCompletionResponse;
import com.example.serverprovision.execution.engine.ProvisioningHistoryRecorder;
import com.example.serverprovision.execution.engine.phase.PhaseCursorAdvancer;
import com.example.serverprovision.execution.entity.GuestServer;
import com.example.serverprovision.execution.entity.ProvisioningHistory;
import com.example.serverprovision.execution.entity.ProvisioningProgress;
import com.example.serverprovision.execution.enums.ProvisioningPhase;
import com.example.serverprovision.execution.enums.ProvisioningPhaseStep;
import com.example.serverprovision.execution.enums.ProvisioningStatus;
import com.example.serverprovision.execution.event.GuestServerChangedEvent;
import com.example.serverprovision.execution.exception.AgentReportRejectedException;
import com.example.serverprovision.execution.exception.GuestServerNotFoundException;
import com.example.serverprovision.execution.repository.ProvisioningHistoryRepository;
import com.example.serverprovision.execution.repository.ProvisioningProgressRepository;
import com.example.serverprovision.execution.service.GuestTokenAuthenticator;
import com.example.serverprovision.execution.wininstall.vo.WindowsImageName;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willAnswer;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * E4-1-a-4 CP4 — 완료 보고의 소비(D-4 · D-8). 원장은 실물(meta 왕복이 요점 — 서빙 meta 보존 + 완료 meta), 인증 · 저장소 · 전진기는 mock.
 * 게이트 순서: 인증 404 → 중복 no-op → 미진행 409 → phase 불일치 409 → 열린 행 없음 409 → 닫기 · 회수 · 전진/종단 · 이벤트.
 */
@ExtendWith(MockitoExtension.class)
class WindowsInstallCompletionServiceTest {

    private static final String TOKEN = "a3f9d2c8b41e4f7a9c0d5e6f7a8b9c1d";
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 9, 3, 11, 0);
    private static final WindowsImageName IMAGE = new WindowsImageName("Windows Server 2025 SERVERSTANDARD");

    @Mock GuestTokenAuthenticator authenticator;
    @Mock ProvisioningProgressRepository progressRepository;
    @Mock ProvisioningHistoryRepository historyRepository;
    @Mock ProvisioningHistoryRecorder recorder;
    @Mock WindowsInstallTokenRegistry tokenRegistry;
    @Mock PhaseCursorAdvancer cursorAdvancer;
    @Mock ApplicationEventPublisher eventPublisher;
    @Mock com.example.serverprovision.execution.repository.RaidVolumeRepository raidVolumeRepository;

    private final GuestServer guest = GuestServer.builder().id(UUID.randomUUID()).systemUUID(UUID.randomUUID()).build();
    private WindowsInstallLedger ledger;
    private WindowsInstallCompletionService service;

    @BeforeEach
    void setUp() {
        lenient().when(recorder.openRunning(any(), any(), any(), any())).thenAnswer(inv -> ProvisioningHistory.openRunning(
                inv.getArgument(0), inv.getArgument(1), inv.getArgument(2), inv.getArgument(3)));
        ledger = new WindowsInstallLedger(recorder, historyRepository, new ObjectMapper());
        service = new WindowsInstallCompletionService(authenticator, progressRepository, ledger, tokenRegistry, cursorAdvancer, eventPublisher, raidVolumeRepository);
        lenient().when(authenticator.requireByToken(TOKEN)).thenReturn(guest);
    }

    private static WindowsInstallCompletionRequest report(int problems) {
        return new WindowsInstallCompletionRequest("SPV-14174000", "Microsoft Windows Server 2025 Standard 10.0.26100", 47,
                problems, problems == 0 ? List.of() : List.of("Unknown device (ACPI\\INT34C6)", "PCI Simple Communications Controller"),
                "[mock] Added driver packages:  47", null, null);
    }

    private ProvisioningProgress installing() {
        ProvisioningProgress p = ProvisioningProgress.builder().id(UUID.randomUUID()).guestServer(guest)
                .currentStep(ProvisioningPhaseStep.OS_INSTALLING).lastTransitionAt(NOW).build();
        p.start(NOW);
        p.positionAt(ProvisioningPhaseStep.OS_INSTALLING, NOW);
        given(progressRepository.findByGuestServer_Id(guest.getId())).willReturn(Optional.of(p));
        return p;
    }

    private ProvisioningHistory openRow() {
        return openRow("600605b0aa11");
    }

    /** 서빙 meta 의 확증 기준(expectedUniqueId)을 정해 여는 행 — null 이면 구 서빙 행(기준 없음)을 재연한다. */
    private ProvisioningHistory openRow(String expectedUniqueId) {
        ProvisioningHistory row = ledger.openServed(guest, IMAGE, com.example.serverprovision.execution.engine.windows.WindowsDiskSelection.DiskSelection.confident(0, expectedUniqueId, 480103981056L, com.example.serverprovision.execution.engine.windows.WindowsDiskSelection.Basis.INVENTORY_ORDER), NOW.minusMinutes(20));
        given(historyRepository.findFirstByGuestServer_IdAndStepCodeAndStatusOrderByCreatedAtDesc(
                guest.getId(), ProvisioningPhaseStep.OS_INSTALLING, ProvisioningStatus.RUNNING)).willReturn(Optional.of(row));
        return row;
    }

    // ==== 성공 ====================================================

    @Test
    @DisplayName("열린 서빙 행 → SUCCEEDED(서빙 meta 보존 + 완료 meta) · 토큰 회수 · advanceOrComplete · 이벤트 · 종단이면 provisioningCompleted")
    void complete_closesRevokesAdvances() {
        ProvisioningProgress p = installing();
        ProvisioningHistory row = openRow();
        willAnswer(inv -> { p.markCompleted(inv.getArgument(2)); return null; })
                .given(cursorAdvancer).advanceOrComplete(eq(p), eq(guest.getId()), any());

        WindowsInstallCompletionResponse res = service.complete(TOKEN, report(2));

        assertThat(res.closed()).isTrue();
        assertThat(res.provisioningCompleted()).isTrue();
        assertThat(res.nextPhase()).isNull();
        assertThat(row.getStatus()).isEqualTo(ProvisioningStatus.SUCCEEDED);
        assertThat(ledger.servedAtOf(row)).isEqualTo(NOW.minusMinutes(20));      // 서빙 meta 보존
        assertThat(ledger.imageOf(row)).isEqualTo(IMAGE.value());
        assertThat(ledger.isCompletedRow(row)).isTrue();
        assertThat(ledger.computerNameOf(row)).isEqualTo("SPV-14174000");
        assertThat(ledger.driversAddedOf(row)).isEqualTo(47);
        assertThat(ledger.problemDeviceCountOf(row)).isEqualTo(2);
        assertThat(ledger.problemDevicesOf(row)).hasSize(2).first().asString().contains("ACPI");
        assertThat(row.displayNote()).isEqualTo("설치 완료 · 드라이버 47 · 문제 장치 2");
        assertThat(row.getStatusMeta()).doesNotContain(TOKEN);
        verify(tokenRegistry).revoke(guest.getId());
        verify(eventPublisher).publishEvent(new GuestServerChangedEvent(guest.getId()));
    }

    @Test
    @DisplayName("다음 소유 phase 가 있으면 nextPhase 에 그 phase — 종단 아님")
    void complete_advancesToNextPhase() {
        ProvisioningProgress p = installing();
        openRow();
        willAnswer(inv -> { p.advanceToEntry(ProvisioningPhaseStep.entryOf(ProvisioningPhase.TESTING), inv.getArgument(2)); return null; })
                .given(cursorAdvancer).advanceOrComplete(eq(p), eq(guest.getId()), any());

        WindowsInstallCompletionResponse res = service.complete(TOKEN, report(0));

        assertThat(res.closed()).isTrue();
        assertThat(res.provisioningCompleted()).isFalse();
        assertThat(res.nextPhase()).isEqualTo(ProvisioningPhase.TESTING);
    }

    // ==== E4-1-a-6 설치 디스크 사후 확증 ====================================

    private WindowsInstallCompletionRequest reportWithDisk(String installedDiskUniqueId) {
        return new WindowsInstallCompletionRequest("SPV-14174000", "Windows Server 2025 10.0.26100", 47, 0, List.of(),
                "tail", installedDiskUniqueId, null);
    }

    private void osVolumeWwn(String wwn) {
        com.example.serverprovision.execution.entity.RaidVolume os =
                com.example.serverprovision.execution.entity.RaidVolume.of(null, "spvR1V1",
                        com.example.serverprovision.management.raidcard.enums.RaidLevel.RAID1, "[]", 480_103_981_056L,
                        com.example.serverprovision.execution.engine.raid.PlannedVolumeRole.OS, 1, "Optl", wwn);
        given(raidVolumeRepository.findFirstByGuestServer_IdAndVolumeRole(guest.getId(),
                com.example.serverprovision.execution.engine.raid.PlannedVolumeRole.OS)).willReturn(Optional.of(os));
    }

    @Test
    @DisplayName("확증 O — 보고된 UniqueId 가 OS 볼륨 WWN 과 일치(대소문자 무시) → diskConfirmed true · CONFIRMED")
    void diskConfirmed_true() {
        installing();
        ProvisioningHistory row = openRow();   // 서빙 meta 의 기준 = 600605b0aa11 — raid_volume 은 보지 않는다(HF15-5)

        service.complete(TOKEN, reportWithDisk("600605B0AA11"));

        assertThat(ledger.diskConfirmationOf(row)).isEqualTo("CONFIRMED");
        assertThat(row.getStatus()).isEqualTo(ProvisioningStatus.SUCCEEDED);
    }

    @Test
    @DisplayName("어긋남 — UniqueId 가 OS 볼륨 WWN 과 다르다 → diskConfirmed false · MISMATCH · 그래도 완료(실패 아님)")
    void diskConfirmed_mismatch() {
        installing();
        ProvisioningHistory row = openRow();

        WindowsInstallCompletionResponse res = service.complete(TOKEN, reportWithDisk("600605b0dddd"));

        assertThat(res.closed()).isTrue();                       // 사후라 되돌릴 수 없다 — 완료는 완료
        assertThat(ledger.diskConfirmationOf(row)).isEqualTo("MISMATCH");
        assertThat(row.getStatus()).isEqualTo(ProvisioningStatus.SUCCEEDED);
    }

    @Test
    @DisplayName("구 서빙 행(기준 없음)은 raid_volume 의 OS 볼륨 WWN 으로 폴백해 대조한다(HF15-5 호환)")
    void diskConfirmed_fallbackToRaidVolume_whenNoExpectedInMeta() {
        installing();
        ProvisioningHistory row = openRow(null);
        osVolumeWwn("600605b0aa11");

        service.complete(TOKEN, reportWithDisk("600605B0AA11"));

        assertThat(ledger.diskConfirmationOf(row)).isEqualTo("CONFIRMED");
    }

    @Test
    @DisplayName("IR 볼륨 — 서빙 meta 의 기준이 NAA 변환값이면 Windows UniqueId(대문자)와 그대로 맞는다(F-7 · 일산 상3 실측값)")
    void diskConfirmed_irNaaFromMeta() {
        installing();
        ProvisioningHistory row = openRow("600508e0000000002e2ff7379820a800");

        service.complete(TOKEN, reportWithDisk("600508E0000000002E2FF7379820A800"));

        assertThat(ledger.diskConfirmationOf(row)).isEqualTo("CONFIRMED");
        verify(raidVolumeRepository, never()).findFirstByGuestServer_IdAndVolumeRole(any(), any());
    }

    @Test
    @DisplayName("미보고 — UniqueId 가 없으면(구 스크립트) diskConfirmed null · UNREPORTED · OS 볼륨은 조회하지 않는다")
    void diskConfirmed_unreported() {
        installing();
        ProvisioningHistory row = openRow();

        service.complete(TOKEN, reportWithDisk(null));

        assertThat(ledger.diskConfirmationOf(row)).isEqualTo("UNREPORTED");
        verify(raidVolumeRepository, never()).findFirstByGuestServer_IdAndVolumeRole(any(), any());
    }

    @Test
    @DisplayName("중복 보고(열린 행 없음 + 최신 행이 완료 행) → closed:false 200 · 원장 · 토큰 · 커서 무변경(멱등)")
    void duplicate_noop() {
        ProvisioningProgress p = installing();
        p.markCompleted(NOW);
        ProvisioningHistory done = ledger.openServed(guest, IMAGE, com.example.serverprovision.execution.engine.windows.WindowsDiskSelection.DiskSelection.confident(0, "600605b0aa11", 480103981056L, com.example.serverprovision.execution.engine.windows.WindowsDiskSelection.Basis.INVENTORY_ORDER), NOW.minusMinutes(20));
        ledger.closeSucceeded(done, new WindowsInstallLedger.Completion("SPV-1", null, 1, 0, List.of(), null, null, null), NOW);
        given(historyRepository.findFirstByGuestServer_IdAndStepCodeAndStatusOrderByCreatedAtDesc(
                guest.getId(), ProvisioningPhaseStep.OS_INSTALLING, ProvisioningStatus.RUNNING)).willReturn(Optional.empty());
        given(historyRepository.findFirstByGuestServer_IdAndStepCodeOrderByCreatedAtDesc(guest.getId(), ProvisioningPhaseStep.OS_INSTALLING))
                .willReturn(Optional.of(done));

        WindowsInstallCompletionResponse res = service.complete(TOKEN, report(0));

        assertThat(res.closed()).isFalse();
        assertThat(res.provisioningCompleted()).isTrue();
        verify(tokenRegistry, never()).revoke(any());
        verify(cursorAdvancer, never()).advanceOrComplete(any(), any(), any());
        verify(eventPublisher).publishEvent(new GuestServerChangedEvent(guest.getId()));   // 접촉은 갱신
    }

    // ==== 거절 ====================================================

    @Test
    @DisplayName("토큰 불일치 → 인증기의 404 가 그대로 — 진행 · 원장을 읽지 않는다")
    void badToken_404() {
        given(authenticator.requireByToken("deadbeef")).willThrow(GuestServerNotFoundException.byToken());

        assertThatThrownBy(() -> service.complete("deadbeef", report(0))).isInstanceOf(GuestServerNotFoundException.class);
        verify(progressRepository, never()).findByGuestServer_Id(any());
    }

    @Test
    @DisplayName("실패 상태 게스트(스윕이 먼저 닫음) 의 지연 보고 → 409 notProvisioning")
    void failedGuest_409() {
        ProvisioningProgress p = installing();
        p.markFailed(NOW);
        given(historyRepository.findFirstByGuestServer_IdAndStepCodeAndStatusOrderByCreatedAtDesc(any(), any(), any())).willReturn(Optional.empty());
        given(historyRepository.findFirstByGuestServer_IdAndStepCodeOrderByCreatedAtDesc(any(), any())).willReturn(Optional.empty());

        assertThatThrownBy(() -> service.complete(TOKEN, report(0)))
                .isInstanceOf(AgentReportRejectedException.class).hasMessageContaining("프로비저닝 중이 아닌");
        verify(tokenRegistry, never()).revoke(any());
    }

    @Test
    @DisplayName("커서가 다른 phase(RAID 구성) → 409 phaseMismatch")
    void otherPhase_409() {
        ProvisioningProgress p = ProvisioningProgress.builder().id(UUID.randomUUID()).guestServer(guest)
                .currentStep(ProvisioningPhaseStep.entryOf(ProvisioningPhase.RAID_CONFIGURATION)).lastTransitionAt(NOW).build();
        p.start(NOW);
        given(progressRepository.findByGuestServer_Id(guest.getId())).willReturn(Optional.of(p));
        given(historyRepository.findFirstByGuestServer_IdAndStepCodeAndStatusOrderByCreatedAtDesc(any(), any(), any())).willReturn(Optional.empty());
        given(historyRepository.findFirstByGuestServer_IdAndStepCodeOrderByCreatedAtDesc(any(), any())).willReturn(Optional.empty());

        assertThatThrownBy(() -> service.complete(TOKEN, report(0)))
                .isInstanceOf(AgentReportRejectedException.class).hasMessageContaining("phase 밖");
    }

    @Test
    @DisplayName("서빙 전(열린 행 없음 · 완료 행도 없음) → 409 noOpenStep")
    void beforeServing_409() {
        installing();
        given(historyRepository.findFirstByGuestServer_IdAndStepCodeAndStatusOrderByCreatedAtDesc(any(), any(), any())).willReturn(Optional.empty());
        given(historyRepository.findFirstByGuestServer_IdAndStepCodeOrderByCreatedAtDesc(any(), any())).willReturn(Optional.empty());

        assertThatThrownBy(() -> service.complete(TOKEN, report(0)))
                .isInstanceOf(AgentReportRejectedException.class).hasMessageContaining("열린 OS_INSTALLING 행이 없어");
        verify(cursorAdvancer, never()).advanceOrComplete(any(), any(), any());
    }
}
