package com.example.serverprovision.execution.engine;

import com.example.serverprovision.execution.dto.response.StepCloseResponse;
import com.example.serverprovision.execution.dto.response.StepOpenResponse;
import com.example.serverprovision.execution.entity.GuestServer;
import com.example.serverprovision.execution.entity.ProvisioningProgress;
import com.example.serverprovision.execution.entity.ProvisioningHistory;
import com.example.serverprovision.execution.enums.AgentDirective;
import com.example.serverprovision.execution.enums.ProvisioningPhase;
import com.example.serverprovision.execution.enums.ProvisioningPhaseStep;
import com.example.serverprovision.execution.enums.ProvisioningStatus;
import com.example.serverprovision.execution.exception.AgentReportRejectedException;
import com.example.serverprovision.execution.exception.GuestServerNotFoundException;
import com.example.serverprovision.execution.exception.ProvisioningHistoryNotFoundException;
import com.example.serverprovision.execution.entity.GuestServerDetail;
import com.example.serverprovision.execution.enums.DiscoveryStage;
import com.example.serverprovision.execution.event.GuestServerChangedEvent;
import com.example.serverprovision.execution.repository.GuestServerDetailRepository;
import com.example.serverprovision.execution.repository.GuestServerRepository;
import com.example.serverprovision.execution.repository.ProvisioningProgressRepository;
import com.example.serverprovision.execution.repository.ProvisioningHistoryRepository;
import com.example.serverprovision.execution.vo.GuestToken;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

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
 * E1-0b CP4 → ES-2 — 에이전트 채널 규약: 토큰 인증(404) · 커서는 step 보고를 따라간다(D-1) ·
 * RUNNING 열림/닫힘 멱등(DEC-3) · FAILED 종료 = markFailed 실트리거(DEC-4, 실패 지점 = 커서) ·
 * phase 이탈 open 409 · stepId forging 404.
 */
@ExtendWith(MockitoExtension.class)
class AgentReportServiceTest {

    private static final String TOKEN = "a3f9d2c8b41e4f7a9c0d5e6f7a8b9c1d";
    private static final LocalDateTime T = LocalDateTime.of(2026, 7, 18, 12, 0);

    @Mock GuestServerRepository guestServerRepository;
    @Mock GuestServerDetailRepository guestServerDetailRepository;   // E1-2 — 지시 판정(미수집 여부) 입력
    @Mock ProvisioningProgressRepository provisioningProgressRepository;
    @Mock ProvisioningHistoryRepository provisioningHistoryRepository;
    @Mock ProvisioningHistoryRecorder provisioningHistoryRecorder;
    @Mock PhaseExecutorRegistry phaseExecutorRegistry;               // E1-2 — 소비 훅 위임(기본 empty = 미등록)
    @Mock ApplicationEventPublisher eventPublisher;                  // S7 — 실시간 스트림 신호 발행 검증
    @InjectMocks AgentReportService service;

    private GuestServer guest(UUID id) {
        GuestServer g = GuestServer.builder().id(id).systemUUID(UUID.randomUUID()).build();
        return g;
    }

    private ProvisioningProgress progress(GuestServer g, boolean started, ProvisioningPhaseStep step) {
        return ProvisioningProgress.builder()
                .id(UUID.randomUUID()).guestServer(g)
                .currentStep(step).lastTransitionAt(T)
                .startedAt(started ? T : null)
                .build();
    }

    private GuestServer stubGuest() {
        UUID id = UUID.randomUUID();
        GuestServer g = guest(id);
        given(guestServerRepository.findByGuestToken(new GuestToken(TOKEN))).willReturn(Optional.of(g));
        return g;
    }

    // ==== checkin — 지시 판정 (ES-2: 체크인은 커서를 움직이지 않는다) ====

