package com.example.serverprovision.global.systemsetting.dto.request;

import com.example.serverprovision.global.systemsetting.validation.ProvisioningAllowedCidrFormat;

/** 프로비저닝 허용 대역 저장 요청. 검증 규칙은 {@code ProvisioningAllowedCidr.violationOf}. */
public record ProvisioningAllowedRangeRequest(
        @ProvisioningAllowedCidrFormat String cidr
) {
}
