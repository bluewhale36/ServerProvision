package com.example.serverprovision.global.systemsetting.entity;

import com.example.serverprovision.global.entity.BaseTimeEntity;
import com.example.serverprovision.global.security.springsecurity.domain.Role;
import com.example.serverprovision.global.systemsetting.converter.SystemSettingValueConverter;
import com.example.serverprovision.global.systemsetting.enums.SystemSettingKey;
import com.example.serverprovision.global.systemsetting.vo.SystemSettingValue;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.Collection;

/** 시스템 설정 한 키 = 한 행(U7). 값은 키별 타입의 JSON, 접근은 키마다 둔 최소 권한으로 판정한다. */
@Entity
@Table(name = "system_setting")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SystemSetting extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "setting_key", nullable = false, unique = true, length = 64)
    private SystemSettingKey settingKey;

    /** null = 아직 저장되지 않음. */
    @Convert(converter = SystemSettingValueConverter.class)
    @Column(name = "value_json", columnDefinition = "json")
    private SystemSettingValue value;

    @Enumerated(EnumType.STRING)
    @Column(name = "min_role", nullable = false, length = 16)
    private Role minRole;

    private SystemSetting(SystemSettingKey settingKey, Role minRole) {
        this.settingKey = settingKey;
        this.minRole = minRole;
    }

    /** 행이 없는 키를 처음 저장할 때. 최소 권한은 키의 기본값. */
    public static SystemSetting create(SystemSettingKey key) {
        return new SystemSetting(key, key.defaultMinRole());
    }

    public void changeValue(SystemSettingValue newValue) {
        if (newValue != null && !settingKey.valueType().isInstance(newValue)) {
            throw new IllegalArgumentException(
                    "설정 키와 값 타입이 맞지 않습니다: " + settingKey + " ← " + newValue.getClass().getSimpleName());
        }
        this.value = newValue;
    }

    /** 화면 노출과 저장 가드가 함께 부르는 접근 판정. ADMIN 은 USER 를 포함한다({@link Role#implied}). */
    public boolean isAccessibleBy(Collection<Role> roles) {
        return isAccessible(minRole, roles);
    }

    public static boolean isAccessible(Role minRole, Collection<Role> roles) {
        return Role.expand(roles).contains(minRole);
    }
}
