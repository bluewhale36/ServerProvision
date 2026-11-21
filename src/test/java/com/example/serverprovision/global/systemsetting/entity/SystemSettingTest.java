package com.example.serverprovision.global.systemsetting.entity;

import com.example.serverprovision.global.security.springsecurity.domain.Role;
import com.example.serverprovision.global.systemsetting.enums.SystemSettingKey;
import com.example.serverprovision.global.systemsetting.vo.ProvisioningAllowedCidr;
import com.example.serverprovision.global.systemsetting.vo.ProvisioningAllowedRange;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class SystemSettingTest {

    @Test
    @DisplayName("최소 권한 SUPER_ADMIN: SUPER_ADMIN 통과 · ADMIN · USER 거절 · 권한 없음 거절")
    void superAdminOnly() {
        SystemSetting setting = SystemSetting.create(SystemSettingKey.PROVISIONING_ALLOWED_RANGE);
        assertThat(setting.getMinRole()).isEqualTo(Role.SUPER_ADMIN);
        assertThat(setting.isAccessibleBy(Set.of(Role.SUPER_ADMIN))).isTrue();
        assertThat(setting.isAccessibleBy(Set.of(Role.ADMIN, Role.USER))).isFalse();
        assertThat(setting.isAccessibleBy(Set.of(Role.USER))).isFalse();
        assertThat(setting.isAccessibleBy(Set.of())).isFalse();
    }

    @Test
    @DisplayName("상위 등급은 하위 최소 권한을 통과한다(Role.implied)")
    void higherRolePasses() {
        assertThat(SystemSetting.isAccessible(Role.USER, Set.of(Role.USER))).isTrue();
        assertThat(SystemSetting.isAccessible(Role.USER, Set.of(Role.ADMIN))).isTrue();
        assertThat(SystemSetting.isAccessible(Role.ADMIN, Set.of(Role.SUPER_ADMIN))).isTrue();
    }

    @Test
    @DisplayName("값 변경")
    void changeValue() {
        SystemSetting setting = SystemSetting.create(SystemSettingKey.PROVISIONING_ALLOWED_RANGE);
        assertThat(setting.getValue()).isNull();
        setting.changeValue(new ProvisioningAllowedRange(ProvisioningAllowedCidr.parse("10.1.1.0/24")));
        assertThat(setting.getValue().display()).isEqualTo("10.1.1.0/24");
    }
}
