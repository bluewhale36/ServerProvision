package com.example.serverprovision.execution.engine;

import com.example.serverprovision.execution.dto.response.AgentCheckinResponse;
import com.example.serverprovision.execution.dto.response.StepOpenResponse;
import com.example.serverprovision.execution.entity.GuestServer;
import com.example.serverprovision.execution.entity.ProvisioningProgress;
import com.example.serverprovision.execution.entity.SetupStep;
import com.example.serverprovision.execution.enums.AgentDirective;
import com.example.serverprovision.execution.enums.GuestServerStatus;
import com.example.serverprovision.execution.enums.ProvisioningPhase;
import com.example.serverprovision.execution.enums.ProvisioningPhaseStep;
import com.example.serverprovision.execution.enums.ProvisioningStatus;
import com.example.serverprovision.execution.exception.AgentReportRejectedException;
import com.example.serverprovision.execution.exception.GuestServerNotFoundException;
import com.example.serverprovision.execution.exception.SetupStepNotFoundException;
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
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AgentReportService {

    private final GuestServerRepository guestServerRepository;
    private final ProvisioningProgressRepository provisioningProgressRepository;
    private final SetupStepRepository setupStepRepository;
    private final SetupStepRecorder setupStepRecorder;

    /**
     * 체크인 — 진단 리눅스 기동 사실 신호. <b>첫 체크인(개시됨 + 커서 BOOTSTRAPPING)만</b>
     * DIAGNOSE_LINUX 로 전이하고(수신 트랜잭션 내 즉시, DEC-2), 그 외(재체크인 · 미개시 direct 호출 ·
     * 종결/회수 상태)는 전이 없이 지시 골격만 반환한다.
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
        return new AgentCheckinResponse(AgentDirective.WAIT);
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
     * 타 게스트의 stepId(forging)는 404 로 존재를 숨긴다.
     */
    @Transactional
    public void closeStep(String presentedToken, UUID stepId, ProvisioningStatus result, String statusMeta) {
        GuestServer server = requireByToken(presentedToken);
        ProvisioningProgress progress = requireProgress(server);
        requireProvisioning(server, progress);

        SetupStep step = setupStepRepository.findById(stepId)
                .filter(s -> s.getGuestServer().getId().equals(server.getId()))
                .orElseThrow(() -> new SetupStepNotFoundException(stepId));

        boolean closed = step.close(result, statusMeta, LocalDateTime.now());
        if (closed && result == ProvisioningStatus.FAILED) {
            // 가드가 미실패·미종단을 이미 보장하므로 markFailed 는 곧바로 안전하다.
            progress.markFailed(step.getStepCode(), LocalDateTime.now());
            log.warn("게스트 실패 보고 — 실패 신호 기록 : guestServerId={}, step={}",
                    server.getId(), step.getStepCode());
        }
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
        return guestServerRepository.findByGuestToken(new GuestToken(presented))
                .orElseThrow(GuestServerNotFoundException::byToken);
    }

    private ProvisioningProgress requireProgress(GuestServer server) {
        // progress 는 등록 트랜잭션이 1:1 로 seed 한다(U1 §D6) — 부재는 데이터 손상이므로 500 이 정직하다.
        return provisioningProgressRepository.findByGuestServer_Id(server.getId())
                .orElseThrow(() -> new IllegalStateException(
                        "provisioning_progress 1:1 불변 위반 — 등록 seed 누락. guestServerId=" + server.getId()));
    }
}
