package com.example.serverprovision.execution.engine.boot;

import com.example.serverprovision.execution.engine.phase.HoldTtlPolicy;
import com.example.serverprovision.execution.engine.phase.PhaseExecutorRegistry;
import com.example.serverprovision.execution.engine.phase.PhaseReadiness;
import com.example.serverprovision.execution.engine.ProvisioningHistoryRecorder;
import com.example.serverprovision.execution.entity.GuestServer;
import com.example.serverprovision.execution.entity.ProvisioningHistory;
import com.example.serverprovision.execution.entity.ProvisioningProgress;
import com.example.serverprovision.execution.enums.ProvisioningMotion;
import com.example.serverprovision.execution.enums.ProvisioningStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * phase 진입 게이트(E2-1-b, 토론 D1 · D2) — 게스트가 부팅으로 재진입하는 순간 그 phase 의 재료가
 * 온전한지 묻고, 결손 사다리를 집행한다.
 *
 * <p>왜 dispatch 가 아니라 별도 게이트인가 — {@link BootScriptDispatcher} 는 "상태를 일절 바꾸지 않는
 * 읽기 전용 판정"(DEC-2)이고, 결손 대기 진입 · 해소 · 시한 만료 실패는 상태 전이다. 전이는 이 게이트가,
 * 그 상태의 표현은 dispatcher 가 나눠 갖는다.</p>
 *
 * <p>사다리(D1 4차 개정) — 진입 결손은 <b>대기</b>(폴링이 공짜 재시도라 자원이 돌아오면 저절로 재개),
 * 시한이 지나면 <b>실패 전환</b>(운영자 재시도로 회복 가능), 착수 후 결손은 실패(그 전이는 아예
 * 표현 불가 — {@link ProvisioningProgress#holdForShortage} 가 STEP_RUNNING 에서 거부한다).</p>
 *
 * <p>시한 처리를 스케줄러가 아니라 폴링 시점에 두는 이유: 게스트는 30초마다 반드시 이 경로를 밟으므로
 * 새 인프라가 필요 없다. 폴링이 끊긴 게스트(전원 단절)는 애초에 운영자 수동 실패 전환(UC-4) 소관이다.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PhaseEntryGate {

    private final PhaseExecutorRegistry phaseExecutorRegistry;
    private final ProvisioningHistoryRecorder provisioningHistoryRecorder;
    private final HoldTtlPolicy holdTtlPolicy;

    /**
     * 진입 판정 + 전이. 호출자({@code BootService})의 트랜잭션 안에서 돌아 전이가 응답과 같은 원자
     * 단위로 커밋된다.
     *
     * @return 이 부팅에 적용된 준비도 — dispatcher 가 대기 스크립트의 사유로 쓴다.
     */
    public PhaseReadiness evaluate(GuestServer server, ProvisioningProgress progress, LocalDateTime now) {
        if (!isInExecutionWindow(server, progress)) {
            return PhaseReadiness.ready();          // 회수 · 미개시 · 실패 · 종단은 dispatch 상위 행이 받는다
        }
        if (progress.getMotion() == ProvisioningMotion.STEP_RUNNING) {
            return PhaseReadiness.ready();          // 착수한 게스트는 게이트 대상이 아니다(D1 — 그 결손은 실패 소관)
        }

        PhaseReadiness readiness = phaseExecutorRegistry.find(progress.currentPhase())
                .map(executor -> executor.readiness(server, progress))
                .orElseGet(PhaseReadiness::ready);  // 실행기 미등록 phase 는 판정할 재료가 없다(dispatch 가 HOLD 안내)

        if (readiness.isBlocked()) {
            blockedPath(server, progress, readiness, now);
        } else if (progress.isHolding()) {
            progress.resumeFromHold(now);           // 재료가 돌아왔다 — 운영자 개입 없이 재개
            log.info("[gate] {} — 자원 결손 해소, 진입 재개 : step={}", server.getId(), progress.getCurrentStep());
        }
        return readiness;
    }

    /** 결손 경로 — 처음이면 대기 진입, 이미 대기 중이면 시한을 본다(해소는 위에서 이미 갈라졌다). */
    private void blockedPath(GuestServer server, ProvisioningProgress progress,
                             PhaseReadiness readiness, LocalDateTime now) {
        if (!progress.isHolding()) {
            progress.holdForShortage(now);
            log.info("[gate] {} — 자원 결손으로 대기 진입 : step={}, 사유={}",
                    server.getId(), progress.getCurrentStep(), readiness.wire());
            return;
        }
        if (!holdTtlPolicy.isExpired(progress.getLastTransitionAt(), now)) {
            return;                                  // 아직 시한 안 — 다음 폴링에서 다시 본다
        }
        // 시한 만료 = 실패 전환(D1 4차 개정). 사유는 파생할 수 없으므로 사건 시점에 원장으로 남긴다.
        provisioningHistoryRecorder.recordInstant(server, progress.getCurrentStep(), ProvisioningStatus.FAILED,
                ProvisioningHistory.holdTtlMeta(readiness.wire(), holdTtlPolicy.ttl()), now);
        progress.markFailed(now);
        log.warn("[gate] {} — 자원 결손 대기 시한({}) 만료, 실패 전환 : step={}, 사유={}",
                server.getId(), holdTtlPolicy.ttl(), progress.getCurrentStep(), readiness.wire());
    }

    private boolean isInExecutionWindow(GuestServer server, ProvisioningProgress progress) {
        return server.getDecommissionedAt() == null
                && progress.isStarted() && !progress.isFailed() && !progress.isCompleted();
    }
}
