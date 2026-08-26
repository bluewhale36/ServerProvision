package com.example.serverprovision.execution.engine.firmware.step;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

/**
 * 집행 행 수집(E2-2) — 기동 시 {@link FlashStep} 빈을 모아 순서대로 세운다.
 * {@code PhaseExecutorRegistry} 가 phase 실행기를 수집하는 것과 같은 형태다. 순서 정렬 · 중복 fail-fast 는
 * 공통 {@code WorkerStepRegistry}(E3-1 에서 추출)가 맡고, 여기는 flash 타입과 로그 태그만 고정한다.
 *
 * <p>같은 순서를 두 행이 주장하면 어느 쪽이 이길지 알 수 없으므로 기동에서 막는다 — silent 우선순위
 * 사고를 차단하는 것이 이 registry 의 두 번째 몫이다.</p>
 */
@Component
public class FlashStepRegistry {

    private final com.example.serverprovision.execution.engine.worker.WorkerStepRegistry<FlashContext, FlashStep> delegate;

    public FlashStepRegistry(List<FlashStep> steps) {
        this.delegate = new com.example.serverprovision.execution.engine.worker.WorkerStepRegistry<>("flash", steps);
    }

    /** 위에서 처음 맞는 행 — 없으면 이번 주기에 할 일이 없다. */
    public Optional<FlashStep> firstMatching(FlashContext context) {
        return delegate.firstMatching(context);
    }
}
