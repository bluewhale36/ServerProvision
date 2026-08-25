package com.example.serverprovision.execution.service;

import com.example.serverprovision.execution.dto.request.UpdateGuestServerRequest;
import com.example.serverprovision.execution.entity.GuestServer;
import com.example.serverprovision.execution.entity.ProvisioningProgress;
import com.example.serverprovision.execution.enums.ProvisioningPhase;
import com.example.serverprovision.execution.enums.ProvisioningPhaseStep;
import com.example.serverprovision.execution.event.GuestServerChangedEvent;
import com.example.serverprovision.execution.exception.GuestServerNotFoundException;
import com.example.serverprovision.execution.exception.ProvisioningRetryRejectedException;
import com.example.serverprovision.execution.exception.ProvisioningStartRejectedException;
import com.example.serverprovision.execution.repository.GuestServerRepository;
import com.example.serverprovision.execution.repository.ProvisioningProgressRepository;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * U1 CP4 — {@link GuestServerCommandService} 단위 테스트. 인라인 수정(4필드, blank→null), 회수(멱등),
 * 유니크 위임, 404 를 검증한다. E1-0a — 프로비저닝 개시(happy / 재개시 409 / 회수 409) 추가.
 */
@ExtendWith(MockitoExtension.class)
class GuestServerCommandServiceTest {

    @Mock GuestServerRepository guestServerRepository;
    @Mock ProvisioningProgressRepository provisioningProgressRepository;
    @Mock com.example.serverprovision.execution.engine.ProvisioningHistoryRecorder provisioningHistoryRecorder;   // ES-2 D-5 — 수동 전환 원장 표식
    @Mock RetryPolicy retryPolicy;   // E2-1-b CP5 F-1 — 재시도 차단 판정(원장 사실 포함)은 정책이 든다
    @Mock com.example.serverprovision.execution.engine.phase.PhaseCursorAdvancer phaseCursorAdvancer;   // R13 — 개시 시 소급 완주 판정
    @Mock ApplicationEventPublisher eventPublisher;   // S7 — 실시간 스트림 신호 발행 검증
    @InjectMocks GuestServerCommandService service;

    private GuestServer server(UUID id) {
        return GuestServer.builder().id(id).systemUUID(UUID.randomUUID()).build();
    }

    private ProvisioningProgress seedProgress() {
        return ProvisioningProgress.builder()
                .currentStep(ProvisioningPhaseStep.DIAGNOSTIC_BOOTING)   // ES-2 seed 계약
                .lastTransitionAt(LocalDateTime.now())
                .build();
    }

    @Test
    @DisplayName("update — 4필드 갱신 + 빈 입력은 null 정규화")
    void update_appliesAndNormalizes() {
        UUID id = UUID.randomUUID();
        GuestServer s = server(id);
        given(guestServerRepository.findById(id)).willReturn(Optional.of(s));

        service.update(id, new UpdateGuestServerRequest(" web-01 ", "RE2108", "  ", null));

        assertThat(s.getName()).isEqualTo("web-01");        // trim
        assertThat(s.getModelName()).isEqualTo("RE2108");
        assertThat(s.getSerialNumber()).isNull();           // blank → null
        assertThat(s.getMemo()).isNull();
        verify(eventPublisher).publishEvent(new GuestServerChangedEvent(id));   // S7 — 다른 탭 동기화 신호
    }

    @Test
    @DisplayName("update — 없는 id → GuestServerNotFoundException")
    void update_notFound() {
        UUID id = UUID.randomUUID();
        given(guestServerRepository.findById(id)).willReturn(Optional.empty());

        assertThatThrownBy(() -> service.update(id, new UpdateGuestServerRequest("x", null, null, null)))
                .isInstanceOf(GuestServerNotFoundException.class);
    }

