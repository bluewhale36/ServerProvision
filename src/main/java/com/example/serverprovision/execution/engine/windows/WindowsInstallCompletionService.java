package com.example.serverprovision.execution.engine.windows;

import com.example.serverprovision.execution.dto.request.WindowsInstallCompletionRequest;
import com.example.serverprovision.execution.dto.response.WindowsInstallCompletionResponse;
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
import com.example.serverprovision.execution.service.GuestTokenAuthenticator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

/**
 * Windows 설치 완료 보고의 소비(E4-1-a-4 D-4 · D-8) — 첫 로그온 스크립트의 한 번 보고가 열린 서빙 행을 SUCCEEDED 로 닫고
 * (서빙 meta 보존 + 완료 meta), 설치 번들 토큰을 회수하고, 커서를 다음 소유 phase 로 옮기거나 종단한다
 * ({@link PhaseCursorAdvancer}). 중복 보고는 200 no-op(멱등) — 응답이 유실된 스크립트의 재시도가 409 로 끝나지 않게.
 * 진단 창구의 {@code closeStep} 을 재사용하지 않는 이유는 게스트가 행 식별자를 모르기 때문이다(서빙 행은 렌더 뒤에 열린다).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WindowsInstallCompletionService {

    private final GuestTokenAuthenticator authenticator;
    private final ProvisioningProgressRepository progressRepository;
    private final WindowsInstallLedger ledger;
    private final WindowsInstallTokenRegistry tokenRegistry;
    private final PhaseCursorAdvancer cursorAdvancer;
    private final ApplicationEventPublisher eventPublisher;

    /**
     * 순서 — 인증(404) → 열린 행이 없고 최신 행이 완료 행이면 no-op(200) → 게이트(프로비저닝 중 · 커서 OS 설치 phase, 아니면 409)
     * → 열린 행 없음 409 → 닫기 · 회수 · 전진/종단 · 이벤트.
     */
    @Transactional
    public WindowsInstallCompletionResponse complete(String presentedToken, WindowsInstallCompletionRequest report) {
        GuestServer server = authenticator.requireByToken(presentedToken);
        ProvisioningProgress progress = requireProgress(server);
        UUID id = server.getId();
        Optional<ProvisioningHistory> running = ledger.latestRunning(id);
        if (running.isEmpty() && ledger.latestOf(id).filter(ledger::isCompletedRow).isPresent()) {
            publishChanged(server);
            log.info("[wininstall] {} — 완료 보고 중복(no-op)", id);
            return new WindowsInstallCompletionResponse(false, progress.isCompleted(), nextPhaseOf(progress));
        }
        GuestServerStatus status = GuestServerStatus.derive(progress, server.getDecommissionedAt());
        if (status != GuestServerStatus.PROVISIONING) {
            throw AgentReportRejectedException.notProvisioning(id);
        }
        if (progress.currentPhase() != ProvisioningPhase.OS_INSTALLING) {
            throw AgentReportRejectedException.phaseMismatch(id, ProvisioningPhaseStep.OS_INSTALLING, progress.getCurrentStep());
        }
        ProvisioningHistory row = running.orElseThrow(
                () -> AgentReportRejectedException.noOpenStep(id, ProvisioningPhaseStep.OS_INSTALLING));

        LocalDateTime now = LocalDateTime.now();
        ledger.closeSucceeded(row, new WindowsInstallLedger.Completion(report.computerName(), report.osVersion(),
                report.driversAdded(), report.problemDeviceCount(), report.problemDevicesOrEmpty(),
                report.setupCompleteLogTail()), now);
        tokenRegistry.revoke(id);   // 완료한 게스트의 응답 파일이 열린 채 남지 않게(-3 인계 ②)
        cursorAdvancer.advanceOrComplete(progress, id, now);
        publishChanged(server);
        log.info("[wininstall] {} — 설치 완료 보고 : computerName={}, drivers={}, problemDevices={}, {}",
                id, report.computerName(), report.driversAdded(), report.problemDeviceCount(),
                progress.isCompleted() ? "종단" : "다음 phase " + progress.currentPhase());
        return new WindowsInstallCompletionResponse(true, progress.isCompleted(), nextPhaseOf(progress));
    }

    private static ProvisioningPhase nextPhaseOf(ProvisioningProgress progress) {
        return progress.isCompleted() ? null : progress.currentPhase();
    }

    private void publishChanged(GuestServer server) {
        eventPublisher.publishEvent(new GuestServerChangedEvent(server.getId()));
    }

    private ProvisioningProgress requireProgress(GuestServer server) {
        return progressRepository.findByGuestServer_Id(server.getId())
                .orElseThrow(() -> new IllegalStateException(
                        "provisioning_progress 1:1 불변 위반 — 등록 seed 누락. guestServerId=" + server.getId()));
    }
}
