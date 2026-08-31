package com.example.serverprovision.execution.service;

import com.example.serverprovision.execution.dto.request.UpdateGuestServerRequest;
import com.example.serverprovision.execution.engine.phase.PhaseCursorAdvancer;
import com.example.serverprovision.execution.entity.GuestServer;
import com.example.serverprovision.execution.engine.ProvisioningHistoryRecorder;
import com.example.serverprovision.execution.entity.ProvisioningHistory;
import com.example.serverprovision.execution.enums.ProvisioningPhaseStep;
import com.example.serverprovision.execution.enums.ProvisioningStatus;
import com.example.serverprovision.execution.entity.ProvisioningProgress;
import com.example.serverprovision.execution.event.GuestServerChangedEvent;
import com.example.serverprovision.execution.exception.DisruptiveActionRejectedException;
import com.example.serverprovision.execution.exception.GuestServerNotFoundException;
import com.example.serverprovision.execution.exception.GuestServerNotDecommissionedException;
import com.example.serverprovision.execution.exception.ProvisioningMarkFailedRejectedException;
import com.example.serverprovision.execution.exception.ProvisioningRetryRejectedException;
import com.example.serverprovision.execution.exception.ProvisioningStartRejectedException;
import com.example.serverprovision.execution.repository.GuestServerRepository;
import com.example.serverprovision.execution.repository.ProvisioningProgressRepository;
import com.example.serverprovision.global.exception.TypedNameMismatchException;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 게스트 서버 상태 변경(인라인 수정 · 회수 · 프로비저닝 개시) application service (U1 §D11).
 * 운영자 입력 4필드는 모두 단일 테이블 guest_server(§D1). 유니크(name / serial_number) 충돌 여부는 컨트롤러가
 * boolean 질의로 미리 확인해 BindingResult 인라인 표시한다(예외=프로그램 예외 전용).
 */
@Service
@RequiredArgsConstructor
public class GuestServerCommandService {

    private final GuestServerRepository guestServerRepository;
    private final ProvisioningProgressRepository provisioningProgressRepository;
    private final ProvisioningHistoryRecorder provisioningHistoryRecorder;
    private final RetryPolicy retryPolicy;   // 재시도 차단 판정 — 화면 노출과 같은 지점
    private final PhaseCursorAdvancer phaseCursorAdvancer;   // R13 — 개시 시 유보된 완주 판정의 소급 집행
    private final ApplicationEventPublisher eventPublisher;
    private final com.example.serverprovision.execution.engine.WorkerObservations workerObservations;   // E2-4 O-3

    @Transactional(readOnly = true)
    public boolean isNameTakenByOther(UUID id, String name) {
        return guestServerRepository.existsByNameAndIdNot(name, id);
    }

    @Transactional(readOnly = true)
    public boolean isSerialTakenByOther(UUID id, String serialNumber) {
        return guestServerRepository.existsBySerialNumberAndIdNot(serialNumber, id);
    }

    /**
     * 이름·사내 모델명·사내 시리얼·메모(모두 guest_server)를 한 트랜잭션으로 갱신. 빈 입력은 null 로 정규화.
     */
    @Transactional
    public void update(UUID id, UpdateGuestServerRequest req) {
        GuestServer server = guestServerRepository.findById(id)
                .orElseThrow(() -> new GuestServerNotFoundException(id));
        server.updateOperatorInfo(
                blankToNull(req.name()),
                blankToNull(req.modelName()),
                blankToNull(req.serialNumber()),
                blankToNull(req.memo()));
        publishChanged(id);
    }

