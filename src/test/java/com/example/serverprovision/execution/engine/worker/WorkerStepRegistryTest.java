package com.example.serverprovision.execution.engine.worker;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * E3-1 D-1 — E2-2 {@code FlashStepRegistry} 에서 추출한 공통 행 수집기. 등록 순서와 무관하게 order 로 세우고,
 * 같은 order 는 기동에서 막는다(silent 우선순위 사고 차단). phase 별 registry 두 벌(flash · setting)이 이것에 기댄다.
 */
class WorkerStepRegistryTest {

    @Test
    @DisplayName("등록 순서가 아니라 order 순서로 묻는다 — 처음 맞는 행이 답이다")
    void ordersByOrderNotRegistration() {
        WorkerStepRegistry<String, Stub> registry = new WorkerStepRegistry<>("test", List.of(
                new Stub(3, "c"), new Stub(1, "a"), new Stub(2, "b")));

        assertThat(registry.firstMatching("any")).map(Stub::name).contains("a");
        assertThat(registry.firstMatching("skip-a")).map(Stub::name).contains("b");
    }

    @Test
    @DisplayName("맞는 행이 없으면 empty — 이번 주기에 할 일이 없다")
    void noneMatchingIsEmpty() {
        WorkerStepRegistry<String, Stub> registry = new WorkerStepRegistry<>("test", List.of(new Stub(1, "a")));

        assertThat(registry.firstMatching("skip-a")).isEmpty();
    }

    @Test
    @DisplayName("같은 order 두 행 — 태그를 담은 메시지로 기동에서 막는다")
    void duplicateOrderFailsFastWithTag() {
        assertThatThrownBy(() -> new WorkerStepRegistry<>("flash", List.of(new Stub(5, "x"), new Stub(5, "y"))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("flash")
                .hasMessageContaining("겹칩니다")
                .hasMessageContaining("5:Stub");
    }

    /** 컨텍스트 "skip-<name>" 이면 그 행은 맞지 않는다. */
    private record Stub(int order, String name) implements WorkerStep<String> {
        @Override public boolean matches(String context) { return !context.equals("skip-" + name); }
        @Override public void execute(String context) { }
    }
}