    @Test
    @DisplayName("decommission — 회수 시각 기록")
    void decommission_recordsTime() {
        UUID id = UUID.randomUUID();
        GuestServer s = server(id);
        given(guestServerRepository.findById(id)).willReturn(Optional.of(s));
        given(provisioningProgressRepository.findByGuestServer_Id(id)).willReturn(Optional.empty());

        service.decommission(id);

        assertThat(s.getDecommissionedAt()).isNotNull();
        verify(eventPublisher).publishEvent(new GuestServerChangedEvent(id));
    }

    @Test
    @DisplayName("decommission — 펌웨어를 굽는 중이면 DisruptiveActionRejectedException(409, R13 후속)")
    void decommission_duringFlash_rejected() {
        UUID id = UUID.randomUUID();
        GuestServer s = server(id);
        ProvisioningProgress flashing = seedProgress();
        flashing.start(LocalDateTime.now());
        flashing.advanceToEntry(ProvisioningPhaseStep.BIOS_UPDATING, LocalDateTime.now());
        flashing.positionAt(ProvisioningPhaseStep.BIOS_UPDATING, LocalDateTime.now());
        given(guestServerRepository.findById(id)).willReturn(Optional.of(s));
        given(provisioningProgressRepository.findByGuestServer_Id(id)).willReturn(Optional.of(flashing));

        assertThatThrownBy(() -> service.decommission(id))
                .isInstanceOf(com.example.serverprovision.execution.exception.DisruptiveActionRejectedException.class);
        assertThat(s.getDecommissionedAt()).isNull();
    }

    @Test
    @DisplayName("decommission — 이미 회수된 서버는 최초 시각 보존(멱등)")
    void decommission_idempotent() {
        UUID id = UUID.randomUUID();
        LocalDateTime first = LocalDateTime.now().minusDays(1);
        GuestServer s = GuestServer.builder().id(id).systemUUID(UUID.randomUUID()).decommissionedAt(first).build();
        given(guestServerRepository.findById(id)).willReturn(Optional.of(s));
        given(provisioningProgressRepository.findByGuestServer_Id(id)).willReturn(Optional.empty());

        service.decommission(id);

        assertThat(s.getDecommissionedAt()).isEqualTo(first);
    }

    @Test
    @DisplayName("isNameTakenByOther / isSerialTakenByOther — repo 위임")
    void uniquenessQueries_delegate() {
        UUID id = UUID.randomUUID();
        given(guestServerRepository.existsByNameAndIdNot("dup", id)).willReturn(true);
        given(guestServerRepository.existsBySerialNumberAndIdNot("S1", id)).willReturn(false);

        assertThat(service.isNameTakenByOther(id, "dup")).isTrue();
        assertThat(service.isSerialTakenByOther(id, "S1")).isFalse();
    }

    // ==== 프로비저닝 개시 (E1-0a, DEC-26) ======================================

    @Test
    @DisplayName("startProvisioning — startedAt 기록 (개시 SSOT 가드 통과)")
    void start_records() {
        UUID id = UUID.randomUUID();
        ProvisioningProgress progress = seedProgress();
        given(guestServerRepository.findById(id)).willReturn(Optional.of(server(id)));
        given(provisioningProgressRepository.findByGuestServer_Id(id)).willReturn(Optional.of(progress));

        service.startProvisioning(id);

        assertThat(progress.isStarted()).isTrue();
        // 수집 완주 표식(INFORMATION_PERSISTING)이 아니면 소급 완주 판정은 없다(R13)
        verify(phaseCursorAdvancer, never()).advanceOrComplete(any(), any(), any());
        verify(eventPublisher).publishEvent(new GuestServerChangedEvent(id));
    }

