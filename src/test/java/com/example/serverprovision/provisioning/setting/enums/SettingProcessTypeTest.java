package com.example.serverprovision.provisioning.setting.enums;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

class SettingProcessTypeTest {

    @Test
    @DisplayName("OS 설치의 선행 단계는 RAID 구성이다(설치 디스크를 RAID 의 OS 영역 볼륨으로 정함)")
    void osInstallationRequiresRaidConfiguration() {
        assertThat(SettingProcessType.OS_INSTALLATION.requiredStep()).contains(SettingProcessType.RAID_CONFIGURATION);
    }

    @Test
    @DisplayName("OS 설치 말고는 선행 단계가 없다(OS 후처리 설정 포함)")
    void othersRequireNothing() {
        assertThat(Arrays.stream(SettingProcessType.values())
                .filter(type -> type != SettingProcessType.OS_INSTALLATION)
                .filter(type -> type.requiredStep().isPresent()))
                .isEmpty();
    }
}
