package com.example.serverprovision.execution.engine;

import com.example.serverprovision.execution.enums.ProvisioningPhase;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * phase 실행기 수집 registry(E1-0b, DEC-6) — MarkableScanner 선례와 동형의 기동 시 수집.
 * <ul>
 *   <li>같은 phase 판별자 2개 = 기동 실패(fail-fast) — silent 우선순위 사고 차단.</li>
 *   <li>전체 enum 커버는 강제하지 않는다 — 미등록 phase 도달은 dispatch 가 명시 HOLD 로 응답
 *       (점진 배송과 완전성 검증의 양립, 1차 문서 §4-D7).</li>
 * </ul>
 */
@Component
public class PhaseExecutorRegistry {

    private final Map<ProvisioningPhase, ProvisioningPhaseExecutor> executors;

    public PhaseExecutorRegistry(List<ProvisioningPhaseExecutor> beans) {
        // toMap 은 중복 키에서 IllegalStateException — 그 fail-fast 가 곧 계약이다.
        this.executors = beans.stream()
                .collect(Collectors.toUnmodifiableMap(ProvisioningPhaseExecutor::phase, Function.identity()));
    }

    public Optional<ProvisioningPhaseExecutor> find(ProvisioningPhase phase) {
        return Optional.ofNullable(executors.get(phase));
    }
}
