package com.example.serverprovision.execution.engine.rhel;

import com.example.serverprovision.execution.dto.request.RhelInstallCompletionRequest;
import com.example.serverprovision.execution.dto.response.OsInstallCompletionResponse;
import com.example.serverprovision.execution.engine.ProvisioningHistoryRecorder;
import com.example.serverprovision.execution.engine.osinstall.InstallConfirmation;
import com.example.serverprovision.execution.engine.phase.PhaseCursorAdvancer;
import com.example.serverprovision.execution.entity.GuestServer;
import com.example.serverprovision.execution.entity.ProvisioningHistory;
import com.example.serverprovision.execution.entity.ProvisioningProgress;
import com.example.serverprovision.execution.enums.ProvisioningPhase;
import com.example.serverprovision.execution.enums.ProvisioningPhaseStep;
import com.example.serverprovision.execution.enums.ProvisioningStatus;
import com.example.serverprovision.execution.event.GuestServerChangedEvent;
import com.example.serverprovision.execution.exception.AgentReportRejectedException;
import com.example.serverprovision.execution.repository.ProvisioningHistoryRepository;
import com.example.serverprovision.execution.repository.ProvisioningProgressRepository;
import com.example.serverprovision.execution.service.GuestPrincipalLoader;
import com.example.serverprovision.global.security.springsecurity.guest.GuestPrincipal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;
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
 * RHEL 첫 부팅 완료 보고의 소비. 원장은 실물(서빙 meta 보존과 완료 meta 왕복이 요점)이고, 인증 · 저장소 · 전진기 · 설치기는 mock 이다.
 * 판정 순서는 Windows 와 같은 공통 흐름이라 거절 3 종과 중복 no-op 도 여기서 다시 확인한다.
 */
@ExtendWith(MockitoExtension.class)
class RhelInstallCompletionServiceTest {

