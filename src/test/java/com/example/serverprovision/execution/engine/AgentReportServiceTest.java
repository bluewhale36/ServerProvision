package com.example.serverprovision.execution.engine;

import com.example.serverprovision.execution.dto.response.StepCloseResponse;
import com.example.serverprovision.execution.dto.response.StepOpenResponse;
import com.example.serverprovision.execution.entity.GuestServer;
import com.example.serverprovision.execution.entity.ProvisioningProgress;
import com.example.serverprovision.execution.entity.SetupStep;
import com.example.serverprovision.execution.enums.AgentDirective;
import com.example.serverprovision.execution.enums.ProvisioningPhase;
import com.example.serverprovision.execution.enums.ProvisioningPhaseStep;
import com.example.serverprovision.execution.enums.ProvisioningStatus;
import com.example.serverprovision.execution.exception.AgentReportRejectedException;
import com.example.serverprovision.execution.exception.GuestServerNotFoundException;
import com.example.serverprovision.execution.exception.SetupStepNotFoundException;
import com.example.serverprovision.execution.entity.GuestServerDetail;
import com.example.serverprovision.execution.enums.DiscoveryStage;
import com.example.serverprovision.execution.repository.GuestServerDetailRepository;
import com.example.serverprovision.execution.repository.GuestServerRepository;
import com.example.serverprovision.execution.repository.ProvisioningProgressRepository;
import com.example.serverprovision.execution.repository.SetupStepRepository;
import com.example.serverprovision.execution.vo.GuestToken;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * E1-0b CP4 — 에이전트 채널 규약: 토큰 인증(404) · 첫 체크인 1회 전이(DEC-2) ·
 * RUNNING 열림/닫힘 멱등(DEC-3) · FAILED 종료 = markFailed 실트리거(DEC-4) · stepId forging 404.
 */
@ExtendWith(MockitoExtension.class)
class AgentReportServiceTest {

    private static final String TOKEN = "a3f9d2c8b41e4f7a9c0d5e6f7a8b9c1d";
    private static final LocalDateTime T = LocalDateTime.of(2026, 7, 18, 12, 0);

    @Mock GuestServerRepository guestServerRepository;
    @Mock GuestServerDetailRepository guestServerDetailRepository;   // E1-2 — 지시 판정(미수집 여부) 입력
    @Mock ProvisioningProgressRepository provisioningProgressRepository;
    @Mock SetupStepRepository setupStepRepository;
    @Mock SetupStepRecorder setupStepRecorder;
    @Mock PhaseExecutorRegistry phaseExecutorRegistry;               // E1-2 — 소비 훅 위임(기본 empty = 미등록)
    @InjectMocks AgentReportService service;

    private GuestServer guest(UUID id) {
        GuestServer g = GuestServer.builder().id(id).systemUUID(UUID.randomUUID()).build();
        return g;
    }

    private ProvisioningProgress progress(GuestServer g, boolean started, ProvisioningPhase phase) {
        return ProvisioningProgress.builder()
                .id(UUID.randomUUID()).guestServer(g)
                .currentPhase(phase).lastTransitionAt(T)
                .startedAt(started ? T : null)
                .build();
    }

    private GuestServer stubGuest() {
        UUID id = UUID.randomUUID();
        GuestServer g = guest(id);
        given(guestServerRepository.findByGuestToken(new GuestToken(TOKEN))).willReturn(Optional.of(g));
        return g;
    }

    // ==== checkin — 첫 체크인 1회 전이 =================================

    @Test
    @DisplayName("첫 체크인(개시 + BOOTSTRAPPING) → DIAGNOSE_LINUX 전이 + COLLECT 지시(미수집)")
    void checkin_first_advances() {
        GuestServer g = stubGuest();
        ProvisioningProgress p = progress(g, true, ProvisioningPhase.BOOTSTRAPPING);
        given(provisioningProgressRepository.findByGuestServer_Id(g.getId())).willReturn(Optional.of(p));

        var res = service.checkin(TOKEN);

        // 전이 후 커서 = 진단 + detail 미수집(기본 empty) → 수집 지시 (E1-2 지시 판정)
        assertThat(res.directive()).isEqualTo(AgentDirective.COLLECT);
        assertThat(p.getCurrentPhase()).isEqualTo(ProvisioningPhase.DIAGNOSE_LINUX);
    }

