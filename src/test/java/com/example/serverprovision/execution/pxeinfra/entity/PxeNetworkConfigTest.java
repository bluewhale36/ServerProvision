package com.example.serverprovision.execution.pxeinfra.entity;

import com.example.serverprovision.execution.pxeinfra.enums.DhcpMode;
import com.example.serverprovision.execution.pxeinfra.vo.LeaseSeconds;
import com.example.serverprovision.execution.pxeinfra.vo.SubnetCidr;
import com.example.serverprovision.execution.vo.IpAddressVO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static com.example.serverprovision.execution.pxeinfra.PxeNetworkConfigFixtures.authoritative;
import static com.example.serverprovision.execution.pxeinfra.PxeNetworkConfigFixtures.proxy;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * R16 — 모드별 invariant 안전망과 PROXY 의 주소 배정 필드 정규화. 정상 흐름은 Validator 가 먼저 막으므로 여기 도달은
 * 비정상 경로(direct POST · 변조)라 IllegalArgumentException 이 맞다.
 */
class PxeNetworkConfigTest {

    @Test
    @DisplayName("PROXY — 주소 배정 필드에 값이 와도 버린다(null 정규화 · 서브넷 · 부트 서버만 남는다)")
    void proxyDiscardsAddressing() {
        PxeNetworkConfig config = PxeNetworkConfig.create(DhcpMode.PROXY, SubnetCidr.of("10.1.1.0/24"),
                IpAddressVO.of("10.1.1.100"), IpAddressVO.of("10.1.1.200"), IpAddressVO.of("10.1.1.1"),
                IpAddressVO.of("8.8.8.8"), IpAddressVO.of("8.8.4.4"), IpAddressVO.of("10.1.1.17"),
                LeaseSeconds.of(600), "x.example");

        assertThat(config.getDhcpMode()).isEqualTo(DhcpMode.PROXY);
        assertThat(config.getRangeStart()).isNull();
        assertThat(config.getRangeEnd()).isNull();
        assertThat(config.getRouters()).isNull();
        assertThat(config.getPrimaryDns()).isNull();
        assertThat(config.getSecondaryDns()).isNull();
        assertThat(config.getLeaseSeconds()).isNull();
        assertThat(config.getDomainName()).isNull();
        assertThat(config.getSubnetCidr().value()).isEqualTo("10.1.1.0/24");
        assertThat(config.getBootServerIp().value()).isEqualTo("10.1.1.17");
    }

    @Test
    @DisplayName("AUTHORITATIVE — 범위 · 라우터 · 주 DNS · 임대가 없으면 거절")
    void authoritativeRequiresAddressing() {
        assertThatThrownBy(() -> PxeNetworkConfig.create(DhcpMode.AUTHORITATIVE, SubnetCidr.of("10.0.2.0/24"),
                null, null, null, null, null, IpAddressVO.of("10.0.2.2"), null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("자체 DHCP 모드");
    }

    @Test
    @DisplayName("AUTHORITATIVE — 범위가 서브넷을 벗어나면 거절")
    void authoritativeRangeInsideSubnet() {
        assertThatThrownBy(() -> PxeNetworkConfig.create(DhcpMode.AUTHORITATIVE, SubnetCidr.of("10.0.2.0/24"),
                IpAddressVO.of("10.0.2.100"), IpAddressVO.of("10.0.3.5"), IpAddressVO.of("10.0.2.1"),
                IpAddressVO.of("8.8.8.8"), null, IpAddressVO.of("10.0.2.2"), LeaseSeconds.of(600), null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("서브넷 경계");
    }

    @Test
    @DisplayName("두 모드 공통 — 모드 · 서브넷 · 부트 서버는 필수")
    void commonRequired() {
        assertThatThrownBy(() -> PxeNetworkConfig.create(null, SubnetCidr.of("10.0.2.0/24"),
                null, null, null, null, null, IpAddressVO.of("10.0.2.2"), null, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> PxeNetworkConfig.create(DhcpMode.PROXY, null,
                null, null, null, null, null, IpAddressVO.of("10.0.2.2"), null, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> PxeNetworkConfig.create(DhcpMode.PROXY, SubnetCidr.of("10.0.2.0/24"),
                null, null, null, null, null, null, null, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("updateDesired — 자체 DHCP 행을 PROXY 로 바꾸면 주소 배정 필드가 비워진다(모드 전환 = 조각 통째 재작성)")
    void switchingToProxyClearsAddressing() {
        PxeNetworkConfig config = authoritative("8.8.4.4", "prov.example.com");
        assertThat(config.getRangeStart()).isNotNull();

        config.updateDesired(DhcpMode.PROXY, SubnetCidr.of("10.1.1.0/24"), config.getRangeStart(), config.getRangeEnd(),
                config.getRouters(), config.getPrimaryDns(), config.getSecondaryDns(), IpAddressVO.of("10.1.1.17"),
                config.getLeaseSeconds(), config.getDomainName());

        assertThat(config.getDhcpMode()).isEqualTo(DhcpMode.PROXY);
        assertThat(config.getRangeStart()).isNull();
        assertThat(config.getLeaseSeconds()).isNull();
        assertThat(proxy().getDhcpMode()).isEqualTo(DhcpMode.PROXY);
    }
}
