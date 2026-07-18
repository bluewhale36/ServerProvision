package com.example.serverprovision.execution.engine;

import com.example.serverprovision.execution.entity.GuestServer;
import com.example.serverprovision.execution.entity.ProvisioningProgress;
import com.example.serverprovision.execution.enums.ProvisioningPhase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * E1-0b CP4 — registry 수집 규약: 중복 판별자 = 기동 실패(fail-fast), 미등록 = empty(HOLD 입력).
 */
class PhaseExecutorRegistryTest {

    private static ProvisioningPhaseExecutor executor(ProvisioningPhase phase) {
        return new ProvisioningPhaseExecutor() {
            @Override public ProvisioningPhase phase() { return phase; }
            @Override public String bootScript(GuestServer s, ProvisioningProgress p, String q) { return ""; }
        };
    }

    @Test
    @DisplayName("같은 phase 판별자 2개 → 기동 실패 (silent 우선순위 사고 차단)")
    void duplicatePhase_failsFast() {
        assertThatThrownBy(() -> new PhaseExecutorRegistry(
                List.of(executor(ProvisioningPhase.DIAGNOSE_LINUX), executor(ProvisioningPhase.DIAGNOSE_LINUX))))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("미등록 phase → empty (dispatch 가 HOLD 로 응답할 입력)")
    void unregistered_returnsEmpty() {
        PhaseExecutorRegistry registry =
                new PhaseExecutorRegistry(List.of(executor(ProvisioningPhase.DIAGNOSE_LINUX)));
        assertThat(registry.find(ProvisioningPhase.DIAGNOSE_LINUX)).isPresent();
        assertThat(registry.find(ProvisioningPhase.OS_INSTALLING)).isEmpty();
    }
}