    @Test
    @DisplayName("체크인 — 이미 수집됨(DIAGNOSTIC_ENRICHED) → WAIT (COLLECT 재지시 없음)")
    void checkin_enriched_waits() {
        GuestServer g = stubGuest();
        ProvisioningProgress p = progress(g, true, ProvisioningPhase.DIAGNOSE_LINUX);
        given(provisioningProgressRepository.findByGuestServer_Id(g.getId())).willReturn(Optional.of(p));
        GuestServerDetail enriched = org.mockito.Mockito.mock(GuestServerDetail.class);
        given(enriched.getDiscoveryStage()).willReturn(DiscoveryStage.DIAGNOSTIC_ENRICHED);
        given(guestServerDetailRepository.findByServerIdWithBoardModel(g.getId()))
                .willReturn(Optional.of(enriched));

        assertThat(service.checkin(TOKEN).directive()).isEqualTo(AgentDirective.WAIT);
    }

    @Test
    @DisplayName("재체크인(이미 진단 진입) — 전이 없음(1회뿐)")
    void checkin_again_noTransition() {
        GuestServer g = stubGuest();
        ProvisioningProgress p = progress(g, true, ProvisioningPhase.DIAGNOSE_LINUX);
        given(provisioningProgressRepository.findByGuestServer_Id(g.getId())).willReturn(Optional.of(p));

        service.checkin(TOKEN);

        assertThat(p.getCurrentPhase()).isEqualTo(ProvisioningPhase.DIAGNOSE_LINUX);
        assertThat(p.getLastTransitionAt()).isEqualTo(T);   // 전이 시각 불변
    }

    @Test
    @DisplayName("가드 — 미개시 서버 체크인 → AgentReportRejected(409): 게이트 우회 direct POST 거절, 전이 없음")
    void checkin_notStarted_rejected() {
        GuestServer g = stubGuest();
        ProvisioningProgress p = progress(g, false, ProvisioningPhase.BOOTSTRAPPING);
        given(provisioningProgressRepository.findByGuestServer_Id(g.getId())).willReturn(Optional.of(p));

        assertThatThrownBy(() -> service.checkin(TOKEN))
                .isInstanceOf(AgentReportRejectedException.class);
        assertThat(p.getCurrentPhase()).isEqualTo(ProvisioningPhase.BOOTSTRAPPING);   // 커서 불변
    }

    @Test
    @DisplayName("가드 — 회수 서버 체크인 → AgentReportRejected(409)")
    void checkin_decommissioned_rejected() {
        UUID id = UUID.randomUUID();
        GuestServer decom = GuestServer.builder().id(id).systemUUID(UUID.randomUUID())
                .decommissionedAt(T).build();
        given(guestServerRepository.findByGuestToken(new GuestToken(TOKEN))).willReturn(Optional.of(decom));
        given(provisioningProgressRepository.findByGuestServer_Id(id))
                .willReturn(Optional.of(progress(decom, true, ProvisioningPhase.DIAGNOSE_LINUX)));

        assertThatThrownBy(() -> service.checkin(TOKEN))
                .isInstanceOf(AgentReportRejectedException.class);
    }

    @Test
    @DisplayName("토큰 불일치·공백 → GuestServerNotFound(404) — 존재 비노출")
    void checkin_badToken_throws404() {
        given(guestServerRepository.findByGuestToken(any())).willReturn(Optional.empty());
        assertThatThrownBy(() -> service.checkin("deadbeef"))
                .isInstanceOf(GuestServerNotFoundException.class);
        assertThatThrownBy(() -> service.checkin("  "))
                .isInstanceOf(GuestServerNotFoundException.class);
    }

    // ==== steps open / close ==========================================

    @Test
    @DisplayName("openStep — recorder 위임 + stepId 반환")
    void openStep_delegates() {
        GuestServer g = stubGuest();
        given(provisioningProgressRepository.findByGuestServer_Id(g.getId()))
                .willReturn(Optional.of(progress(g, true, ProvisioningPhase.DIAGNOSE_LINUX)));
        SetupStep opened = SetupStep.openRunning(g, ProvisioningPhaseStep.INFORMATION_COLLECTING, T);
        given(setupStepRecorder.openRunning(any(), any(), any())).willReturn(opened);

        StepOpenResponse res = service.openStep(TOKEN, ProvisioningPhaseStep.INFORMATION_COLLECTING);

        assertThat(res.stepId()).isEqualTo(opened.getId());
    }

