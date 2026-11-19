package com.example.serverprovision.global.systemsetting.enums;

import com.example.serverprovision.global.security.springsecurity.domain.Role;
import com.example.serverprovision.global.systemsetting.vo.ProvisioningAllowedRange;
import com.example.serverprovision.global.systemsetting.vo.SystemSettingValue;

/** 시스템 설정 키. 행이 없을 때의 최소 권한과 값 타입을 키가 정한다. */
public enum SystemSettingKey {

    PROVISIONING_ALLOWED_RANGE("프로비저닝 허용 대역", ProvisioningAllowedRange.class, Role.SUPER_ADMIN);

    private final String label;
    private final Class<? extends SystemSettingValue> valueType;
    private final Role defaultMinRole;

    SystemSettingKey(String label, Class<? extends SystemSettingValue> valueType, Role defaultMinRole) {
        this.label = label;
        this.valueType = valueType;
        this.defaultMinRole = defaultMinRole;
    }

    public String label() {
        return label;
    }

    public Class<? extends SystemSettingValue> valueType() {
        return valueType;
    }

    public Role defaultMinRole() {
        return defaultMinRole;
    }
}
