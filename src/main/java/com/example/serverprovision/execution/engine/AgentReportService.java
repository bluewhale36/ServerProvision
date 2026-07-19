package com.example.serverprovision.execution.engine;

import com.example.serverprovision.execution.dto.response.AgentCheckinResponse;
import com.example.serverprovision.execution.dto.response.StepCloseResponse;
import com.example.serverprovision.execution.dto.response.StepOpenResponse;
import com.example.serverprovision.execution.entity.GuestServer;
import com.example.serverprovision.execution.entity.GuestServerDetail;
import com.example.serverprovision.execution.entity.ProvisioningProgress;
import com.example.serverprovision.execution.entity.SetupStep;
import com.example.serverprovision.execution.enums.AgentDirective;
import com.example.serverprovision.execution.enums.DiscoveryStage;
import com.example.serverprovision.execution.enums.GuestServerStatus;
import com.example.serverprovision.execution.enums.ProvisioningPhase;
import com.example.serverprovision.execution.enums.ProvisioningPhaseStep;
import com.example.serverprovision.execution.enums.ProvisioningStatus;
import com.example.serverprovision.execution.exception.AgentReportRejectedException;
import com.example.serverprovision.execution.exception.GuestServerNotFoundException;
import com.example.serverprovision.execution.exception.SetupStepNotFoundException;
import com.example.serverprovision.execution.repository.GuestServerDetailRepository;
import com.example.serverprovision.execution.repository.GuestServerRepository;
import com.example.serverprovision.execution.repository.ProvisioningProgressRepository;
import com.example.serverprovision.execution.repository.SetupStepRepository;
import com.example.serverprovision.execution.vo.GuestToken;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
    private final SetupStepRepository setupStepRepository;
    private final SetupStepRecorder setupStepRecorder;
    private final PhaseExecutorRegistry phaseExecutorRegistry;

    /**
     * 체크인 — 진단 리눅스 기동 사실 신호. <b>첫 체크인(개시됨 + 커서 BOOTSTRAPPING)만</b>
     * DIAGNOSE_LINUX 로 전이하고(수신 트랜잭션 내 즉시, DEC-2), 응답 지시는 {@link #directiveFor} 가
     * 판정한다. 완주 서버의 REBOOT 는 여기로 오지 못한다(게이트가 거절) — close 응답이 운반.
     */
    @Transactional
    public AgentCheckinResponse checkin(String presentedToken) {
        GuestServer server = requireByToken(presentedToken);
        ProvisioningProgress progress = requireProgress(server);
        requireProvisioning(server, progress);

        // 가드 통과 = 개시됨 + 미회수 + 미실패 + 미종단. 남는 판단은 첫 체크인(커서 BOOTSTRAPPING) 전이뿐.
        if (progress.getCurrentPhase() == ProvisioningPhase.BOOTSTRAPPING) {
            progress.advanceTo(ProvisioningPhase.DIAGNOSE_LINUX, LocalDateTime.now());
            log.info("게스트 첫 체크인 — 진단 진입 : guestServerId={}", server.getId());
        }
        return new AgentCheckinResponse(directiveFor(server, progress), server.getName());
    }

    /** step 시작 보고 — RUNNING 행을 열고 종료 보고가 바인딩할 행 식별자를 돌려준다(DEC-3). */
    @Transactional
    public StepOpenResponse openStep(String presentedToken, ProvisioningPhaseStep stepCode) {
        GuestServer server = requireByToken(presentedToken);
        requireProvisioning(server, requireProgress(server));
        SetupStep step = setupStepRecorder.openRunning(server, stepCode, LocalDateTime.now());
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
        if (status != GuestServerStatus.PROVISIONING && status != GuestServerStatus.PROVISIONED) {
            throw AgentReportRejectedException.notProvisioning(server.getId());
        }

        SetupStep step = setupStepRepository.findById(stepId)
                .filter(s -> s.getGuestServer().getId().equals(server.getId()))
                .orElseThrow(() -> new SetupStepNotFoundException(stepId));

        if (status == GuestServerStatus.PROVISIONED) {
            if (step.getFinishedAt() == null) {
                // 완주 후 새 step 을 닫으려는 시도는 비정상 — 게이트 원칙 유지.
                throw AgentReportRejectedException.notProvisioning(server.getId());
            }
            return new StepCloseResponse(directiveFor(server, progress));   // no-op + REBOOT 재계산
        }

        boolean closed = step.close(result, statusMeta, LocalDateTime.now());
        if (closed && result == ProvisioningStatus.FAILED) {
            // 가드가 미실패·미종단을 이미 보장하므로 markFailed 는 곧바로 안전하다.
            progress.markFailed(step.getStepCode(), LocalDateTime.now());
            log.warn("게스트 실패 보고 — 실패 신호 기록 : guestServerId={}, step={}",
                    server.getId(), step.getStepCode());
        }
        if (closed && result == ProvisioningStatus.SUCCEEDED) {
            // phase 소비 위임(E1-2) — 접수 창구의 분기 증식 대신 실행기 훅(DEC-6 확장 자리).
            phaseExecutorRegistry.find(step.getStepCode().getPhaseType())
                    .ifPresent(executor -> executor.onStepClosed(server, progress, step));
        }
        return new StepCloseResponse(directiveFor(server, progress));
    }

    /**
     * 지시 판정 단일 지점(E1-2) — 우선순위 규약:
     * ① 완주 → REBOOT(진단 리눅스를 떠나 iPXE 폴링으로 — dispatch 4행 이분이 받는다)
     * ② 커서 진단 + 미수집(IPXE_REGISTERED) → COLLECT
     * ③ 그 외 → WAIT.
     * COLLECT 재수신(응답 유실 재체크인)은 무해 — 소비 훅의 적재가 최신값 덮기라 멱등이다.
     */
    private AgentDirective directiveFor(GuestServer server, ProvisioningProgress progress) {
        if (progress.isCompleted()) {
            return AgentDirective.REBOOT;
        }
        if (progress.getCurrentPhase() == ProvisioningPhase.DIAGNOSE_LINUX && !isEnriched(server)) {
            return AgentDirective.COLLECT;
        }
        return AgentDirective.WAIT;
    }

    private boolean isEnriched(GuestServer server) {
        return guestServerDetailRepository.findByServerIdWithBoardModel(server.getId())
                .map(GuestServerDetail::getDiscoveryStage)
                .map(stage -> stage == DiscoveryStage.DIAGNOSTIC_ENRICHED)
                .orElse(false);
    }

    /**
     * 에이전트 보고 게이트(HF) — 서버가 실제 프로비저닝 중일 때만 허용한다. "프로비저닝 중" 은
     * {@link GuestServerStatus#derive}({@code PROVISIONING}) 와 동일 조건(개시됨 + 미회수 + 미실패 + 미종단)
     * 이라 별도 기준을 만들지 않고 재사용한다. 개시 게이트는 {@code /boot} 가 정상 흐름을 막고, 이 가드는
     * 그것을 우회하는 direct POST(하네스 · 외부 변조)의 안전망이다.
     */
    private void requireProvisioning(GuestServer server, ProvisioningProgress progress) {
        if (GuestServerStatus.derive(progress, server.getDecommissionedAt()) != GuestServerStatus.PROVISIONING) {
            throw AgentReportRejectedException.notProvisioning(server.getId());
        }
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
