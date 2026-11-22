package com.example.serverprovision.execution.engine.osinstall;

import com.example.serverprovision.execution.dto.response.OsInstallCompletionResponse;
import com.example.serverprovision.execution.engine.phase.PhaseCursorAdvancer;
import com.example.serverprovision.execution.entity.GuestServer;
import com.example.serverprovision.execution.entity.ProvisioningHistory;
import com.example.serverprovision.execution.entity.ProvisioningProgress;
import com.example.serverprovision.execution.enums.GuestServerStatus;
import com.example.serverprovision.execution.enums.ProvisioningPhase;
import com.example.serverprovision.execution.enums.ProvisioningPhaseStep;
import com.example.serverprovision.execution.event.GuestServerChangedEvent;
import com.example.serverprovision.execution.exception.AgentReportRejectedException;
import com.example.serverprovision.execution.repository.ProvisioningProgressRepository;
import com.example.serverprovision.execution.service.GuestPrincipalLoader;
import com.example.serverprovision.global.security.springsecurity.guest.GuestPrincipal;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

/**
 * 설치된 OS 가 보내는 완료 보고의 계열 공통 흐름이다. 순서는 이 클래스가 정하고, 계열은 행을 닫는 법과 닫은 뒤의 뒷정리만 정한다.
 * 중복 보고는 200 no-op 이다. 응답을 받지 못한 게스트가 다시 보내도 409 로 끝나지 않게 하려는 것이다.
 *
 * @param <Q> 계열의 보고 본문
 */
@Slf4j
public abstract class OsInstallCompletionService<Q> {

    private final GuestPrincipalLoader guestPrincipalLoader;
    private final ProvisioningProgressRepository progressRepository;
    private final PhaseCursorAdvancer cursorAdvancer;
    private final ApplicationEventPublisher eventPublisher;

    protected OsInstallCompletionService(GuestPrincipalLoader guestPrincipalLoader,
                                         ProvisioningProgressRepository progressRepository,
                                         PhaseCursorAdvancer cursorAdvancer, ApplicationEventPublisher eventPublisher) {
        this.guestPrincipalLoader = guestPrincipalLoader;
        this.progressRepository = progressRepository;
        this.cursorAdvancer = cursorAdvancer;
        this.eventPublisher = eventPublisher;
    }

    /** 이 계열의 원장. */
    protected abstract OsInstallLedger ledger();

    /** 열린 서빙 행을 보고 내용으로 닫는다. 돌려준 문자열은 로그 한 줄에 그대로 붙는다. */
    protected abstract String close(GuestServer server, ProvisioningHistory row, Q report, LocalDateTime now);

    /** 닫은 뒤의 뒷정리(서빙 토큰 회수 등). */
    protected abstract void afterClosed(GuestServer server, ProvisioningHistory row);

    /** 커서를 옮긴 뒤 같은 보고로 끝낼 수 있는 다음 phase 를 닫는다. 기본은 없다(RHEL 의 OS 후처리, E4-1-b-7). */
    protected void afterAdvanced(GuestServer server, ProvisioningProgress progress, Q report, LocalDateTime now) {
    }

    /**
     * 순서는 신원 로드, 중복이면 no-op, 진행 상태 · 커서 · 열린 행 확인(아니면 409), 닫기, 뒷정리, 커서 전진 또는 종단,
     * 같은 보고로 끝나는 다음 phase, 변경 이벤트다.
     * 신원이 없으면 500 이다. 게스트 체인이 이미 인증했으므로 데이터 손상이다.
     */
    @Transactional
    public OsInstallCompletionResponse complete(GuestPrincipal guest, Q report) {
        GuestServer server = guestPrincipalLoader.require(guest);
        ProvisioningProgress progress = requireProgress(server);
        UUID id = server.getId();
        Optional<ProvisioningHistory> running = ledger().latestRunning(id);
        if (running.isEmpty() && alreadyCompleted(id)) {
            publishChanged(id);
            log.info("[{}] {} — 완료 보고 중복(no-op)", ledger().origin().logTag(), id);
            return responseOf(false, progress);
        }
        ProvisioningHistory row = admit(id, progress, server, running);
        LocalDateTime now = LocalDateTime.now();
        String summary = close(server, row, report, now);
        afterClosed(server, row);
        cursorAdvancer.advanceOrComplete(progress, id, now);
        afterAdvanced(server, progress, report, now);
        publishChanged(id);
        log.info("[{}] {} — 설치 완료 보고 : {}, {}", ledger().origin().logTag(), id, summary,
                progress.isCompleted() ? "종단" : progress.isFailed() ? "실패 " + progress.currentPhase()
                        : "다음 phase " + progress.currentPhase());
        return responseOf(true, progress);
    }

    private boolean alreadyCompleted(UUID guestServerId) {
        return ledger().latestOf(guestServerId).filter(ledger()::isCompletedRow).isPresent();
    }

    /** 보고를 받을 수 있는 상태인지 본다. 프로비저닝 중이고 커서가 OS 설치이며 열린 행이 있어야 한다. */
    private ProvisioningHistory admit(UUID id, ProvisioningProgress progress, GuestServer server,
                                      Optional<ProvisioningHistory> running) {
        if (GuestServerStatus.derive(progress, server.getDecommissionedAt()) != GuestServerStatus.PROVISIONING) {
            throw AgentReportRejectedException.notProvisioning(id);
        }
        if (progress.currentPhase() != ProvisioningPhase.OS_INSTALLING) {
            throw AgentReportRejectedException.phaseMismatch(id, ProvisioningPhaseStep.OS_INSTALLING, progress.getCurrentStep());
        }
        return running.orElseThrow(() -> AgentReportRejectedException.noOpenStep(id, ProvisioningPhaseStep.OS_INSTALLING));
    }

    /** 다음 phase 는 아직 갈 곳이 있을 때만 싣는다. 같은 보고로 실패하면 커서가 실패 지점에 남으므로 null 이다(E4-1-b-7 CP5 O-2). */
    private static OsInstallCompletionResponse responseOf(boolean closed, ProvisioningProgress progress) {
        return new OsInstallCompletionResponse(closed, progress.isCompleted(),
                progress.isCompleted() || progress.isFailed() ? null : progress.currentPhase());
    }

    private void publishChanged(UUID guestServerId) {
        eventPublisher.publishEvent(new GuestServerChangedEvent(guestServerId));
    }

    private ProvisioningProgress requireProgress(GuestServer server) {
        return progressRepository.findByGuestServer_Id(server.getId())
                .orElseThrow(() -> new IllegalStateException(
                        "provisioning_progress 1:1 불변 위반 — 등록 seed 누락. guestServerId=" + server.getId()));
    }
}
