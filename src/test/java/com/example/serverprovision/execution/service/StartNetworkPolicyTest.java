package com.example.serverprovision.execution.service;

import com.example.serverprovision.execution.entity.HostNicBinding;
import com.example.serverprovision.execution.enums.IpSource;
import com.example.serverprovision.execution.enums.StartNetworkBlockKind;
import com.example.serverprovision.execution.repository.GuestServerDetailRepository;
import com.example.serverprovision.execution.repository.HostNicBindingRepository;
import com.example.serverprovision.execution.vo.IpAddressVO;
import com.example.serverprovision.execution.vo.MacAddressVO;
import com.example.serverprovision.execution.vo.StartNetworkBlock;
import com.example.serverprovision.global.systemsetting.service.SystemSettingService;
import com.example.serverprovision.global.systemsetting.vo.ProvisioningAllowedCidr;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class StartNetworkPolicyTest {

    @Mock SystemSettingService systemSettingService;
    @Mock GuestServerDetailRepository detailRepository;
    @Mock HostNicBindingRepository nicRepository;
    @InjectMocks StartNetworkPolicy policy;

    private static final IpAddressVO IN_BMC = IpAddressVO.of("10.1.1.21");

    private static HostNicBinding nic(String ip, boolean primary) {
        return HostNicBinding.builder().id(UUID.randomUUID()).macAddress(MacAddressVO.of("aa:bb:cc:dd:ee:0" + (primary ? 1 : 2)))
                .ipAddress(IpAddressVO.of(ip)).ipSource(IpSource.DHCP).isPrimary(primary).build();
    }

    private static List<StartNetworkBlockKind> kinds(List<StartNetworkBlock> blocks) {
        return blocks.stream().map(StartNetworkBlock::kind).toList();
    }

    @BeforeEach
    void range() {
        given(systemSettingService.provisioningAllowedRange()).willReturn(Optional.of(ProvisioningAllowedCidr.parse("10.1.1.0/24")));
    }

    @Test
    @DisplayName("판정 NIC 은 primary 만: 보조 NIC 이 밖이어도 통과")
    void primaryOnly() {
        assertThat(policy.blocksOf(IN_BMC, List.of(nic("10.1.1.101", true), nic("192.168.0.9", false)))).isEmpty();
        assertThat(kinds(policy.blocksOf(IN_BMC, List.of(nic("172.16.3.40", true), nic("10.1.1.5", false)))))
                .containsExactly(StartNetworkBlockKind.LAN_OUTSIDE);
    }

    @Test
    @DisplayName("primary 가 없으면 LAN 모름")
    void noPrimary() {
        assertThat(kinds(policy.blocksOf(IN_BMC, List.of(nic("10.1.1.5", false)))))
                .containsExactly(StartNetworkBlockKind.LAN_UNKNOWN);
    }

    @Test
    @DisplayName("HF37 — primary NIC 의 IP 가 비어 있으면 500 이 아니라 LAN 모름")
    void primaryWithoutIp() {
        HostNicBinding noIp = HostNicBinding.builder().id(UUID.randomUUID()).macAddress(MacAddressVO.of("aa:bb:cc:dd:ee:01"))
                .ipSource(IpSource.DHCP).isPrimary(true).build();
        assertThat(kinds(policy.blocksOf(IN_BMC, List.of(noIp, nic("10.1.1.5", false)))))
                .containsExactly(StartNetworkBlockKind.LAN_UNKNOWN);
    }

    @Test
    @DisplayName("가드용: 상세 · NIC 를 저장소에서 읽는다(상세 없음 = BMC 모름)")
    void byServerId() {
        UUID id = UUID.randomUUID();
        given(detailRepository.findByGuestServer_Id(id)).willReturn(Optional.empty());
        given(nicRepository.findAllByServerIdOrderByPrimary(id)).willReturn(List.of(nic("10.1.1.101", true)));
        assertThat(kinds(policy.blocksOf(id))).containsExactly(StartNetworkBlockKind.BMC_UNKNOWN);
    }
}