    @Test
    @DisplayName("첫 체크인(개시 + seed 커서) → COLLECT 지시(미수집) — 옛 BOOTSTRAPPING 전이 특례 소멸(ES-2)")
    void checkin_first_collects() {
        GuestServer g = stubGuest();
        ProvisioningProgress p = progress(g, true, ProvisioningPhaseStep.DIAGNOSTIC_BOOTING);
        given(provisioningProgressRepository.findByGuestServer_Id(g.getId())).willReturn(Optional.of(p));

        var res = service.checkin(TOKEN);

        // seed 커서가 이미 진단 phase + detail 미수집(기본 empty) → 수집 지시 (E1-2 지시 판정)
        assertThat(res.directive()).isEqualTo(AgentDirective.COLLECT);
        assertThat(p.getCurrentStep()).isEqualTo(ProvisioningPhaseStep.DIAGNOSTIC_BOOTING);   // 체크인 무전이
    }

    @Test
    @DisplayName("체크인 — 이미 수집됨(DIAGNOSTIC_ENRICHED) → WAIT (COLLECT 재지시 없음)")
    void checkin_enriched_waits() {
        GuestServer g = stubGuest();
        ProvisioningProgress p = progress(g, true, ProvisioningPhaseStep.INFORMATION_COLLECTING);
        given(provisioningProgressRepository.findByGuestServer_Id(g.getId())).willReturn(Optional.of(p));
        GuestServerDetail enriched = org.mockito.Mockito.mock(GuestServerDetail.class);
        // U3-3 DEC-A — 판정은 엔티티의 isDiagnosticEnriched() 가 갖는다(엔진 · 목록 · 그룹 가드가 공유).
        given(enriched.isDiagnosticEnriched()).willReturn(true);
        given(guestServerDetailRepository.findByServerIdWithBoardModel(g.getId()))
                .willReturn(Optional.of(enriched));

        assertThat(service.checkin(TOKEN).directive()).isEqualTo(AgentDirective.WAIT);
    }

    @Test
    @DisplayName("체크인 — 커서가 진단 이후로 전진한 게스트(FIRMWARE_UPDATING) → REBOOT (DES-2: 진단 리눅스를 떠나라)")
    void checkin_advancedCursor_returnsReboot() {
        GuestServer g = stubGuest();
        ProvisioningProgress p = progress(g, true, ProvisioningPhaseStep.BIOS_UPDATING);   // 진단 이후로 pre-position 된 커서
        given(provisioningProgressRepository.findByGuestServer_Id(g.getId())).willReturn(Optional.of(p));

        var res = service.checkin(TOKEN);

        assertThat(res.directive()).isEqualTo(AgentDirective.REBOOT);
        assertThat(p.currentPhase()).isEqualTo(ProvisioningPhase.FIRMWARE_UPDATING);      // 체크인 무전이
        assertThat(p.isCompleted()).isFalse();                                            // 종단 아닌데도 REBOOT
    }

    @Test
    @DisplayName("재체크인 — 커서 · 전이 시각 불변 (커서는 step 보고에만 움직인다, ES-2)")
    void checkin_again_noTransition() {
        GuestServer g = stubGuest();
        ProvisioningProgress p = progress(g, true, ProvisioningPhaseStep.INFORMATION_COLLECTING);
        given(provisioningProgressRepository.findByGuestServer_Id(g.getId())).willReturn(Optional.of(p));

        service.checkin(TOKEN);

        assertThat(p.getCurrentStep()).isEqualTo(ProvisioningPhaseStep.INFORMATION_COLLECTING);
        assertThat(p.getLastTransitionAt()).isEqualTo(T);   // 전이 시각 불변
    }

    @Test
    @DisplayName("가드 — 미개시 서버 체크인 → AgentReportRejected(409): 게이트 우회 direct POST 거절, 전이 없음")
    void checkin_notStarted_rejected() {
        GuestServer g = stubGuest();
        ProvisioningProgress p = progress(g, false, ProvisioningPhaseStep.DIAGNOSTIC_BOOTING);
        given(provisioningProgressRepository.findByGuestServer_Id(g.getId())).willReturn(Optional.of(p));

        assertThatThrownBy(() -> service.checkin(TOKEN))
                .isInstanceOf(AgentReportRejectedException.class);
        assertThat(p.getCurrentStep()).isEqualTo(ProvisioningPhaseStep.DIAGNOSTIC_BOOTING);   // 커서 불변
    }

