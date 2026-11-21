package com.example.serverprovision.execution.pxeinfra.service;

import com.example.serverprovision.global.systemsetting.spi.ProvisioningRangeFollower;
import com.example.serverprovision.global.systemsetting.vo.ProvisioningAllowedCidr;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;

/** dnsmasq 서브넷은 허용 대역을 따르지만 "저장 및 적용" 을 다시 해야 조각에 반영된다(HF35). 자동 재적용은 하지 않는다. */
@Component
@RequiredArgsConstructor
public class PxeNetworkRangeFollower implements ProvisioningRangeFollower {

    static final String NOTICE = "dnsmasq 에 반영하려면 저장 및 적용을 다시 해야 합니다.";

    private final PxeNetworkConfigService configService;

    @Override
    public Optional<String> reapplyNotice(ProvisioningAllowedCidr current) {
        return configService.load()
                .filter(config -> config.subnetDiffersFrom(current.subnet()))
                .map(config -> NOTICE);
    }
}
