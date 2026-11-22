package com.example.serverprovision.execution.engine.osinstall;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class InstallConfirmationTest {

    @Test
    @DisplayName("meta 의 Boolean 을 옮기고 되돌리면 같은 값이다")
    void of_roundTrip() {
        assertThat(InstallConfirmation.of(true)).isEqualTo(InstallConfirmation.CONFIRMED);
        assertThat(InstallConfirmation.of(false)).isEqualTo(InstallConfirmation.MISMATCH);
        assertThat(InstallConfirmation.of(null)).isEqualTo(InstallConfirmation.UNREPORTED);
        for (InstallConfirmation c : InstallConfirmation.values()) {
            assertThat(InstallConfirmation.of(c.metaValue())).isEqualTo(c);
        }
    }

    @Test
    @DisplayName("기대값과 보고값은 대소문자 · 앞뒤 공백을 무시해 비교하고, 한쪽이 비면 미보고다")
    void matching() {
        assertThat(InstallConfirmation.matching("wwn-0x600508E0", " wwn-0x600508e0 ")).isEqualTo(InstallConfirmation.CONFIRMED);
        assertThat(InstallConfirmation.matching("wwn-0x600508e0", "wwn-0x5000c500")).isEqualTo(InstallConfirmation.MISMATCH);
        assertThat(InstallConfirmation.matching("wwn-0x600508e0", "")).isEqualTo(InstallConfirmation.UNREPORTED);
        assertThat(InstallConfirmation.matching(null, "wwn-0x600508e0")).isEqualTo(InstallConfirmation.UNREPORTED);
    }

    @Test
    @DisplayName("화면 이름은 일치 · 불일치 · 미보고")
    void description() {
        assertThat(InstallConfirmation.CONFIRMED.getDescription()).isEqualTo("일치");
        assertThat(InstallConfirmation.MISMATCH.getDescription()).isEqualTo("불일치");
        assertThat(InstallConfirmation.UNREPORTED.getDescription()).isEqualTo("미보고");
    }
}