    @Test
    @DisplayName("가드 — 미개시 서버 openStep → AgentReportRejected(409): 원장 유령 step 방지")
    void openStep_notStarted_rejected() {
        GuestServer g = stubGuest();
        given(provisioningProgressRepository.findByGuestServer_Id(g.getId()))
                .willReturn(Optional.of(progress(g, false, ProvisioningPhase.BOOTSTRAPPING)));

        assertThatThrownBy(() -> service.openStep(TOKEN, ProvisioningPhaseStep.INFORMATION_COLLECTING))
                .isInstanceOf(AgentReportRejectedException.class);
        verify(setupStepRecorder, never()).openRunning(any(), any(), any());   // 원장 미오염
    }

    @Test
    @DisplayName("closeStep(FAILED) — 행 닫힘 + markFailed 즉시 (실패 신호 실트리거)")
    void close_failed_marksProgressFailed() {
        GuestServer g = stubGuest();
        ProvisioningProgress p = progress(g, true, ProvisioningPhase.DIAGNOSE_LINUX);
        SetupStep step = SetupStep.openRunning(g, ProvisioningPhaseStep.INFORMATION_COLLECTING, T);
        given(setupStepRepository.findById(step.getId())).willReturn(Optional.of(step));
        given(provisioningProgressRepository.findByGuestServer_Id(g.getId())).willReturn(Optional.of(p));

        service.closeStep(TOKEN, step.getId(), ProvisioningStatus.FAILED, "{\"reason\":\"x\"}");

        assertThat(step.getStatus()).isEqualTo(ProvisioningStatus.FAILED);
        assertThat(p.isFailed()).isTrue();
        assertThat(p.getFailedStepCode()).isEqualTo(ProvisioningPhaseStep.INFORMATION_COLLECTING);
    }

    @Test
    @DisplayName("closeStep 중복 — no-op 멱등: 행 불변 + markFailed 재발화 없음")
    void close_duplicate_noOp() {
        GuestServer g = stubGuest();
        ProvisioningProgress p = progress(g, true, ProvisioningPhase.DIAGNOSE_LINUX);
        given(provisioningProgressRepository.findByGuestServer_Id(g.getId())).willReturn(Optional.of(p));
        SetupStep step = SetupStep.openRunning(g, ProvisioningPhaseStep.INFORMATION_COLLECTING, T);
        step.close(ProvisioningStatus.SUCCEEDED, null, T);
        given(setupStepRepository.findById(step.getId())).willReturn(Optional.of(step));

        service.closeStep(TOKEN, step.getId(), ProvisioningStatus.FAILED, null);

        assertThat(step.getStatus()).isEqualTo(ProvisioningStatus.SUCCEEDED);   // 행 불변
        assertThat(p.isFailed()).isFalse();                                     // markFailed 재발화 없음
    }

    @Test
    @DisplayName("closeStep(SUCCEEDED) — 해당 phase 실행기 소비 훅 위임 (E1-2, DEC-6 확장)")
    void close_succeeded_delegatesToExecutor() {
        GuestServer g = stubGuest();
        ProvisioningProgress p = progress(g, true, ProvisioningPhase.DIAGNOSE_LINUX);
        given(provisioningProgressRepository.findByGuestServer_Id(g.getId())).willReturn(Optional.of(p));
        SetupStep step = SetupStep.openRunning(g, ProvisioningPhaseStep.INFORMATION_COLLECTING, T);
        given(setupStepRepository.findById(step.getId())).willReturn(Optional.of(step));
        ProvisioningPhaseExecutor executor = org.mockito.Mockito.mock(ProvisioningPhaseExecutor.class);
        given(phaseExecutorRegistry.find(ProvisioningPhase.DIAGNOSE_LINUX)).willReturn(Optional.of(executor));

        service.closeStep(TOKEN, step.getId(), ProvisioningStatus.SUCCEEDED, "{}");

        verify(executor).onStepClosed(g, p, step);
    }

