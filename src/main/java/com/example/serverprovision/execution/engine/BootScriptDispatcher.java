package com.example.serverprovision.execution.engine;

import com.example.serverprovision.execution.entity.GuestServer;
import com.example.serverprovision.execution.entity.ProvisioningProgress;
import com.example.serverprovision.execution.enums.ProvisioningPhase;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * {@code /boot} 응답 판정(E1-0b) — dispatch 매트릭스 v1 의 SSOT. 판정 순서가 곧 우선순위이며
 * GuestServerStatus.derive 진리표(회수 &gt; 실패 &gt; 종단 &gt; 개시)와 정렬을 맞춘다 —
 * "배지가 말하는 것 = 게스트가 받는 것". 상태를 일절 바꾸지 않는 읽기 전용 판정(DEC-2)이다.
 *
 * <p>신규 phase 의 행 추가는 이 클래스의 분기 증식이 아니라 {@link ProvisioningPhaseExecutor}
 * 빈 등록(6행 HOLD → 7행 위임 자동 전환)이다.</p>
 */
@Component
@RequiredArgsConstructor
public class BootScriptDispatcher {

    private final PhaseExecutorRegistry phaseExecutorRegistry;

    /**
     * 등록(멱등)이 끝난 게스트에 대한 응답 스크립트. (매트릭스 1행 "미등록 → 등록" 은 호출 전에
     * {@code GuestServerRegistrationService} 가 이미 수행 — 여기 도달 시점엔 항상 등록돼 있다.)
     */
    public String dispatch(GuestServer server, ProvisioningProgress progress, String rebootQuery) {
        if (server.getDecommissionedAt() != null) {                       // 2행
            return IpxeScripts.decommissioned(rebootQuery);
        }
        if (progress.isFailed()) {                                        // 3행
            return IpxeScripts.failed(progress.getFailedStepCode(), rebootQuery);
        }
        if (progress.isCompleted()) {                                     // 4행
            return IpxeScripts.completedExit();
        }
        if (!progress.isStarted()) {                                      // 5행 — 개시 게이트(DEC-26)
            return IpxeScripts.waitingForStart(rebootQuery);
        }
        ProvisioningPhase target = bootTargetPhase(progress);
        return phaseExecutorRegistry.find(target)                         // 6행 HOLD / 7행 위임
                .map(executor -> executor.bootScript(server, progress, rebootQuery))
                .orElseGet(() -> IpxeScripts.hold(target, rebootQuery));
    }

    /**
     * 이 부팅이 이끌어야 할 phase. 커서는 게스트 사실 신호(체크인)에야 움직이므로(DEC-2),
     * 개시됐지만 커서가 아직 BOOTSTRAPPING 인 서버가 받아야 할 것은 커서 자신이 아니라
     * <b>다음 진입 대상</b>(진단 리눅스)의 스크립트다 — 아니면 E1-1 이 실행기를 등록해도
     * 게스트가 영원히 HOLD 에 갇힌다(E1-0b 스모크가 발견). 규칙은 {@link PhaseSequence} SSOT 재사용
     * (BOOTSTRAPPING 다음은 보유 무관 진단 — 빈 집합으로 충분).
     */
    private ProvisioningPhase bootTargetPhase(ProvisioningProgress progress) {
        if (progress.getCurrentPhase() == ProvisioningPhase.BOOTSTRAPPING) {
            return PhaseSequence.nextAfter(ProvisioningPhase.BOOTSTRAPPING, Set.of())
                    .orElseThrow(() -> new IllegalStateException("BOOTSTRAPPING 다음 phase 부재 — PhaseSequence 규칙 위반"));
        }
        return progress.getCurrentPhase();
    }
}