    @Test
    @DisplayName("startProvisioning(R13) — 수집 완주 대기(커서 INFORMATION_PERSISTING)면 유보된 완주 판정을 소급 집행")
    void start_afterCollectionDone_advancesRetroactively() {
        UUID id = UUID.randomUUID();
        ProvisioningProgress progress = ProvisioningProgress.builder()
                .currentStep(ProvisioningPhaseStep.INFORMATION_PERSISTING)   // 미개시 수집 완주 유보 상태
                .lastTransitionAt(LocalDateTime.now())
                .build();
        given(guestServerRepository.findById(id)).willReturn(Optional.of(server(id)));
        given(provisioningProgressRepository.findByGuestServer_Id(id)).willReturn(Optional.of(progress));

        service.startProvisioning(id);

        assertThat(progress.isStarted()).isTrue();
        verify(phaseCursorAdvancer).advanceOrComplete(eq(progress), eq(id), any());
    }

    @Test
    @DisplayName("startProvisioning(R13) — 실패 상태(미개시 진단 실패) → ProvisioningStartRejectedException(재시도 안내)")
    void start_failed_rejected() {
        UUID id = UUID.randomUUID();
        ProvisioningProgress progress = seedProgress();
        progress.markFailed(LocalDateTime.now());   // 미개시 진단 창의 게스트 FAILED 보고 재현
        given(guestServerRepository.findById(id)).willReturn(Optional.of(server(id)));
        given(provisioningProgressRepository.findByGuestServer_Id(id)).willReturn(Optional.of(progress));

        assertThatThrownBy(() -> service.startProvisioning(id))
                .isInstanceOf(ProvisioningStartRejectedException.class)
                .hasMessageContaining("재시도");
        verify(phaseCursorAdvancer, never()).advanceOrComplete(any(), any(), any());
    }

    @Test
    @DisplayName("startProvisioning — 이미 개시된 서버 → ProvisioningStartRejectedException(409 안전망)")
    void start_alreadyStarted_rejected() {
        UUID id = UUID.randomUUID();
        ProvisioningProgress progress = seedProgress();
        progress.start(LocalDateTime.now());
        given(guestServerRepository.findById(id)).willReturn(Optional.of(server(id)));
        given(provisioningProgressRepository.findByGuestServer_Id(id)).willReturn(Optional.of(progress));

        assertThatThrownBy(() -> service.startProvisioning(id))
                .isInstanceOf(ProvisioningStartRejectedException.class)
                .hasMessageContaining("이미 개시");
        verify(eventPublisher, never()).publishEvent(any());   // 거절 = 신호 없음
    }

    @Test
    @DisplayName("startProvisioning — 회수된 서버 → ProvisioningStartRejectedException(409 안전망)")
    void start_decommissioned_rejected() {
        UUID id = UUID.randomUUID();
        GuestServer decommissioned = GuestServer.builder()
                .id(id).systemUUID(UUID.randomUUID()).decommissionedAt(LocalDateTime.now()).build();
        given(guestServerRepository.findById(id)).willReturn(Optional.of(decommissioned));
        given(provisioningProgressRepository.findByGuestServer_Id(id)).willReturn(Optional.of(seedProgress()));

        assertThatThrownBy(() -> service.startProvisioning(id))
                .isInstanceOf(ProvisioningStartRejectedException.class)
                .hasMessageContaining("회수");
    }

    @Test
    @DisplayName("startProvisioning — 없는 id → GuestServerNotFoundException")
    void start_notFound() {
        UUID id = UUID.randomUUID();
        given(guestServerRepository.findById(id)).willReturn(Optional.empty());

        assertThatThrownBy(() -> service.startProvisioning(id))
                .isInstanceOf(GuestServerNotFoundException.class);
    }

    // ==== 수동 실패 전환 · 재시도 (E1-2, DEC-4) + S7 발행 검증 ==================

