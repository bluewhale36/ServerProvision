package com.example.serverprovision.provisioning.assignment.mapper;

import com.example.serverprovision.execution.enums.ProvisioningPhase;
import com.example.serverprovision.provisioning.assignment.vo.OwnedPhases;
import com.example.serverprovision.provisioning.setting.enums.SettingProcessType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * {@link SettingProcessPhaseMapper} 단위 — 전 상수 커버리지 + 1:1 정확 매핑 + union.
 */
class SettingProcessPhaseMapperTest {

    @Test
    @DisplayName("전 SettingProcessType 상수가 phase 매핑을 가진다(누락 = IllegalState)")
    void everyProcessType_hasMapping() {
        for (SettingProcessType type : SettingProcessType.values()) {
            assertThatCode(() -> SettingProcessPhaseMapper.phaseOf(type)).doesNotThrowAnyException();
        }
    }

    @Test
    @DisplayName("매핑은 identity 아닌 명시 1:1 (4 상수)")
    void mapping_isExact() {
        assertThat(SettingProcessPhaseMapper.phaseOf(SettingProcessType.BASIC_UPDATE))
                .isEqualTo(ProvisioningPhase.FIRMWARE_UPDATING);
        assertThat(SettingProcessPhaseMapper.phaseOf(SettingProcessType.BASIC_SETTING))
                .isEqualTo(ProvisioningPhase.FIRMWARE_SETTING);
        assertThat(SettingProcessPhaseMapper.phaseOf(SettingProcessType.OS_INSTALLATION))
                .isEqualTo(ProvisioningPhase.OS_INSTALLING);
        assertThat(SettingProcessPhaseMapper.phaseOf(SettingProcessType.OS_SETTING))
                .isEqualTo(ProvisioningPhase.OS_SETTING);
    }

    @Test
    @DisplayName("toOwnedPhases 는 타입 집합을 union 해 선언 순으로 정렬한다")
    void toOwnedPhases_unionsAndSorts() {
        OwnedPhases owned = SettingProcessPhaseMapper.toOwnedPhases(
                EnumSet.of(SettingProcessType.OS_INSTALLATION, SettingProcessType.BASIC_UPDATE));

        assertThat(owned.asSet()).containsExactly(
                ProvisioningPhase.FIRMWARE_UPDATING, ProvisioningPhase.OS_INSTALLING);
    }

    @Test
    @DisplayName("빈 타입 집합 → 빈 ownedPhases(빈 정의서 할당)")
    void toOwnedPhases_empty() {
        OwnedPhases owned = SettingProcessPhaseMapper.toOwnedPhases(
                EnumSet.noneOf(SettingProcessType.class));

        assertThat(owned.isEmpty()).isTrue();
    }
}