    @Test
    @DisplayName("closeStep — 소비 훅이 완주를 판정하면 응답 directive = REBOOT (완주 지시의 유일한 운반로)")
    void close_completed_returnsReboot() {
        GuestServer g = stubGuest();
        ProvisioningProgress p = progress(g, true, ProvisioningPhase.DIAGNOSE_LINUX);
        given(provisioningProgressRepository.findByGuestServer_Id(g.getId())).willReturn(Optional.of(p));
        SetupStep step = SetupStep.openRunning(g, ProvisioningPhaseStep.INFORMATION_COLLECTING, T);
        given(setupStepRepository.findById(step.getId())).willReturn(Optional.of(step));
        ProvisioningPhaseExecutor executor = org.mockito.Mockito.mock(ProvisioningPhaseExecutor.class);
        given(phaseExecutorRegistry.find(ProvisioningPhase.DIAGNOSE_LINUX)).willReturn(Optional.of(executor));
        org.mockito.Mockito.doAnswer(inv -> {   // 소비 훅이 같은 트랜잭션에서 markCompleted (DEC-25)
            p.markCompleted(T.plusSeconds(1));
            return null;
        }).when(executor).onStepClosed(g, p, step);

        StepCloseResponse res = service.closeStep(TOKEN, step.getId(), ProvisioningStatus.SUCCEEDED, "{}");

        assertThat(res.directive()).isEqualTo(AgentDirective.REBOOT);
    }

    @Test
    @DisplayName("완주 후 중복 close(REBOOT 응답 유실 재전송) — 게이트의 좁은 예외: no-op + REBOOT 재계산")
    void close_duplicateAfterCompletion_returnsRebootAgain() {
        GuestServer g = stubGuest();
        ProvisioningProgress p = progress(g, true, ProvisioningPhase.DIAGNOSE_LINUX);
        p.markCompleted(T);   // 완주 상태 (derive = PROVISIONED)
        given(provisioningProgressRepository.findByGuestServer_Id(g.getId())).willReturn(Optional.of(p));
        SetupStep step = SetupStep.openRunning(g, ProvisioningPhaseStep.INFORMATION_COLLECTING, T);
        step.close(ProvisioningStatus.SUCCEEDED, "{}", T);   // 이미 종결된 행
        given(setupStepRepository.findById(step.getId())).willReturn(Optional.of(step));

        StepCloseResponse res = service.closeStep(TOKEN, step.getId(), ProvisioningStatus.SUCCEEDED, "{}");

        assertThat(res.directive()).isEqualTo(AgentDirective.REBOOT);
        assertThat(step.getStatus()).isEqualTo(ProvisioningStatus.SUCCEEDED);   // 행 불변(no-op)
    }

    @Test
    @DisplayName("가드 — 회수 서버 closeStep → AgentReportRejected(409): step 조회 이전에 거절")
    void close_decommissioned_rejected() {
        UUID id = UUID.randomUUID();
        GuestServer decom = GuestServer.builder().id(id).systemUUID(UUID.randomUUID())
                .decommissionedAt(T).build();
        given(guestServerRepository.findByGuestToken(new GuestToken(TOKEN))).willReturn(Optional.of(decom));
        given(provisioningProgressRepository.findByGuestServer_Id(id))
                .willReturn(Optional.of(progress(decom, true, ProvisioningPhase.DIAGNOSE_LINUX)));

        assertThatThrownBy(() -> service.closeStep(TOKEN, UUID.randomUUID(), ProvisioningStatus.SUCCEEDED, null))
                .isInstanceOf(AgentReportRejectedException.class);
        verify(setupStepRepository, never()).findById(any());   // 가드가 step 조회보다 앞선다
    }

    @Test
    @DisplayName("closeStep — 타 게스트 stepId(forging)·미존재 → SetupStepNotFound(404)")
    void close_forgedOrUnknownStep_throws404() {
        GuestServer g = stubGuest();
        given(provisioningProgressRepository.findByGuestServer_Id(g.getId()))
                .willReturn(Optional.of(progress(g, true, ProvisioningPhase.DIAGNOSE_LINUX)));
        GuestServer other = guest(UUID.randomUUID());
        SetupStep foreign = SetupStep.openRunning(other, ProvisioningPhaseStep.OS_INSTALLING, T);
        given(setupStepRepository.findById(foreign.getId())).willReturn(Optional.of(foreign));

        assertThatThrownBy(() -> service.closeStep(TOKEN, foreign.getId(), ProvisioningStatus.SUCCEEDED, null))
                .isInstanceOf(SetupStepNotFoundException.class);

        UUID unknown = UUID.randomUUID();
        given(setupStepRepository.findById(unknown)).willReturn(Optional.empty());
        assertThatThrownBy(() -> service.closeStep(TOKEN, unknown, ProvisioningStatus.SUCCEEDED, null))
                .isInstanceOf(SetupStepNotFoundException.class);
    }
}
