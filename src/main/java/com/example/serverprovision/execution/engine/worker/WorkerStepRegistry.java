package com.example.serverprovision.execution.engine.worker;

import lombok.extern.slf4j.Slf4j;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * 행 수집(E3-1 D-1 — E2-2 {@code FlashStepRegistry} 에서 추출) — 빈을 순서대로 세우고, 같은 순서를 두 행이
 * 주장하면 기동에서 막는다(silent 우선순위 사고 차단). phase 별 registry 는 이것을 감싸 로그 태그만 준다.
 *
 * @param <C> 컨텍스트 · @param <S> 그 phase 의 행 타입
 */
@Slf4j
public class WorkerStepRegistry<C, S extends WorkerStep<C>> {

    private final List<S> ordered;

    public WorkerStepRegistry(String tag, List<S> steps) {
        this.ordered = steps.stream().sorted(Comparator.comparingInt(WorkerStep::order)).toList();
        long distinct = ordered.stream().mapToInt(WorkerStep::order).distinct().count();
        if (distinct != ordered.size()) {
            throw new IllegalStateException(tag + " 행의 순서가 겹칩니다 — " + describe());
        }
        log.info("[{}] 행 {} 개 등록 : {}", tag, ordered.size(), describe());
    }

    /** 위에서 처음 맞는 행 — 없으면 이번 주기에 할 일이 없다. */
    public Optional<S> firstMatching(C context) {
        return ordered.stream().filter(step -> step.matches(context)).findFirst();
    }

    private List<String> describe() {
        return ordered.stream().map(s -> s.order() + ":" + s.getClass().getSimpleName()).toList();
    }
}
