package com.example.serverprovision.execution.enums;

import com.example.serverprovision.execution.entity.ProvisioningProgress;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * U1 §D4 + E1-0a — 운영 상태 도출 진리표. 상태는 저장하지 않고 (회수 + 진행 신호)에서 도출되므로
 * 이 순수 함수가 뷰모델의 SSOT 다. 우선순위: 회수 > 실패 > 종단 > 개시 여부.
 */
class GuestServerStatusTest {

    private static final LocalDateTime T = LocalDateTime.of(2026, 7, 12, 12, 0);

    private ProvisioningProgress progress(LocalDateTime startedAt, LocalDateTime failedAt, LocalDateTime completedAt) {
        return ProvisioningProgress.builder()
                .currentPhase(ProvisioningPhase.BOOTSTRAPPING)
                .lastTransitionAt(T)
                .startedAt(startedAt)
                .failedAt(failedAt)
                .failedStepCode(failedAt != null ? ProvisioningPhaseStep.INFORMATION_COLLECTING : null)
                .completedAt(completedAt)
                .build();
    }

    @Test
    @DisplayName("1순위 회수 — 실패·종단 신호가 있어도 DECOMMISSIONED")
    void decommission_takesPrecedence() {
        assertThat(GuestServerStatus.derive(progress(T, T, null), T)).isEqualTo(GuestServerStatus.DECOMMISSIONED);
        assertThat(GuestServerStatus.derive(progress(T, null, T), T)).isEqualTo(GuestServerStatus.DECOMMISSIONED);
        assertThat(GuestServerStatus.derive(null, T)).isEqualTo(GuestServerStatus.DECOMMISSIONED);
    }

    @Test
    @DisplayName("2순위 실패 신호 → FAILED (E1-0a 신설 도달)")
    void failedSignal_isFailed() {
        assertThat(GuestServerStatus.derive(progress(T, T, null), null)).isEqualTo(GuestServerStatus.FAILED);
    }

    @Test
    @DisplayName("3순위 종단 신호 → PROVISIONED (E1-0a 신설 도달)")
    void completedSignal_isProvisioned() {
        assertThat(GuestServerStatus.derive(progress(T, null, T), null)).isEqualTo(GuestServerStatus.PROVISIONED);
    }

    @Test
    @DisplayName("progress 없음(null) → REGISTERED")
    void noProgress_isRegistered() {
        assertThat(GuestServerStatus.derive(null, null)).isEqualTo(GuestServerStatus.REGISTERED);
    }

    @Test
    @DisplayName("미개시(seed 상태, startedAt null) → REGISTERED — 커서==BOOTSTRAPPING 분기의 대체(DEC-26)")
    void notStarted_isRegistered() {
        assertThat(GuestServerStatus.derive(progress(null, null, null), null))
                .isEqualTo(GuestServerStatus.REGISTERED);
    }

    @Test
    @DisplayName("개시 직후(게스트 미체크인, 커서 아직 BOOTSTRAPPING) → PROVISIONING — 진입 대기 포함")
    void startedButNotCheckedIn_isProvisioning() {
        assertThat(GuestServerStatus.derive(progress(T, null, null), null))
                .isEqualTo(GuestServerStatus.PROVISIONING);
    }

    @Test
    @DisplayName("개시 + 커서 진행 중 → PROVISIONING")
    void startedAndAdvanced_isProvisioning() {
        ProvisioningProgress advanced = ProvisioningProgress.builder()
                .currentPhase(ProvisioningPhase.OS_INSTALLING)
                .lastTransitionAt(T)
                .startedAt(T)
                .build();
        assertThat(GuestServerStatus.derive(advanced, null)).isEqualTo(GuestServerStatus.PROVISIONING);
    }
}
