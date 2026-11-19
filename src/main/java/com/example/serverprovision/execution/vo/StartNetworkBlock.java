package com.example.serverprovision.execution.vo;

import com.example.serverprovision.execution.enums.StartNetworkBlockKind;
import com.example.serverprovision.global.systemsetting.vo.ProvisioningAllowedCidr;

/** 개시를 막는 네트워크 사유 하나. {@code observed} · {@code allowedCidr} 는 사유에 따라 null. */
public record StartNetworkBlock(StartNetworkBlockKind kind, IpAddressVO observed, ProvisioningAllowedCidr allowedCidr) {

    public String message() {
        return kind.message(observed == null ? null : observed.value(),
                allowedCidr == null ? null : allowedCidr.value());
    }
}
