package com.example.serverprovision.execution.engine.diagnose;

import com.example.serverprovision.execution.engine.phase.PhaseExecutorRegistry;
import com.example.serverprovision.execution.engine.ProvisioningHistoryRecorder;
import com.example.serverprovision.execution.dto.response.AgentCheckinResponse;
import com.example.serverprovision.execution.dto.response.StepCloseResponse;
import com.example.serverprovision.execution.dto.response.StepOpenResponse;
import com.example.serverprovision.execution.entity.GuestServer;
import com.example.serverprovision.execution.entity.GuestServerDetail;
import com.example.serverprovision.execution.entity.ProvisioningProgress;
import com.example.serverprovision.execution.entity.ProvisioningHistory;
import com.example.serverprovision.execution.enums.AgentDirective;
import com.example.serverprovision.execution.enums.DiscoveryStage;
import com.example.serverprovision.execution.enums.GuestServerStatus;
import com.example.serverprovision.execution.enums.ProvisioningPhase;
import com.example.serverprovision.execution.enums.ProvisioningPhaseStep;
import com.example.serverprovision.execution.enums.ProvisioningStatus;
import com.example.serverprovision.execution.event.GuestServerChangedEvent;
import com.example.serverprovision.execution.exception.AgentReportRejectedException;
import com.example.serverprovision.execution.exception.GuestServerNotFoundException;
import com.example.serverprovision.execution.exception.ProvisioningHistoryNotFoundException;
import com.example.serverprovision.execution.repository.GuestServerDetailRepository;
import com.example.serverprovision.execution.repository.GuestServerRepository;
import com.example.serverprovision.execution.repository.ProvisioningProgressRepository;
import com.example.serverprovision.execution.repository.ProvisioningHistoryRepository;
import com.example.serverprovision.execution.vo.GuestToken;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 에이전트 채널(체크인 · step 보고) application service(E1-0b) — 게스트 사실 신호가 상태를
 * 전진시키는 유일한 통로(DEC-1 · DEC-2). 모든 요청은 게스트 토큰으로 인증되며 불일치는 404
 * (존재 비노출, plan Q2).
 *
 * <p>E1-2 — 지시 판정({@link #directiveFor})이 실전화되고, step 종결 보고의 소비는 phase 실행기의
 * {@code onStepClosed} 훅에 위임한다(접수 창구에 phase 분기를 쌓지 않는다). 모든 접촉은
 * {@code GuestServer.lastSeenAt} 관찰 로그를 갱신한다(DEC-32).</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AgentReportService {

    private final GuestServerRepository guestServerRepository;
    private final GuestServerDetailRepository guestServerDetailRepository;
    private final ProvisioningProgressRepository provisioningProgressRepository;
    private final ProvisioningHistoryRepository provisioningHistoryRepository;
    private final ProvisioningHistoryRecorder provisioningHistoryRecorder;
    private final PhaseExecutorRegistry phaseExecutorRegistry;
    private final ApplicationEventPublisher eventPublisher;

    /**
     * 체크인 — 진단 리눅스 기동 사실 신호. 응답 지시는 {@link #directiveFor} 가 판정한다.
     * 옛 "첫 체크인 BOOTSTRAPPING → DIAGNOSE_LINUX 전이" 특례는 ES-2 로 소멸 — 등록 seed 가
     * 이미 진단 진입 step({@code DIAGNOSTIC_BOOTING})을 가리킨다. 완주 서버의 REBOOT 는 여기로
     * 오지 못한다(게이트가 거절) — close 응답이 운반.
     */
    @Transactional
    public AgentCheckinResponse checkin(String presentedToken) {
        GuestServer server = requireByToken(presentedToken);
        ProvisioningProgress progress = requireProgress(server);
        requireProvisioning(server, progress);
        publishChanged(server);
        return new AgentCheckinResponse(directiveFor(server, progress), server.getName());
    }

    /**
     * step 시작 보고 — RUNNING 행을 열고 종료 보고가 바인딩할 행 식별자를 돌려준다(DEC-3).
     * ES-2: 커서가 보고된 step 을 따라간다(같은 phase 안 — 재부팅 후 phase 첫 step 재수행 관용).
     * 커서 phase 밖의 step 은 진짜 비정상(direct POST · stale · 변조)이라 409 로 거절한다 — 게이트가
     * 막으므로 엔티티의 phase 이탈 IllegalStateException 은 내부 버그 안전망으로만 남는다.
     */
    @Transactional
    public StepOpenResponse openStep(String presentedToken, ProvisioningPhaseStep stepCode) {
        GuestServer server = requireByToken(presentedToken);
        ProvisioningProgress progress = requireProgress(server);
        requireProvisioning(server, progress);
        if (stepCode.getPhaseType() != progress.currentPhase()) {
            throw AgentReportRejectedException.phaseMismatch(server.getId(), stepCode, progress.getCurrentStep());
        }
        LocalDateTime now = LocalDateTime.now();
        ProvisioningHistory step = provisioningHistoryRecorder.openRunning(server, stepCode, now);
        progress.positionAt(stepCode, now);
        publishChanged(server);
        return new StepOpenResponse(step.getId());
    }

    /**
     * step 종료 보고 — 행 식별자 바인딩 닫힘. 중복 종료 보고는 no-op(멱등, DEC-3).
     * FAILED 종료는 실패 신호의 실트리거 — {@code markFailed} 즉시(DEC-4).
     * <b>최초 SUCCEEDED 종결은 해당 phase 실행기의 소비 훅으로 위임</b>(E1-2 — 수집 적재 · 완주 판정이
     * 같은 트랜잭션에서 일어난다). 응답은 소비 결과까지 반영한 다음 지시(REBOOT 등)를 싣는다.
     * 타 게스트의 stepId(forging)는 404 로 존재를 숨긴다.
     */
    @Transactional
    public StepCloseResponse closeStep(String presentedToken, UUID stepId,
                                       ProvisioningStatus result, String statusMeta) {
        GuestServer server = requireByToken(presentedToken);
        ProvisioningProgress progress = requireProgress(server);

        // 게이트의 좁은 예외(E1-2): 완주는 close 트랜잭션 안에서 판정되므로, REBOOT 응답이 유실된
        // 에이전트의 재전송(이미 종결된 행의 중복 close)은 완주 상태에서도 허용해야 지시를 잃지 않는다
        // (멱등 계약). 그 외 비진행 상태(미개시·실패·회수)는 기존대로 step 조회 이전에 거절.
        GuestServerStatus status = GuestServerStatus.derive(progress, server.getDecommissionedAt());
        if (status != GuestServerStatus.PROVISIONING && status != GuestServerStatus.PROVISIONED
                && !inUnstartedDiagnosticWindow(status, progress)) {
            throw AgentReportRejectedException.notProvisioning(server.getId());
        }

        ProvisioningHistory step = provisioningHistoryRepository.findById(stepId)
                .filter(s -> s.getGuestServer().getId().equals(server.getId()))
                .orElseThrow(() -> new ProvisioningHistoryNotFoundException(stepId));

        if (status == GuestServerStatus.PROVISIONED) {
            if (step.getFinishedAt() == null) {
                // 완주 후 새 step 을 닫으려는 시도는 비정상 — 게이트 원칙 유지.
                throw AgentReportRejectedException.notProvisioning(server.getId());
            }
            publishChanged(server);   // 접촉(lastSeenAt)은 이 no-op 경로에서도 갱신됐다
            return new StepCloseResponse(directiveFor(server, progress));   // no-op + REBOOT 재계산
        }

        LocalDateTime now = LocalDateTime.now();
        boolean closed = step.close(result, statusMeta, now);
        if (closed && result == ProvisioningStatus.FAILED) {
            // 실패 지점 = 커서(ES-2 D-5). 재시작 등으로 커서가 다른 step 에 가 있으면 같은 phase 안에서
            // 보고 행의 step 으로 되돌려 "커서 = 실패 지점" 을 확정한다. phase 가 다른 지연 close(희귀)는
            // 커서를 움직이지 않고 실패 신호만 남긴다 — pre-position 된 커서가 이미 다음 목표를 가리킨다.
            if (step.getStepCode().getPhaseType() == progress.currentPhase()) {
                progress.positionAt(step.getStepCode(), now);
            } else {
                log.warn("커서 phase 밖 실패 close — 커서 유지 : guestServerId={}, 보고 step={}, 커서={}",
                        server.getId(), step.getStepCode(), progress.getCurrentStep());
            }
            // 가드가 미실패·미종단을 이미 보장하므로 markFailed 는 곧바로 안전하다.
            progress.markFailed(now);
            log.warn("게스트 실패 보고 — 실패 신호 기록 : guestServerId={}, step={}",
                    server.getId(), step.getStepCode());
        }
        if (closed && result == ProvisioningStatus.SUCCEEDED) {
            // phase 소비 위임(E1-2) — 접수 창구의 분기 증식 대신 실행기 훅(DEC-6 확장 자리).
            phaseExecutorRegistry.find(step.getStepCode().getPhaseType())
                    .ifPresent(executor -> executor.onStepClosed(server, progress, step));
        }
        publishChanged(server);
        return new StepCloseResponse(directiveFor(server, progress));
    }

    /**
     * 실시간 스트림 신호(S7) — 게이트를 통과한 모든 에이전트 접촉은 최소 lastSeenAt 이 변한다.
     * 전이·원장·소비 훅의 적재도 같은 트랜잭션이므로 접수 메서드 말미 1회 발행으로 충분하다.
     * AFTER_COMMIT 리스너가 수신하므로 게이트 거절(롤백) 시엔 신호도 함께 사라진다.
     */
    private void publishChanged(GuestServer server) {
        eventPublisher.publishEvent(new GuestServerChangedEvent(server.getId()));
    }

    /**
     * 지시 판정 진입점(E1-2 · ES-1 → E3.5-1 다형화, 0-3 결정 D-2) — 공통 규칙 둘만 갖고 내용은
     * 커서 phase 실행기의 {@code directiveFor} 에 위임한다:
     * ① 종단(isCompleted) · 실패(isFailed) → REBOOT. 종단 커서는 마지막 수행 step 에 멈춰 있어(ES-2)
     *    실행기에게 물으면 그 phase 의 평시 답이 나오고, 실패 게스트에게 작업을 재지시하면 다음 보고가
     *    게이트(409)에 막혀 지시만 낭비된다 — 실패 화면(iPXE 대기 + HF10 신원 줄)으로 보내는 것이 정직하다.
     *    둘 다 phase 무관 공통 판정이라 여기 남는다(옛 ordinal 비교가 실패 경로에 REBOOT 를 주던 동작의 보존).
     * ② 실행기 미등록 phase(HOLD) → REBOOT. 게스트가 진단 리눅스에 남을 이유가 없다.
     * 옛 "커서가 진단 이후로 전진 → REBOOT" 상수 비교는 인터페이스 기본값(REBOOT)으로 일반화됐다 —
     * 서버 주도 phase 실행기는 override 없이 같은 답을 낸다. 재수신(응답 유실 재체크인)은 무해 —
     * 소비 훅의 적재가 최신값 덮기라 멱등이다.
     */
    private AgentDirective directiveFor(GuestServer server, ProvisioningProgress progress) {
        if (progress.isCompleted() || progress.isFailed()) {
            return AgentDirective.REBOOT;
        }
        return phaseExecutorRegistry.find(progress.currentPhase())
                .map(executor -> executor.directiveFor(server, progress))
                .orElse(AgentDirective.REBOOT);
    }

    /**
     * 에이전트 보고 게이트(HF) — 서버가 실제 프로비저닝 중일 때만 허용한다. "프로비저닝 중" 은
     * {@link GuestServerStatus#derive}({@code PROVISIONING}) 와 동일 조건(개시됨 + 미회수 + 미실패 + 미종단)
     * 이라 별도 기준을 만들지 않고 재사용한다. 개시 게이트는 {@code /boot} 가 정상 흐름을 막고, 이 가드는
     * 그것을 우회하는 direct POST(하네스 · 외부 변조)의 안전망이다.
     */
    private void requireProvisioning(GuestServer server, ProvisioningProgress progress) {
        GuestServerStatus status = GuestServerStatus.derive(progress, server.getDecommissionedAt());
        if (status != GuestServerStatus.PROVISIONING
                && !inUnstartedDiagnosticWindow(status, progress)) {
            throw AgentReportRejectedException.notProvisioning(server.getId());
        }
    }

    /**
     * 미개시 진단 창(R13) — 등록 즉시 진단 phase 가 자동 진행되므로, 미개시(REGISTERED)라도 커서가
     * 진단 phase 면 에이전트 보고를 수리한다. 진단 밖 phase 의 미개시 보고는 여전히 비정상(도메인
     * 가드가 미개시 커서를 진단 밖으로 못 옮기므로 direct POST · 변조 신호)이라 기존대로 거절된다.
     * checkin · openStep 의 게이트와 closeStep 의 인라인 게이트가 같은 판정을 공유한다.
     */
    private boolean inUnstartedDiagnosticWindow(GuestServerStatus status, ProvisioningProgress progress) {
        return status == GuestServerStatus.REGISTERED
                && progress.currentPhase() == ProvisioningPhase.DIAGNOSE_LINUX;
    }

    private GuestServer requireByToken(String presented) {
        if (presented == null || presented.isBlank()) {
            throw GuestServerNotFoundException.byToken();
        }
        GuestServer server = guestServerRepository.findByGuestToken(new GuestToken(presented))
                .orElseThrow(GuestServerNotFoundException::byToken);
        // 접촉 관찰 로그(DEC-32). 게이트 거절(409) 시엔 롤백으로 함께 사라지지만, 그런 게스트도
        // /boot 폴링은 계속 하므로(BootService 가 별도 트랜잭션에서 갱신) 관찰 공백은 없다.
        server.touchSeen(LocalDateTime.now());
        return server;
    }

    private ProvisioningProgress requireProgress(GuestServer server) {
        // progress 는 등록 트랜잭션이 1:1 로 seed 한다(U1 §D6) — 부재는 데이터 손상이므로 500 이 정직하다.
        return provisioningProgressRepository.findByGuestServer_Id(server.getId())
                .orElseThrow(() -> new IllegalStateException(
                        "provisioning_progress 1:1 불변 위반 — 등록 seed 누락. guestServerId=" + server.getId()));
    }
}