    private static final GuestPrincipal GUEST = new GuestPrincipal(UUID.randomUUID(), UUID.randomUUID());
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 20, 16, 0);
    private static final UUID SYSTEM_UUID = UUID.fromString("4c4c4544-0042-3510-8051-b4c04f4e3332");
    private static final String TARGET_DISK = "wwn-0x600508e000000000";

    @Mock GuestPrincipalLoader guestPrincipalLoader;
    @Mock ProvisioningProgressRepository progressRepository;
    @Mock ProvisioningHistoryRepository historyRepository;
    @Mock ProvisioningHistoryRecorder recorder;
    @Mock PhaseCursorAdvancer cursorAdvancer;
    @Mock ApplicationEventPublisher eventPublisher;
    @Mock RhelInstaller installer;

    private final GuestServer guest = GuestServer.builder().id(UUID.randomUUID()).systemUUID(SYSTEM_UUID).build();
    private RhelInstallLedger ledger;
    private RhelInstallCompletionService service;

    @BeforeEach
    void setUp() {
        ledger = new RhelInstallLedger(recorder, historyRepository, new ObjectMapper());
        service = new RhelInstallCompletionService(guestPrincipalLoader, progressRepository, cursorAdvancer, eventPublisher,
                ledger, installer);
        lenient().when(guestPrincipalLoader.require(GUEST)).thenReturn(guest);
    }

    private static RhelInstallCompletionRequest report(String rootDisk, String systemUuid) {
        return new RhelInstallCompletionRequest("rocky", "9.5", "Rocky Linux 9.5 (Blue Onyx)",
                "5.14.0-503.14.1.el9_5.x86_64", "localhost", rootDisk, systemUuid);
    }

    private static RhelInstallCompletionRequest matching() {
        return report("WWN-0x600508E000000000", SYSTEM_UUID.toString().toUpperCase());
    }

    private ProvisioningProgress installing() {
        ProvisioningProgress p = ProvisioningProgress.builder().id(UUID.randomUUID()).guestServer(guest)
                .currentStep(ProvisioningPhaseStep.OS_INSTALLING).lastTransitionAt(NOW).build();
        p.start(NOW);
        p.positionAt(ProvisioningPhaseStep.OS_INSTALLING, NOW);
        given(progressRepository.findByGuestServer_Id(guest.getId())).willReturn(Optional.of(p));
        return p;
    }

    /** b-4-2 의 서빙 행과 같은 meta 로 연 RUNNING 행. */
    private ProvisioningHistory openRow() {
        String meta = "{\"origin\":\"rhel-install\",\"iso\":7,\"release\":\"Rocky Linux 9.5\",\"tree\":\"Rocky 9.5\","
                + "\"served\":\"" + NOW.minusMinutes(25) + "\",\"reentries\":1,\"targetDisk\":\"" + TARGET_DISK + "\"}";
        ProvisioningHistory row = ProvisioningHistory.openRunning(guest, ProvisioningPhaseStep.OS_INSTALLING, NOW.minusMinutes(25), meta);
        given(historyRepository.findFirstByGuestServer_IdAndStepCodeAndStatusOrderByCreatedAtDesc(
                guest.getId(), ProvisioningPhaseStep.OS_INSTALLING, ProvisioningStatus.RUNNING)).willReturn(Optional.of(row));
        return row;
    }

    private void noOpenRow() {
        given(historyRepository.findFirstByGuestServer_IdAndStepCodeAndStatusOrderByCreatedAtDesc(any(), any(), any()))
                .willReturn(Optional.empty());
        given(historyRepository.findFirstByGuestServer_IdAndStepCodeOrderByCreatedAtDesc(any(), any())).willReturn(Optional.empty());
    }

    @Test
    @DisplayName("열린 행을 SUCCEEDED 로 닫고(서빙 meta 보존 + 완료 meta) 서빙 뒷정리 · 종단 · 이벤트")
    void complete_closesReleasesTerminates() {
        ProvisioningProgress p = installing();
        ProvisioningHistory row = openRow();
        willAnswer(inv -> { p.markCompleted(inv.getArgument(2)); return null; })
                .given(cursorAdvancer).advanceOrComplete(eq(p), eq(guest.getId()), any());

        OsInstallCompletionResponse res = service.complete(GUEST, matching());

        assertThat(res).isEqualTo(new OsInstallCompletionResponse(true, true, null));
        assertThat(row.getStatus()).isEqualTo(ProvisioningStatus.SUCCEEDED);
        assertThat(ledger.isCompletedRow(row)).isTrue();
        assertThat(ledger.isoOf(row)).contains(7L);                       // 서빙 meta 보존(트리 반납이 읽는다)
        assertThat(ledger.targetDiskOf(row)).isEqualTo(TARGET_DISK);
        assertThat(ledger.reentriesOf(row)).isEqualTo(1);
        RhelInstallLedger.RhelCompletion c = ledger.completionOf(row).orElseThrow();
        assertThat(c.osPrettyName()).isEqualTo("Rocky Linux 9.5 (Blue Onyx)");
        assertThat(c.kernel()).isEqualTo("5.14.0-503.14.1.el9_5.x86_64");
        assertThat(c.hostname()).isEqualTo("localhost");
        assertThat(c.diskConfirmation()).isEqualTo(InstallConfirmation.CONFIRMED);
        assertThat(c.identityConfirmation()).isEqualTo(InstallConfirmation.CONFIRMED);
        assertThat(row.displayNote()).isEqualTo("설치 완료 · 디스크 일치 · 대상 서버 일치");
        verify(installer).releaseAfterCompletion(guest, row);
        verify(eventPublisher).publishEvent(new GuestServerChangedEvent(guest.getId()));
    }

    @Test
    @DisplayName("다음 소유 phase 가 있으면 nextPhase 에 그 phase")
    void complete_advancesToNextPhase() {
        ProvisioningProgress p = installing();
        openRow();
        willAnswer(inv -> { p.advanceToEntry(ProvisioningPhaseStep.entryOf(ProvisioningPhase.OS_SETTING), inv.getArgument(2)); return null; })
                .given(cursorAdvancer).advanceOrComplete(eq(p), eq(guest.getId()), any());

        OsInstallCompletionResponse res = service.complete(GUEST, matching());

        assertThat(res).isEqualTo(new OsInstallCompletionResponse(true, false, ProvisioningPhase.OS_SETTING));
    }

    @Test
    @DisplayName("루트 디스크 · 시스템 UUID 가 다르면 불일치로 남기고 그래도 완료로 닫는다")
    void complete_mismatchIsRecordedNotFailed() {
        installing();
        ProvisioningHistory row = openRow();

        OsInstallCompletionResponse res = service.complete(GUEST, report("wwn-0x5000c500a1b2c3d4", UUID.randomUUID().toString()));

        assertThat(res.closed()).isTrue();
        assertThat(row.getStatus()).isEqualTo(ProvisioningStatus.SUCCEEDED);
        RhelInstallLedger.RhelCompletion c = ledger.completionOf(row).orElseThrow();
        assertThat(c.diskConfirmation()).isEqualTo(InstallConfirmation.MISMATCH);
        assertThat(c.identityConfirmation()).isEqualTo(InstallConfirmation.MISMATCH);
        assertThat(ledger.diskConfirmationOf(row)).isEqualTo(InstallConfirmation.MISMATCH);
    }

    @Test
    @DisplayName("루트 디스크 · 시스템 UUID 를 못 읽어 빈 값이면 미보고")
    void complete_unreported() {
        installing();
        ProvisioningHistory row = openRow();

        service.complete(GUEST, report("", null));

        RhelInstallLedger.RhelCompletion c = ledger.completionOf(row).orElseThrow();
        assertThat(c.diskConfirmation()).isEqualTo(InstallConfirmation.UNREPORTED);
        assertThat(c.identityConfirmation()).isEqualTo(InstallConfirmation.UNREPORTED);
        assertThat(row.displayNote()).isEqualTo("설치 완료 · 디스크 미보고 · 대상 서버 미보고");
    }

    @Test
    @DisplayName("중복 보고(열린 행 없음 + 최신 행이 완료 행)는 closed:false 200, 뒷정리 · 커서는 그대로")
    void duplicate_noop() {
        ProvisioningProgress p = installing();
        ProvisioningHistory done = openRow();
        service.complete(GUEST, matching());
        p.markCompleted(NOW);
        given(historyRepository.findFirstByGuestServer_IdAndStepCodeAndStatusOrderByCreatedAtDesc(
                guest.getId(), ProvisioningPhaseStep.OS_INSTALLING, ProvisioningStatus.RUNNING)).willReturn(Optional.empty());
        given(historyRepository.findFirstByGuestServer_IdAndStepCodeOrderByCreatedAtDesc(guest.getId(), ProvisioningPhaseStep.OS_INSTALLING))
                .willReturn(Optional.of(done));

        OsInstallCompletionResponse res = service.complete(GUEST, matching());

        assertThat(res).isEqualTo(new OsInstallCompletionResponse(false, true, null));
        verify(installer).releaseAfterCompletion(any(), any());            // 첫 보고 한 번뿐
        verify(cursorAdvancer).advanceOrComplete(any(), any(), any());
    }

    @Test
    @DisplayName("스윕이 먼저 실패로 닫은 뒤 늦게 온 보고는 409 notProvisioning")
    void failedGuest_409() {
        ProvisioningProgress p = installing();
        p.markFailed(NOW);
        noOpenRow();

        assertThatThrownBy(() -> service.complete(GUEST, matching()))
                .isInstanceOf(AgentReportRejectedException.class).hasMessageContaining("프로비저닝 중이 아닌");
        verify(installer, never()).releaseAfterCompletion(any(), any());
    }

    @Test
    @DisplayName("커서가 다른 phase 면 409 phaseMismatch")
    void otherPhase_409() {
        ProvisioningProgress p = ProvisioningProgress.builder().id(UUID.randomUUID()).guestServer(guest)
                .currentStep(ProvisioningPhaseStep.entryOf(ProvisioningPhase.RAID_CONFIGURATION)).lastTransitionAt(NOW).build();
        p.start(NOW);
        given(progressRepository.findByGuestServer_Id(guest.getId())).willReturn(Optional.of(p));
        noOpenRow();

        assertThatThrownBy(() -> service.complete(GUEST, matching()))
                .isInstanceOf(AgentReportRejectedException.class).hasMessageContaining("phase 밖");
    }

    @Test
    @DisplayName("서빙 전(열린 행도 완료 행도 없음)이면 409 noOpenStep")
    void beforeServing_409() {
        installing();
        noOpenRow();

        assertThatThrownBy(() -> service.complete(GUEST, matching()))
                .isInstanceOf(AgentReportRejectedException.class).hasMessageContaining("열린 OS_INSTALLING 행이 없어");
        verify(cursorAdvancer, never()).advanceOrComplete(any(), any(), any());
    }
}