    /**
     * 서버 회수 — decommissioned_at 기록(멱등). 운영 상태는 이 마커에서 도출(§D4).
     * 펌웨어를 굽는 중에는 거절한다(R13 후속) — 가드는 뷰 차단과 같은 SSOT.
     */
    @Transactional
    public void decommission(UUID id) {
        GuestServer server = guestServerRepository.findById(id)
                .orElseThrow(() -> new GuestServerNotFoundException(id));
        provisioningProgressRepository.findByGuestServer_Id(id)
                .filter(ProvisioningProgress::isDisruptionBlocked)
                .ifPresent(p -> { throw new DisruptiveActionRejectedException(id); });
        // 회수 직전 인메모리 관측 파괴(E2-4 O-3 확정) — 재투입 화면이 지난 집행의 관측으로 거짓을 말하지 않게.
        workerObservations.clear(id);
        server.decommission(LocalDateTime.now());
        publishChanged(id);
    }

    /**
     * 회수 서버 영구 삭제(U6 D-5) — 자식 행 정리는 DB 의 ON DELETE CASCADE(6개 FK 실측) 소관이다.
     * 가드 SSOT: 노출 판정과 같은 {@code GuestServer.purgeBlockReason}(비회수 = 409) +
     * {@code systemUUIDSuffix} 대조(불일치 = 400, {@code TypedNameMismatchException} 재사용 —
     * "확인 입력이 기대값과 불일치" 계약이 같다). 정상 흐름은 UI 가 막으므로 direct POST 안전망.
     */
    @Transactional
    public void purge(UUID id, String typedSuffix) {
        GuestServer server = guestServerRepository.findById(id)
                .orElseThrow(() -> new GuestServerNotFoundException(id));
        if (server.purgeBlockReason() != null) {
            throw new GuestServerNotDecommissionedException(id);
        }
        String expected = server.systemUUIDSuffix();
        if (typedSuffix == null || !expected.equalsIgnoreCase(typedSuffix.trim())) {
            throw new TypedNameMismatchException(expected, typedSuffix);
        }
        guestServerRepository.delete(server);
        publishChanged(id);   // SSE 는 fetch 재적재 방식이라 삭제 통지로도 유효하다
    }

    /**
     * 프로비저닝 개시(E1-0a, DEC-26 → R13 의미 이동) — startedAt 기록. 게스트 동작(대기 해제)은
     * E1-0b 의 dispatch 와 에이전트 지시 판정이 소비한다.
     * 가드 판정은 뷰의 버튼 노출과 같은 SSOT({@link ProvisioningProgress#isStartableWith})를 쓰고,
     * 거절 사유(회수/실패/재개시)는 메시지 구분용으로만 다시 본다.
     *
     * <p>R13 — 진단(수집)은 개시 없이 자동 진행되고 완주 판정은 유보된다. 개시 시점에 커서가
     * 수집 완주 표식({@code INFORMATION_PERSISTING} — close 소비가 세운 서버 판정 instant)이면
     * 유보된 판정을 여기서 소급 집행한다: 할당 보유 phase 로 전진하거나(무할당이면) 종단.
     * 게스트는 진단 리눅스 안에서 WAIT 루프로 상주하므로 다음 체크인의 지시 재계산이 이 결과를
     * REBOOT 로 나른다.</p>
     */
    @Transactional
    public void startProvisioning(UUID id) {
        GuestServer server = guestServerRepository.findById(id)
                .orElseThrow(() -> new GuestServerNotFoundException(id));
        // progress 는 등록 트랜잭션이 1:1 로 seed 한다(U1 §D6) — 부재는 데이터 손상이므로 500 이 정직하다.
        ProvisioningProgress progress = provisioningProgressRepository.findByGuestServer_Id(id)
                .orElseThrow(() -> new IllegalStateException(
                        "provisioning_progress 1:1 불변 위반 — 등록 seed 누락. guestServerId=" + id));

        if (!progress.isStartableWith(server.getDecommissionedAt())) {
            throw rejectionOf(server, progress, id);
        }
        LocalDateTime now = LocalDateTime.now();
        progress.start(now);
        if (progress.getCurrentStep() == ProvisioningPhaseStep.INFORMATION_PERSISTING) {
            phaseCursorAdvancer.advanceOrComplete(progress, id, now);
        }
        publishChanged(id);
    }

