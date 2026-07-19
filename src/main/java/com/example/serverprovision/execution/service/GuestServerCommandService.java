package com.example.serverprovision.execution.service;

import com.example.serverprovision.execution.dto.request.UpdateGuestServerRequest;
import com.example.serverprovision.execution.entity.GuestServer;
import com.example.serverprovision.execution.entity.ProvisioningProgress;
import com.example.serverprovision.execution.event.GuestServerChangedEvent;
import com.example.serverprovision.execution.exception.GuestServerNotFoundException;
import com.example.serverprovision.execution.exception.ProvisioningMarkFailedRejectedException;
import com.example.serverprovision.execution.exception.ProvisioningRetryRejectedException;
import com.example.serverprovision.execution.exception.ProvisioningStartRejectedException;
import com.example.serverprovision.execution.repository.GuestServerRepository;
import com.example.serverprovision.execution.repository.ProvisioningProgressRepository;
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
    private final ApplicationEventPublisher eventPublisher;

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
     */
    @Transactional
    public void decommission(UUID id) {
        GuestServer server = guestServerRepository.findById(id)
                .orElseThrow(() -> new GuestServerNotFoundException(id));
        server.decommission(LocalDateTime.now());
        publishChanged(id);
    }

    /**
     * 프로비저닝 개시(E1-0a, DEC-26) — startedAt 기록. 게스트 동작(대기 해제)은 E1-0b 의 dispatch 가 소비한다.
     * 가드 판정은 뷰의 버튼 노출과 같은 SSOT({@link ProvisioningProgress#isStartableWith})를 쓰고,
     * 거절 사유(회수/재개시)는 메시지 구분용으로만 다시 본다.
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
            throw server.getDecommissionedAt() != null
                    ? ProvisioningStartRejectedException.decommissioned(id)
                    : ProvisioningStartRejectedException.alreadyStarted(id);
        }
        progress.start(LocalDateTime.now());
        publishChanged(id);
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
        progress.markFailedManually(LocalDateTime.now());
        publishChanged(id);
    }

    /**
     * 운영자 재시도(E1-2, DEC-4) — 실패 신호 해제(전진 가드의 유일한 명시 예외). 커서는 유지되어
     * 다음 /boot 폴링이 실패 phase 의 스크립트를 재발급한다. 펌웨어 flash 실패는 차단
     * (판정 SSOT = {@link ProvisioningProgress#isRetryBlocked} — UI disabled + tooltip 과 공유).
     */
    @Transactional
    public void retry(UUID id) {
        ProvisioningProgress progress = requireProgress(id);
        if (!progress.isFailed()) {
            throw ProvisioningRetryRejectedException.notFailed(id);
        }
        if (progress.isRetryBlocked()) {
            throw ProvisioningRetryRejectedException.firmwareBlocked(id, progress.getFailedStepCode());
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
