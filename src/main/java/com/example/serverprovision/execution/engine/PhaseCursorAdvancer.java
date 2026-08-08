package com.example.serverprovision.execution.engine;

import com.example.serverprovision.execution.entity.ProvisioningProgress;
import com.example.serverprovision.execution.enums.ProvisioningPhase;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Set;
import java.util.UUID;

/**
 * 커서 전진 · 종단 규칙 SSOT(ES-1 · DES-1) — 한 phase 를 완주한 게스트의 커서를 다음 소유 phase 로
 * 전진시키거나(더 할 일이 있으면) 종단시킨다(없으면). 이 규칙은 진단 전용이 아니라 <b>모든 phase 완료가
 * 공유</b>하므로(후속 실행기 {@code FirmwareUpdatingExecutor} 등이 같은 판정을 필요로 한다), 실행기마다
 * 복붙하지 않고 이 협력자 1곳에 둔다(중복 금지 · 조건분기 확장 금지).
 *
 * <p>{@link OwnedPhasesProvider} 주입은 엔진 통틀어 여기 1곳뿐이다 — 활성 할당의 동결 {@code ownedPhases}
 * 를 읽어 {@link PhaseSequence#nextAfter} 에 흘려 넣는다. 무할당 게스트는 빈 집합을 받아 {@code nextAfter}
 * 가 empty → 종단이라, "진단만 완주하면 종단" 이라는 U3 이전 동작을 분기 추가 0 으로 재현한다.</p>
 *
 * <p>별도 {@code @Transactional} 을 두지 않는다 — 호출자({@code onStepClosed} 등)의 트랜잭션에 조인해
 * 커서 전진 · 종단이 수집 적재와 같은 원자 단위로 커밋된다.</p>
 */
@Component
@RequiredArgsConstructor
public class PhaseCursorAdvancer {

    private final OwnedPhasesProvider ownedPhasesProvider;

    /**
     * {@code progress} 의 현재 phase 를 완주한 시점에 호출한다. 소유 다음 phase 가 있으면
     * {@link ProvisioningProgress#advanceTo} 로 커서를 전진시키고, 없으면
     * {@link ProvisioningProgress#markCompleted} 로 종단한다.
     */
    public void advanceOrComplete(ProvisioningProgress progress, UUID guestId, LocalDateTime now) {
        Set<ProvisioningPhase> owned = ownedPhasesProvider.ownedPhasesOf(guestId);
        PhaseSequence.nextAfter(progress.getCurrentPhase(), owned)
                .ifPresentOrElse(next -> progress.advanceTo(next, now),   // 소유 phase 있음 → 전진
                                 () -> progress.markCompleted(now));       // 없음 → 종단(무할당 = 현 동작 보존)
    }
}