    /** 개시 거절 사유 구분 — 판정은 이미 isStartableWith 가 끝냈고 여기는 메시지 선택만. */
    private ProvisioningStartRejectedException rejectionOf(GuestServer server, ProvisioningProgress progress, UUID id) {
        if (server.getDecommissionedAt() != null) {
            return ProvisioningStartRejectedException.decommissioned(id);
        }
        if (progress.isFailed()) {
            return ProvisioningStartRejectedException.failed(id);
        }
        return ProvisioningStartRejectedException.alreadyStarted(id);
    }

    /**
     * 운영자 수동 실패 전환(E1-2, DEC-4) — 무보고 침묵(게스트 침묵 · 전원 단절, UC-4)을 운영자 판단으로
     * 실패 처리한다. 가드 판정은 뷰 버튼 노출과 같은 SSOT({@link ProvisioningProgress#isManualFailable}).
     */
    @Transactional
    public void markFailedManually(UUID id) {
        ProvisioningProgress progress = requireProgress(id);
        GuestServer server = progress.getGuestServer();
        if (!progress.isManualFailable(server.getDecommissionedAt())) {
            throw ProvisioningMarkFailedRejectedException.notProvisioning(id);
        }
        LocalDateTime now = LocalDateTime.now();
        progress.markFailedManually(now);
        // 수동 전환 표식 = 원장 instant 행(ES-2 D-5 — 옛 failed_step_code null 표식 대체). 같은 now 를
        // 쓰므로 상세 응답의 파생 판독(failedAt = finishedAt 짝)이 이 행을 정확히 집는다.
        provisioningHistoryRecorder.recordInstant(server, progress.getCurrentStep(),
                ProvisioningStatus.FAILED, ProvisioningHistory.OPERATOR_ORIGIN_META, now);
        publishChanged(id);
    }

    /**
     * 운영자 재시도(E1-2, DEC-4) — 실패 신호 해제(전진 가드의 유일한 명시 예외). 커서는 유지되어
     * 다음 /boot 폴링이 실패 phase 의 스크립트를 재발급한다. 펌웨어 flash 실패는 차단
     * (판정 SSOT = {@link RetryPolicy} — UI disabled + tooltip 과 공유. 굽다가 난 실패만 막고,
     * 자원 결손 시한 만료는 자원을 되살린 뒤 다시 시도할 수 있다).
     */
    @Transactional
    public void retry(UUID id) {
        ProvisioningProgress progress = requireProgress(id);
        if (!progress.isFailed()) {
            throw ProvisioningRetryRejectedException.notFailed(id);
        }
        if (retryPolicy.isBlocked(progress)) {
            throw ProvisioningRetryRejectedException.firmwareBlocked(id, progress.getCurrentStep());
        }
        progress.clearFailed(LocalDateTime.now());
        publishChanged(id);
    }

    /** 실시간 스트림 신호(S7) — 운영자 액션은 다른 탭·다른 운영자 화면의 동기화 대상. AFTER_COMMIT 수신. */
    private void publishChanged(UUID id) {
        eventPublisher.publishEvent(new GuestServerChangedEvent(id));
    }

    private ProvisioningProgress requireProgress(UUID id) {
        if (!guestServerRepository.existsById(id)) {
            throw new GuestServerNotFoundException(id);
        }
        // progress 는 등록 트랜잭션이 1:1 로 seed 한다(U1 §D6) — 부재는 데이터 손상이므로 500 이 정직하다.
        return provisioningProgressRepository.findByGuestServer_Id(id)
                .orElseThrow(() -> new IllegalStateException(
                        "provisioning_progress 1:1 불변 위반 — 등록 seed 누락. guestServerId=" + id));
    }

    private static String blankToNull(String value) {
        return (value == null || value.isBlank()) ? null : value.trim();
    }
}
