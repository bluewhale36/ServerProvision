package com.example.serverprovision.execution.pxeinfra.dto.response;

import com.example.serverprovision.execution.pxeinfra.apply.ApplyResult;
import com.example.serverprovision.execution.pxeinfra.entity.PxeNetworkConfig;

import java.time.LocalDateTime;

/**
 * 저장된 desired 구성과 마지막 적용 결과를 뷰로 노출한다. {@code driftFromServing} 은 "저장된 desired 가 현재
 * 서빙 중인 dnsmasq 조각과 다른가" — 마지막 적용이 REJECTED/ROLLED_BACK/RESTORE_FAILED 로 끝났으면 저장된
 * desired 가 실제로 서빙되지 않으므로 true 다. 주소 배정 필드는 PROXY 모드에서 null 이다.
 *
 * @param lastGateOutput 마지막 {@code dnsmasq --test} 원문(엔티티에 보존하지 않으므로 방금 적용한 흐름에서만 채워짐)
 */
public record PxeNetworkConfigResponse(
        String dhcpMode,
        String dhcpModeLabel,
        String subnetCidr,
        String rangeStart,
        String rangeEnd,
        String routers,
        String primaryDns,
        String secondaryDns,
        String bootServerIp,
        Long leaseSeconds,
        String domainName,
        String appliedResult,
        LocalDateTime appliedAt,
        boolean driftFromServing,
        String lastGateOutput
) {

    public static PxeNetworkConfigResponse from(PxeNetworkConfig config) {
        return from(config, null);
    }

    public static PxeNetworkConfigResponse from(PxeNetworkConfig config, String lastGateOutput) {
        ApplyResult result = config.getAppliedResult();
        boolean drift = result != null && result != ApplyResult.APPLIED;
        return new PxeNetworkConfigResponse(
                config.getDhcpMode().name(),
                config.getDhcpMode().label(),
                config.getSubnetCidr().value(),
                config.getRangeStart() == null ? null : config.getRangeStart().value(),
                config.getRangeEnd() == null ? null : config.getRangeEnd().value(),
                config.getRouters() == null ? null : config.getRouters().value(),
                config.getPrimaryDns() == null ? null : config.getPrimaryDns().value(),
                config.getSecondaryDns() == null ? null : config.getSecondaryDns().value(),
                config.getBootServerIp().value(),
                config.getLeaseSeconds() == null ? null : config.getLeaseSeconds().value(),
                config.getDomainName(),
                result == null ? null : result.name(),
                config.getAppliedAt(),
                drift,
                lastGateOutput
        );
    }
}
