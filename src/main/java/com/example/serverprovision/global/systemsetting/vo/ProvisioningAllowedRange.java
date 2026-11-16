package com.example.serverprovision.global.systemsetting.vo;

/** {@code PROVISIONING_ALLOWED_RANGE} 의 값. 개시 판정이 BMC · LAN 주소를 이 대역과 대조한다. */
public record ProvisioningAllowedRange(ProvisioningAllowedCidr cidr) implements SystemSettingValue {

    public ProvisioningAllowedRange {
        if (cidr == null) {
            throw new IllegalArgumentException("허용 대역이 비어 있습니다.");
        }
    }

    @Override
    public String display() {
        return cidr.value();
    }
}
