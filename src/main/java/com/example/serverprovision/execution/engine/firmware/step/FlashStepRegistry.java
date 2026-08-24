package com.example.serverprovision.execution.engine.firmware.step;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * 집행 행 수집(E2-2) — 기동 시 {@link FlashStep} 빈을 모아 순서대로 세운다.
 * {@code PhaseExecutorRegistry} 가 phase 실행기를 수집하는 것과 같은 형태다.
 *
 * <p>같은 순서를 두 행이 주장하면 어느 쪽이 이길지 알 수 없으므로 기동에서 막는다 — silent 우선순위
 * 사고를 차단하는 것이 이 registry 의 두 번째 몫이다.</p>
 */
@Slf4j
@Component
public class FlashStepRegistry {

    private final List<FlashStep> ordered;

    public FlashStepRegistry(List<FlashStep> steps) {
        this.ordered = steps.stream().sorted(Comparator.comparingInt(FlashStep::order)).toList();
        long distinct = ordered.stream().mapToInt(FlashStep::order).distinct().count();
        if (distinct != ordered.size()) {
            throw new IllegalStateException("집행 행의 순서가 겹칩니다 — " + ordered.stream()
                    .map(s -> s.order() + ":" + s.getClass().getSimpleName()).toList());
        }
        log.info("[flash] 집행 행 {} 개 등록 : {}", ordered.size(),
                ordered.stream().map(s -> s.order() + ":" + s.getClass().getSimpleName()).toList());
    }

    /** 위에서 처음 맞는 행 — 없으면 이번 주기에 할 일이 없다. */
    public Optional<FlashStep> firstMatching(FlashContext context) {
        return ordered.stream().filter(step -> step.matches(context)).findFirst();
    }
}