    @Test
    @DisplayName("markFailedManually — 실패 신호 + 운영자 표식 원장 instant 행(ES-2 D-5) + 변화 신호 발행")
    void markFailedManually_records_andPublishes() {
        UUID id = UUID.randomUUID();
        ProvisioningProgress progress = ProvisioningProgress.builder()
                .guestServer(server(id))
                .currentStep(ProvisioningPhaseStep.INFORMATION_COLLECTING).lastTransitionAt(LocalDateTime.now())
                .startedAt(LocalDateTime.now())
                .build();
        given(guestServerRepository.existsById(id)).willReturn(true);
        given(provisioningProgressRepository.findByGuestServer_Id(id)).willReturn(Optional.of(progress));

        service.markFailedManually(id);

        assertThat(progress.isFailed()).isTrue();
        assertThat(progress.getCurrentStep()).isEqualTo(ProvisioningPhaseStep.INFORMATION_COLLECTING);   // 커서 유지
        // 수동 전환 표식 = 원장 instant 행(커서 step · FAILED · origin=operator) — 옛 null 컬럼 표식 대체
        verify(provisioningHistoryRecorder).recordInstant(
                any(), org.mockito.ArgumentMatchers.eq(ProvisioningPhaseStep.INFORMATION_COLLECTING),
                org.mockito.ArgumentMatchers.eq(com.example.serverprovision.execution.enums.ProvisioningStatus.FAILED),
                org.mockito.ArgumentMatchers.eq(
                        com.example.serverprovision.execution.entity.ProvisioningHistory.OPERATOR_ORIGIN_META),
                any());
        verify(eventPublisher).publishEvent(new GuestServerChangedEvent(id));
    }

    @Test
    @DisplayName("retry — 실패 신호 해제(커서 유지) + 변화 신호 발행")
    void retry_clearsFailed_andPublishes() {
        UUID id = UUID.randomUUID();
        ProvisioningProgress progress = ProvisioningProgress.builder()
                .guestServer(server(id))
                .currentStep(ProvisioningPhaseStep.INFORMATION_COLLECTING).lastTransitionAt(LocalDateTime.now())
                .startedAt(LocalDateTime.now())
                .failedAt(LocalDateTime.now())
                .build();
        given(guestServerRepository.existsById(id)).willReturn(true);
        given(provisioningProgressRepository.findByGuestServer_Id(id)).willReturn(Optional.of(progress));

        service.retry(id);

        assertThat(progress.isFailed()).isFalse();
        assertThat(progress.currentPhase()).isEqualTo(ProvisioningPhase.DIAGNOSE_LINUX);   // 커서 유지
        verify(eventPublisher).publishEvent(new GuestServerChangedEvent(id));
    }

    @Test
    @DisplayName("retry — 펌웨어 flash 실패는 차단(409) + 신호 없음")
    void retry_firmwareBlocked_rejectedWithoutSignal() {
        UUID id = UUID.randomUUID();
        ProvisioningProgress progress = ProvisioningProgress.builder()
                .guestServer(server(id))
                .currentStep(ProvisioningPhaseStep.BIOS_UPDATING).lastTransitionAt(LocalDateTime.now())
                .startedAt(LocalDateTime.now())
                .failedAt(LocalDateTime.now())
                .build();
        given(guestServerRepository.existsById(id)).willReturn(true);
        given(provisioningProgressRepository.findByGuestServer_Id(id)).willReturn(Optional.of(progress));
        given(retryPolicy.isBlocked(progress)).willReturn(true);   // 굽다가 난 실패 — 정책이 차단으로 판정

        assertThatThrownBy(() -> service.retry(id))
                .isInstanceOf(ProvisioningRetryRejectedException.class);
        verify(eventPublisher, never()).publishEvent(any());
    }

    // ==== U6 — 회수 서버 영구 삭제(purge) =====================================

    private GuestServer decommissioned(UUID id, UUID systemUUID) {
        GuestServer g = GuestServer.builder().id(id).systemUUID(systemUUID).build();
        g.decommission(LocalDateTime.now());
        return g;
    }

