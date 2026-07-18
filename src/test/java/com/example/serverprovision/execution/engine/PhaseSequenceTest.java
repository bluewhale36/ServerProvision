package com.example.serverprovision.execution.engine;

import com.example.serverprovision.execution.enums.ProvisioningPhase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * E1-0a CP4 — 다음 phase 결정 규칙(DEC-8 · DEC-25) 고정. "보유 마지막 phase 완주 = 종단" 이
 * E6 보류 상태의 종단 신호 규칙이므로, 이 표가 바뀌면 실행 순서 계약이 바뀐 것이다.
 */
class PhaseSequenceTest {

    @Test
    @DisplayName("BOOTSTRAPPING 다음은 보유와 무관하게 DIAGNOSE_LINUX — 진단은 정의서 payload 를 소비하지 않음")
    void bootstrapping_alwaysGoesToDiagnose() {
        assertThat(PhaseSequence.nextAfter(ProvisioningPhase.BOOTSTRAPPING, Set.of()))
                .contains(ProvisioningPhase.DIAGNOSE_LINUX);
        assertThat(PhaseSequence.nextAfter(ProvisioningPhase.BOOTSTRAPPING,
                Set.of(ProvisioningPhase.OS_INSTALLING)))
                .contains(ProvisioningPhase.DIAGNOSE_LINUX);
    }

    @Test
    @DisplayName("보유 필터 — 미보유 phase 는 선언 순서에서 건너뛴다 (DEC-8)")
    void skipsUnownedPhases() {
        assertThat(PhaseSequence.nextAfter(ProvisioningPhase.DIAGNOSE_LINUX,
                Set.of(ProvisioningPhase.OS_INSTALLING, ProvisioningPhase.OS_SETTING)))
                .contains(ProvisioningPhase.OS_INSTALLING);   // 펌웨어 2 phase 미보유 → 건너뜀
        assertThat(PhaseSequence.nextAfter(ProvisioningPhase.FIRMWARE_UPDATING,
                Set.of(ProvisioningPhase.FIRMWARE_UPDATING, ProvisioningPhase.OS_SETTING)))
                .contains(ProvisioningPhase.OS_SETTING);
    }

    @Test
    @DisplayName("보유 마지막 phase 완주 → empty = 종단 (DEC-25 — 호출자가 markCompleted)")
    void lastOwnedPhase_terminates() {
        assertThat(PhaseSequence.nextAfter(ProvisioningPhase.OS_SETTING,
                Set.of(ProvisioningPhase.FIRMWARE_UPDATING, ProvisioningPhase.OS_INSTALLING,
                        ProvisioningPhase.OS_SETTING)))
                .isEmpty();
    }

    @Test
    @DisplayName("보유 0(정의서가 없거나 진단만) — 진단 완주 즉시 종단")
    void noOwnedPhases_terminatesAfterDiagnose() {
        assertThat(PhaseSequence.nextAfter(ProvisioningPhase.DIAGNOSE_LINUX, Set.of())).isEmpty();
    }

    @Test
    @DisplayName("TESTING 은 보유 집합에 있어도 진입하지 않음 — E6 보류 + SettingProcessType 계약 부재")
    void testing_neverEntered() {
        assertThat(PhaseSequence.nextAfter(ProvisioningPhase.OS_SETTING,
                Set.of(ProvisioningPhase.TESTING)))
                .isEmpty();
    }
}