    @Test
    @DisplayName("가드 — 회수 서버 체크인 → AgentReportRejected(409)")
    void checkin_decommissioned_rejected() {
        UUID id = UUID.randomUUID();
        GuestServer decom = GuestServer.builder().id(id).systemUUID(UUID.randomUUID())
                .decommissionedAt(T).build();
        given(guestServerRepository.findByGuestToken(new GuestToken(TOKEN))).willReturn(Optional.of(decom));
        given(provisioningProgressRepository.findByGuestServer_Id(id))
                .willReturn(Optional.of(progress(decom, true, ProvisioningPhaseStep.INFORMATION_COLLECTING)));

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
    @DisplayName("openStep — recorder 위임 + stepId 반환 + 커서가 보고 step 을 따라간다(ES-2 D-1)")
    void openStep_delegates() {
        GuestServer g = stubGuest();
        ProvisioningProgress p = progress(g, true, ProvisioningPhaseStep.DIAGNOSTIC_BOOTING);
        given(provisioningProgressRepository.findByGuestServer_Id(g.getId())).willReturn(Optional.of(p));
        ProvisioningHistory opened = ProvisioningHistory.openRunning(g, ProvisioningPhaseStep.INFORMATION_COLLECTING, T);
        given(provisioningHistoryRecorder.openRunning(any(), any(), any())).willReturn(opened);

        StepOpenResponse res = service.openStep(TOKEN, ProvisioningPhaseStep.INFORMATION_COLLECTING);

        assertThat(res.stepId()).isEqualTo(opened.getId());
        assertThat(p.getCurrentStep()).isEqualTo(ProvisioningPhaseStep.INFORMATION_COLLECTING);   // 같은 phase 안 이동
    }

    @Test
    @DisplayName("openStep — 같은 phase 뒤 step 재-open 수용: 재부팅 후 phase 첫 step 재수행 관용(ES-2 D-1)")
    void openStep_withinPhaseRestart_tolerated() {
        GuestServer g = stubGuest();
        ProvisioningProgress p = progress(g, true, ProvisioningPhaseStep.INFORMATION_COLLECTING);   // 수집 중이던 커서
        given(provisioningProgressRepository.findByGuestServer_Id(g.getId())).willReturn(Optional.of(p));
        ProvisioningHistory reopened = ProvisioningHistory.openRunning(g, ProvisioningPhaseStep.DIAGNOSTIC_BOOTING, T);
        given(provisioningHistoryRecorder.openRunning(any(), any(), any())).willReturn(reopened);

        StepOpenResponse res = service.openStep(TOKEN, ProvisioningPhaseStep.DIAGNOSTIC_BOOTING);   // 재부팅 재시작

        assertThat(res.stepId()).isEqualTo(reopened.getId());
        assertThat(p.getCurrentStep()).isEqualTo(ProvisioningPhaseStep.DIAGNOSTIC_BOOTING);   // 커서가 따라감(phase 불변)
    }

    @Test
    @DisplayName("openStep — 커서 phase 밖 step → AgentReportRejected(409) + 원장 미오염 + 커서 불변 (ES-2 게이트)")
    void openStep_phaseMismatch_rejected() {
        GuestServer g = stubGuest();
        ProvisioningProgress p = progress(g, true, ProvisioningPhaseStep.INFORMATION_COLLECTING);   // 진단 phase 커서
        given(provisioningProgressRepository.findByGuestServer_Id(g.getId())).willReturn(Optional.of(p));

        assertThatThrownBy(() -> service.openStep(TOKEN, ProvisioningPhaseStep.BIOS_UPDATING))     // phase 이탈
                .isInstanceOf(AgentReportRejectedException.class);
        verify(provisioningHistoryRecorder, never()).openRunning(any(), any(), any());   // 원장 미오염
        assertThat(p.getCurrentStep()).isEqualTo(ProvisioningPhaseStep.INFORMATION_COLLECTING);    // 커서 불변
    }

    @Test
    @DisplayName("가드 — 미개시 서버 openStep → AgentReportRejected(409): 원장 유령 step 방지")
    void openStep_notStarted_rejected() {
        GuestServer g = stubGuest();
        given(provisioningProgressRepository.findByGuestServer_Id(g.getId()))
                .willReturn(Optional.of(progress(g, false, ProvisioningPhaseStep.DIAGNOSTIC_BOOTING)));

        assertThatThrownBy(() -> service.openStep(TOKEN, ProvisioningPhaseStep.INFORMATION_COLLECTING))
                .isInstanceOf(AgentReportRejectedException.class);
        verify(provisioningHistoryRecorder, never()).openRunning(any(), any(), any());   // 원장 미오염
    }

    @Test
    @DisplayName("closeStep(FAILED) — 행 닫힘 + markFailed 즉시 (실패 신호 실트리거)")
    void close_failed_marksProgressFailed() {
        GuestServer g = stubGuest();
        ProvisioningProgress p = progress(g, true, ProvisioningPhaseStep.DIAGNOSTIC_BOOTING);
        ProvisioningHistory step = ProvisioningHistory.openRunning(g, ProvisioningPhaseStep.INFORMATION_COLLECTING, T);
        given(provisioningHistoryRepository.findById(step.getId())).willReturn(Optional.of(step));
        given(provisioningProgressRepository.findByGuestServer_Id(g.getId())).willReturn(Optional.of(p));

        service.closeStep(TOKEN, step.getId(), ProvisioningStatus.FAILED, "{\"reason\":\"x\"}");

        assertThat(step.getStatus()).isEqualTo(ProvisioningStatus.FAILED);
        assertThat(p.isFailed()).isTrue();
        assertThat(p.getCurrentStep()).isEqualTo(ProvisioningPhaseStep.INFORMATION_COLLECTING);   // 커서 = 실패 지점(D-5)
    }

    @Test
    @DisplayName("closeStep 중복 — no-op 멱등: 행 불변 + markFailed 재발화 없음")
    void close_duplicate_noOp() {
        GuestServer g = stubGuest();
        ProvisioningProgress p = progress(g, true, ProvisioningPhaseStep.INFORMATION_COLLECTING);
        given(provisioningProgressRepository.findByGuestServer_Id(g.getId())).willReturn(Optional.of(p));
        ProvisioningHistory step = ProvisioningHistory.openRunning(g, ProvisioningPhaseStep.INFORMATION_COLLECTING, T);
        step.close(ProvisioningStatus.SUCCEEDED, null, T);
        given(provisioningHistoryRepository.findById(step.getId())).willReturn(Optional.of(step));

        service.closeStep(TOKEN, step.getId(), ProvisioningStatus.FAILED, null);

        assertThat(step.getStatus()).isEqualTo(ProvisioningStatus.SUCCEEDED);   // 행 불변
        assertThat(p.isFailed()).isFalse();                                     // markFailed 재발화 없음
    }

    @Test
    @DisplayName("closeStep(SUCCEEDED) — 해당 phase 실행기 소비 훅 위임 (E1-2, DEC-6 확장)")
    void close_succeeded_delegatesToExecutor() {
        GuestServer g = stubGuest();
        ProvisioningProgress p = progress(g, true, ProvisioningPhaseStep.INFORMATION_COLLECTING);
        given(provisioningProgressRepository.findByGuestServer_Id(g.getId())).willReturn(Optional.of(p));
        ProvisioningHistory step = ProvisioningHistory.openRunning(g, ProvisioningPhaseStep.INFORMATION_COLLECTING, T);
        given(provisioningHistoryRepository.findById(step.getId())).willReturn(Optional.of(step));
        ProvisioningPhaseExecutor executor = org.mockito.Mockito.mock(ProvisioningPhaseExecutor.class);
        given(phaseExecutorRegistry.find(ProvisioningPhase.DIAGNOSE_LINUX)).willReturn(Optional.of(executor));

        service.closeStep(TOKEN, step.getId(), ProvisioningStatus.SUCCEEDED, "{}");

        verify(executor).onStepClosed(g, p, step);
    }

    @Test
    @DisplayName("closeStep — 소비 훅이 완주를 판정하면 응답 directive = REBOOT (완주 지시의 유일한 운반로)")
    void close_completed_returnsReboot() {
        GuestServer g = stubGuest();
        ProvisioningProgress p = progress(g, true, ProvisioningPhaseStep.INFORMATION_COLLECTING);
        given(provisioningProgressRepository.findByGuestServer_Id(g.getId())).willReturn(Optional.of(p));
        ProvisioningHistory step = ProvisioningHistory.openRunning(g, ProvisioningPhaseStep.INFORMATION_COLLECTING, T);
        given(provisioningHistoryRepository.findById(step.getId())).willReturn(Optional.of(step));
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
    @DisplayName("closeStep — 소비 훅이 커서를 전진(FIRMWARE_UPDATING)시키면 응답 directive = REBOOT + not completed (ES-1 · DES-2)")
    void close_advancedCursor_returnsReboot() {
        GuestServer g = stubGuest();
        ProvisioningProgress p = progress(g, true, ProvisioningPhaseStep.INFORMATION_COLLECTING);
        given(provisioningProgressRepository.findByGuestServer_Id(g.getId())).willReturn(Optional.of(p));
        ProvisioningHistory step = ProvisioningHistory.openRunning(g, ProvisioningPhaseStep.INFORMATION_COLLECTING, T);
        given(provisioningHistoryRepository.findById(step.getId())).willReturn(Optional.of(step));
        ProvisioningPhaseExecutor executor = org.mockito.Mockito.mock(ProvisioningPhaseExecutor.class);
        given(phaseExecutorRegistry.find(ProvisioningPhase.DIAGNOSE_LINUX)).willReturn(Optional.of(executor));
        org.mockito.Mockito.doAnswer(inv -> {   // 소비 훅이 같은 트랜잭션에서 다음 소유 phase 의 진입 step 으로 pre-position(ES-1 · ES-2)
            p.advanceToEntry(ProvisioningPhaseStep.BIOS_UPDATING, T.plusSeconds(1));
            return null;
        }).when(executor).onStepClosed(g, p, step);

        StepCloseResponse res = service.closeStep(TOKEN, step.getId(), ProvisioningStatus.SUCCEEDED, "{}");

        assertThat(res.directive()).isEqualTo(AgentDirective.REBOOT);                      // 전진했으므로 진단을 떠나라
        assertThat(p.currentPhase()).isEqualTo(ProvisioningPhase.FIRMWARE_UPDATING);
        assertThat(p.isCompleted()).isFalse();
    }

    @Test
    @DisplayName("완주 후 중복 close(REBOOT 응답 유실 재전송) — 게이트의 좁은 예외: no-op + REBOOT 재계산")
    void close_duplicateAfterCompletion_returnsRebootAgain() {
        GuestServer g = stubGuest();
        ProvisioningProgress p = progress(g, true, ProvisioningPhaseStep.INFORMATION_COLLECTING);
        p.markCompleted(T);   // 완주 상태 (derive = PROVISIONED)
        given(provisioningProgressRepository.findByGuestServer_Id(g.getId())).willReturn(Optional.of(p));
        ProvisioningHistory step = ProvisioningHistory.openRunning(g, ProvisioningPhaseStep.INFORMATION_COLLECTING, T);
        step.close(ProvisioningStatus.SUCCEEDED, "{}", T);   // 이미 종결된 행
        given(provisioningHistoryRepository.findById(step.getId())).willReturn(Optional.of(step));

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
                .willReturn(Optional.of(progress(decom, true, ProvisioningPhaseStep.INFORMATION_COLLECTING)));

        assertThatThrownBy(() -> service.closeStep(TOKEN, UUID.randomUUID(), ProvisioningStatus.SUCCEEDED, null))
                .isInstanceOf(AgentReportRejectedException.class);
        verify(provisioningHistoryRepository, never()).findById(any());   // 가드가 step 조회보다 앞선다
    }

    @Test
    @DisplayName("closeStep — 타 게스트 stepId(forging)·미존재 → ProvisioningHistoryNotFound(404)")
    void close_forgedOrUnknownStep_throws404() {
        GuestServer g = stubGuest();
        given(provisioningProgressRepository.findByGuestServer_Id(g.getId()))
                .willReturn(Optional.of(progress(g, true, ProvisioningPhaseStep.INFORMATION_COLLECTING)));
        GuestServer other = guest(UUID.randomUUID());
        ProvisioningHistory foreign = ProvisioningHistory.openRunning(other, ProvisioningPhaseStep.OS_INSTALLING, T);
        given(provisioningHistoryRepository.findById(foreign.getId())).willReturn(Optional.of(foreign));

        assertThatThrownBy(() -> service.closeStep(TOKEN, foreign.getId(), ProvisioningStatus.SUCCEEDED, null))
                .isInstanceOf(ProvisioningHistoryNotFoundException.class);

        UUID unknown = UUID.randomUUID();
        given(provisioningHistoryRepository.findById(unknown)).willReturn(Optional.empty());
        assertThatThrownBy(() -> service.closeStep(TOKEN, unknown, ProvisioningStatus.SUCCEEDED, null))
                .isInstanceOf(ProvisioningHistoryNotFoundException.class);
    }

    // ==== S7 — 실시간 스트림 신호 발행 (발행 누락 = "그 화면만 안 갱신" 회귀) ====

    @Test
    @DisplayName("checkin — 접수 말미 변화 신호(GuestServerChangedEvent) 발행")
    void checkin_publishesChangedSignal() {
        GuestServer g = stubGuest();
        given(provisioningProgressRepository.findByGuestServer_Id(g.getId()))
                .willReturn(Optional.of(progress(g, true, ProvisioningPhaseStep.INFORMATION_COLLECTING)));

        service.checkin(TOKEN);

        verify(eventPublisher).publishEvent(new GuestServerChangedEvent(g.getId()));
    }

    @Test
    @DisplayName("closeStep — 접수 말미 변화 신호 발행 (전이·원장·소비 훅과 같은 트랜잭션이라 1회면 충분)")
    void closeStep_publishesChangedSignal() {
        GuestServer g = stubGuest();
        ProvisioningProgress p = progress(g, true, ProvisioningPhaseStep.INFORMATION_COLLECTING);
        given(provisioningProgressRepository.findByGuestServer_Id(g.getId())).willReturn(Optional.of(p));
        ProvisioningHistory step = ProvisioningHistory.openRunning(g, ProvisioningPhaseStep.INFORMATION_COLLECTING, T);
        given(provisioningHistoryRepository.findById(step.getId())).willReturn(Optional.of(step));

        service.closeStep(TOKEN, step.getId(), ProvisioningStatus.SUCCEEDED, "{}");

        verify(eventPublisher).publishEvent(new GuestServerChangedEvent(g.getId()));
    }

    @Test
    @DisplayName("가드 거절(409) — 변화 신호 미발행 (롤백 트랜잭션과 함께 사라지는 계약의 발행측 반쪽)")
    void rejectedCheckin_publishesNothing() {
        GuestServer g = stubGuest();
        given(provisioningProgressRepository.findByGuestServer_Id(g.getId()))
                .willReturn(Optional.of(progress(g, false, ProvisioningPhaseStep.DIAGNOSTIC_BOOTING)));

        assertThatThrownBy(() -> service.checkin(TOKEN))
                .isInstanceOf(AgentReportRejectedException.class);

        verify(eventPublisher, never()).publishEvent(any());
    }
}
