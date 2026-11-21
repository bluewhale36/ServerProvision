package com.example.serverprovision.execution.service;

import com.example.serverprovision.execution.entity.GuestServerDetail;
import com.example.serverprovision.execution.entity.HostNicBinding;
import com.example.serverprovision.execution.repository.GuestServerDetailRepository;
import com.example.serverprovision.execution.repository.HostNicBindingRepository;
import com.example.serverprovision.execution.vo.IpAddressVO;
import com.example.serverprovision.execution.vo.StartNetworkBlock;
import com.example.serverprovision.execution.vo.StartNetworkBlocks;
import com.example.serverprovision.global.systemsetting.service.SystemSettingService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * 개시 네트워크 판정의 재료 로더(U7). 판정 자체는 {@link StartNetworkBlocks#judge} 다.
 * 재료가 허용 대역(시스템 설정) · BMC 주소(상세) · primary NIC(바인딩) 세 곳에 흩어져 있어 엔티티 메서드로 둘 수 없다
 * (선례 {@link RetryPolicy}).
 */
@Component
@RequiredArgsConstructor
public class StartNetworkPolicy {

    private final SystemSettingService systemSettingService;
    private final GuestServerDetailRepository guestServerDetailRepository;
    private final HostNicBindingRepository hostNicBindingRepository;

    /** 개시 가드용. */
    public List<StartNetworkBlock> blocksOf(UUID serverId) {
        IpAddressVO bmcIp = guestServerDetailRepository.findByGuestServer_Id(serverId)
                .map(GuestServerDetail::getBmcIp).orElse(null);
        return blocksOf(bmcIp, hostNicBindingRepository.findAllByServerIdOrderByPrimary(serverId));
    }

    /** 상세 화면용. 상세 · NIC 를 이미 불러온 호출자가 쓴다. */
    public List<StartNetworkBlock> blocksOf(IpAddressVO bmcIp, Collection<HostNicBinding> nics) {
        return StartNetworkBlocks.judge(systemSettingService.provisioningAllowedRange(), bmcIp, primaryIpOf(nics));
    }

    /** primary NIC(최초 PXE 등록 NIC)의 IP. primary 가 없으면 데이터 손상으로 보고 '모름'. */
    private static IpAddressVO primaryIpOf(Collection<HostNicBinding> nics) {
        return nics.stream().filter(HostNicBinding::isPrimary).map(HostNicBinding::getIpAddress)
                .findFirst().orElse(null);
    }
}
