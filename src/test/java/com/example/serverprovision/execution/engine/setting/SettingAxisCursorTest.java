package com.example.serverprovision.execution.engine.setting;

import com.example.serverprovision.execution.engine.phase.PhaseCursorAdvancer;
import com.example.serverprovision.execution.entity.ProvisioningProgress;
import com.example.serverprovision.execution.enums.ProvisioningPhaseStep;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/** E3-2 D-2 — 축의 순서와 축 종결 뒤 커서. BIOS 다음은 같은 phase 안의 BMC, BMC 다음은 phase 완주. */
@ExtendWith(MockitoExtension.class)
class SettingAxisCursorTest {

    private static final LocalDateTime T = LocalDateTime.of(2026, 8, 26, 12, 0);

    @Mock PhaseCursorAdvancer advancer;

    @Test
    @DisplayName("SettingAxis — BIOS → BMC → 없음, step 으로 축을 찾는다")
    void axisOrder() {
        assertThat(SettingAxis.BIOS.next()).contains(SettingAxis.BMC);
        assertThat(SettingAxis.BMC.next()).isEmpty();
        assertThat(SettingAxis.of(ProvisioningPhaseStep.BMC_SETTING)).contains(SettingAxis.BMC);
        assertThat(SettingAxis.of(ProvisioningPhaseStep.BIOS_UPDATING)).isEmpty();
    }

    @Test
    @DisplayName("afterAxis(BIOS) — 같은 phase 안에서 BMC_SETTING 으로 옮기고 phase 는 닫지 않는다")
    void afterBiosMovesToBmc() {
        ProvisioningProgress progress = started();

        new SettingCursor(advancer).afterAxis(SettingAxis.BIOS, progress, UUID.randomUUID(), T);

        assertThat(progress.getCurrentStep()).isEqualTo(ProvisioningPhaseStep.BMC_SETTING);
        verify(advancer, never()).advanceOrComplete(any(), any(), any());
    }

    @Test
    @DisplayName("afterAxis(BMC) — phase 완주를 PhaseCursorAdvancer 에 넘긴다(전진 또는 종단)")
    void afterBmcCompletesPhase() {
        ProvisioningProgress progress = started();
        progress.positionAt(ProvisioningPhaseStep.BMC_SETTING, T);
        UUID id = UUID.randomUUID();

        new SettingCursor(advancer).afterAxis(SettingAxis.BMC, progress, id, T);

        verify(advancer).advanceOrComplete(eq(progress), eq(id), eq(T));
    }

    private static ProvisioningProgress started() {
        ProvisioningProgress p = ProvisioningProgress.builder()
                .id(UUID.randomUUID()).currentStep(ProvisioningPhaseStep.BIOS_SETTING).lastTransitionAt(T).build();
        p.start(T);
        return p;
    }
}
