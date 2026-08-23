package com.example.serverprovision.execution.engine;

import com.example.serverprovision.execution.entity.GuestServer;
import com.example.serverprovision.execution.entity.ProvisioningProgress;
import com.example.serverprovision.execution.enums.ProvisioningPhase;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;


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
    public String dispatch(GuestServer server, ProvisioningProgress progress,
                           PhaseReadiness readiness, String rebootQuery) {
        if (server.getDecommissionedAt() != null) {                       // 2행
            return IpxeScripts.decommissioned(rebootQuery);
        }
        if (progress.isFailed()) {                                        // 3행 — 실패 지점 = 커서(ES-2 D-5)
            return IpxeScripts.failed(progress.getCurrentStep(), rebootQuery);
        }
        if (progress.isCompleted()) {                                     // 4행 — E1-2 이분(로드맵 D3)
            // OS 미설치 베어메탈에 exit(로컬 부팅 폴스루)는 부팅 실패 루프다. 완주 커서는 "마지막
            // 보유 phase"(DEC-25)이므로, OS 설치까지 갔던 서버만 exit — 그 전(진단만 완주 = 입고 검수)은
            // 대기 폴링을 유지한다(U3 할당이 생기면 이 폴링이 재개 트리거).
            boolean osInstalled = progress.currentPhase() != null
                    && progress.currentPhase().ordinal() >= ProvisioningPhase.OS_INSTALLING.ordinal();
            return osInstalled ? IpxeScripts.completedExit() : IpxeScripts.awaitingIntake(rebootQuery);
        }
        if (!progress.isStarted()) {                                      // 5행 — 개시 게이트(DEC-26)
            return IpxeScripts.waitingForStart(rebootQuery);
        }
        if (progress.isHolding()) {                                       // 6행 — 자원 결손 대기(E2-1-b)
            // 진입 게이트가 이미 대기로 들여보낸 상태다. 여기서는 그 사실을 게스트에게 알리기만 한다 —
            // 재료가 돌아오면 다음 폴링에서 게이트가 대기를 풀고 이 행을 지나친다.
            return IpxeScripts.shortageHold(readiness.wire(), rebootQuery);
        }
        // 커서의 phase 파생이 곧 부팅 목표(ES-2 D-1) — 커서가 항상 "도달했거나 향하는 목표 step" 을
        // 가리키므로(등록 seed 부터 DIAGNOSTIC_BOOTING), 옛 bootTargetPhase 의 BOOTSTRAPPING 특례가
        // 필요 없어졌다(E1-0b 스모크가 발견했던 영구 HOLD 문제의 원인 자체가 소멸).
        ProvisioningPhase target = progress.currentPhase();
        return phaseExecutorRegistry.find(target)                         // 7행 HOLD / 8행 위임
                .map(executor -> executor.bootScript(server, progress, rebootQuery))
                .orElseGet(() -> IpxeScripts.hold(target, rebootQuery));
    }
}
