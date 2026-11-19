package com.example.serverprovision.management.os.dto.response;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** S23-1 — OS 등록 선택지: 지원 OS 상수 하나가 한 줄이고, 버전 칸에 채울 값과 잠금 여부를 상수의 버전 규칙에서 읽는다. */
class OsRegistrationOptionTest {

    @Test
    @DisplayName("지원 상수만 선언 순서대로 나오고 미지원 상수는 없다")
    void registrable_onlySupportedConstantsInOrder() {
        List<OsRegistrationOption> options = OsRegistrationOption.registrable();

        assertThat(options).extracting(OsRegistrationOption::label).containsExactly(
                "Rocky Linux 8", "Rocky Linux 9", "Rocky Linux 10",
                "Red Hat Enterprise Linux 8", "Red Hat Enterprise Linux 9", "Red Hat Enterprise Linux 10",
                "Windows Server 2025", "Ubuntu");
    }

    @Test
    @DisplayName("RHEL 계열은 '메이저.' 을 채우고, Windows Server 2025 는 '2025' 를 잠가 채우고, Ubuntu 는 비운다")
    void versionPrefillAndLock() {
        List<OsRegistrationOption> options = OsRegistrationOption.registrable();

        assertThat(options.get(1).versionPrefill()).isEqualTo("9.");
        assertThat(options.get(1).versionLocked()).isFalse();
        assertThat(options.get(6).versionPrefill()).isEqualTo("2025");
        assertThat(options.get(6).versionLocked()).isTrue();
        assertThat(options.get(7).versionPrefill()).isEmpty();
        assertThat(options.get(7).versionLocked()).isFalse();
    }
}
