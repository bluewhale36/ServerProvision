package com.example.serverprovision.execution.enums;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * U1 §D4 — 운영 상태 도출 진리표. 상태는 저장하지 않고 (회수 + 진행 단계)에서 도출되므로
 * 이 순수 함수가 뷰모델의 SSOT 다.
 */
class GuestServerStatusTest {

    @Test
    @DisplayName("회수 시각이 있으면 진행 단계와 무관하게 DECOMMISSIONED (회수 최우선)")
    void decommission_takesPrecedence() {
        assertThat(GuestServerStatus.derive(ProvisioningPhase.OS_INSTALLING, LocalDateTime.now()))
                .isEqualTo(GuestServerStatus.DECOMMISSIONED);
        assertThat(GuestServerStatus.derive(null, LocalDateTime.now()))
                .isEqualTo(GuestServerStatus.DECOMMISSIONED);
    }

    @Test
    @DisplayName("progress 없음(null) → REGISTERED")
    void noProgress_isRegistered() {
        assertThat(GuestServerStatus.derive(null, null)).isEqualTo(GuestServerStatus.REGISTERED);
    }

    @Test
    @DisplayName("progress = BOOTSTRAPPING(seed) → REGISTERED (아직 본 프로비저닝 미진입)")
    void bootstrapping_isRegistered() {
        assertThat(GuestServerStatus.derive(ProvisioningPhase.BOOTSTRAPPING, null))
                .isEqualTo(GuestServerStatus.REGISTERED);
    }

    @Test
    @DisplayName("progress 가 BOOTSTRAPPING 초과 진행 → PROVISIONING")
    void beyondBootstrapping_isProvisioning() {
        assertThat(GuestServerStatus.derive(ProvisioningPhase.FIRMWARE_UPDATING, null))
                .isEqualTo(GuestServerStatus.PROVISIONING);
        assertThat(GuestServerStatus.derive(ProvisioningPhase.OS_INSTALLING, null))
                .isEqualTo(GuestServerStatus.PROVISIONING);
        assertThat(GuestServerStatus.derive(ProvisioningPhase.TESTING, null))
                .isEqualTo(GuestServerStatus.PROVISIONING);
    }
}