    @Test
    @DisplayName("purge — 회수 + suffix 일치(대소문자 관대)면 삭제하고 변경 신호를 발행한다")
    void purge_decommissionedAndMatched_deletes() {
        UUID id = UUID.randomUUID();
        UUID systemUUID = UUID.fromString("4c4c4544-0037-5a10-8054-b7c04f464331");
        GuestServer g = decommissioned(id, systemUUID);
        given(guestServerRepository.findById(id)).willReturn(Optional.of(g));

        service.purge(id, " B7C04F464331 ");   // 대문자 + 여백 — 관대 대조

        verify(guestServerRepository).delete(g);
        verify(eventPublisher).publishEvent(new GuestServerChangedEvent(id));
    }

    @Test
    @DisplayName("purge — 회수되지 않은 서버는 409 (UI 가 섹션을 안 내므로 direct POST 안전망)")
    void purge_notDecommissioned_conflict() {
        UUID id = UUID.randomUUID();
        given(guestServerRepository.findById(id)).willReturn(Optional.of(server(id)));

        assertThatThrownBy(() -> service.purge(id, "whatever"))
                .isInstanceOf(com.example.serverprovision.execution.exception.GuestServerNotDecommissionedException.class);
        verify(guestServerRepository, never()).delete(any(GuestServer.class));
    }

    @Test
    @DisplayName("purge — suffix 불일치는 400 (TypedNameMismatchException 재사용 — 확인 입력 계약)")
    void purge_suffixMismatch_badRequest() {
        UUID id = UUID.randomUUID();
        UUID systemUUID = UUID.fromString("4c4c4544-0037-5a10-8054-b7c04f464331");
        given(guestServerRepository.findById(id)).willReturn(Optional.of(decommissioned(id, systemUUID)));

        assertThatThrownBy(() -> service.purge(id, "wrong-suffix"))
                .isInstanceOf(com.example.serverprovision.global.exception.TypedNameMismatchException.class);
        verify(guestServerRepository, never()).delete(any(GuestServer.class));
    }

    @Test
    @DisplayName("purge — 없는 서버는 404")
    void purge_missing_notFound() {
        UUID id = UUID.randomUUID();
        given(guestServerRepository.findById(id)).willReturn(Optional.empty());

        assertThatThrownBy(() -> service.purge(id, "x"))
                .isInstanceOf(GuestServerNotFoundException.class);
    }

    @Test
    @DisplayName("도메인 SSOT — purgeBlockReason(비회수만 사유) · systemUUIDSuffix(마지막 '-' 다음 값)")
    void purgeDomainMethods() {
        UUID systemUUID = UUID.fromString("4c4c4544-0037-5a10-8054-b7c04f464331");
        GuestServer active = GuestServer.builder().id(UUID.randomUUID()).systemUUID(systemUUID).build();

        assertThat(active.purgeBlockReason()).isNotNull();
        assertThat(active.systemUUIDSuffix()).isEqualTo("b7c04f464331");

        active.decommission(LocalDateTime.now());
        assertThat(active.purgeBlockReason()).isNull();
    }

    @Test
    @DisplayName("도메인 SSOT — powerControlBlockReason: 회수 > 굽는 중 > 가능(null)")
    void powerControlBlockReason() {
        GuestServer g = GuestServer.builder().id(UUID.randomUUID()).systemUUID(UUID.randomUUID()).build();
        ProvisioningProgress quiet = org.mockito.Mockito.mock(ProvisioningProgress.class);
        ProvisioningProgress flashing = org.mockito.Mockito.mock(ProvisioningProgress.class);
        given(flashing.isDisruptionBlocked()).willReturn(true);

        assertThat(g.powerControlBlockReason(quiet)).isNull();
        assertThat(g.powerControlBlockReason(flashing)).contains("굽는 중");
        g.decommission(LocalDateTime.now());
        assertThat(g.powerControlBlockReason(flashing)).contains("회수된 서버");   // 회수가 우선
    }
}
