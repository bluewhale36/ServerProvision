package com.example.serverprovision.execution.enums;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * ES-2 — step enum 의 선언 순서 계약. 커서의 phase 진입 판정({@code entryOf})과 진행 비교가 선언
 * 순서에 기대므로, 순서를 거스르는 상수 추가는 이 테스트가 빌드에서 막는다(plan D-1 — 명시 순서
 * 필드 대안은 ordinal 과의 이중 권위로 기각, 그 대신 커버리지 테스트가 계약을 고정한다).
 */
class ProvisioningPhaseStepTest {

    @Test
    @DisplayName("entryOf — 모든 phase 가 진입 step 을 가지며, 그 step 은 해당 phase 소속이다 (전 상수 커버리지)")
    void entryOf_coversEveryPhase() {
        for (ProvisioningPhase phase : ProvisioningPhase.values()) {
            ProvisioningPhaseStep entry = ProvisioningPhaseStep.entryOf(phase);
            assertThat(entry.getPhaseType()).isEqualTo(phase);
        }
    }

    @Test
    @DisplayName("entryOf — 선언 순서상 그 phase 의 첫 상수를 돌려준다 (pre-position 의 유일한 계산 지점)")
    void entryOf_returnsFirstDeclaredConstant() {
        assertThat(ProvisioningPhaseStep.entryOf(ProvisioningPhase.BOOTSTRAPPING))
                .isEqualTo(ProvisioningPhaseStep.NETWORK_ALLOCATING);
        assertThat(ProvisioningPhaseStep.entryOf(ProvisioningPhase.DIAGNOSE_LINUX))
                .isEqualTo(ProvisioningPhaseStep.DIAGNOSTIC_BOOTING);
        assertThat(ProvisioningPhaseStep.entryOf(ProvisioningPhase.FIRMWARE_UPDATING))
                .isEqualTo(ProvisioningPhaseStep.BIOS_UPDATING);
        assertThat(ProvisioningPhaseStep.entryOf(ProvisioningPhase.RAID_CONFIGURATION))
                .isEqualTo(ProvisioningPhaseStep.RAID_INVENTORY_COLLECTING);
    }

    @Test
    @DisplayName("선언 순서 계약 — step 순서가 소속 phase 의 선언 순서를 거스르지 않는다 (순서 위반 상수 = 빌드 실패)")
    void declarationOrder_neverRegressesPhaseOrder() {
        int lastPhaseOrdinal = -1;
        for (ProvisioningPhaseStep step : ProvisioningPhaseStep.values()) {
            int phaseOrdinal = step.getPhaseType().ordinal();
            assertThat(phaseOrdinal)
                    .as("step %s 의 소속 phase(%s)가 앞선 step 의 phase 를 거슬러 선언됐다",
                            step, step.getPhaseType())
                    .isGreaterThanOrEqualTo(lastPhaseOrdinal);
            lastPhaseOrdinal = phaseOrdinal;
        }
    }
}
