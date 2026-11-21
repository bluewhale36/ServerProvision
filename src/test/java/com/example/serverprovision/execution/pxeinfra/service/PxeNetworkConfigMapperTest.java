package com.example.serverprovision.execution.pxeinfra.service;

import com.example.serverprovision.execution.pxeinfra.dto.request.PxeNetworkConfigRequest;
import com.example.serverprovision.execution.pxeinfra.enums.DhcpMode;
import com.example.serverprovision.execution.pxeinfra.vo.SubnetCidr;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** HF35 — 서브넷은 호출자가 넘기는 허용 대역이고, 리스 범위 판정은 그 대역으로 한다. */
class PxeNetworkConfigMapperTest {

    private final PxeNetworkConfigMapper mapper = new PxeNetworkConfigMapper();
    private static final SubnetCidr ALLOWED = SubnetCidr.of("10.0.2.0/24");

    private static PxeNetworkConfigRequest authoritative(String start, String end) {
        return new PxeNetworkConfigRequest(DhcpMode.AUTHORITATIVE, start, end, "10.0.2.1", "8.8.8.8", "",
                "10.0.2.2", 600L, "");
    }

    @Test
    @DisplayName("desired 의 서브넷은 넘겨받은 허용 대역")
    void subnetFromAllowedRange() {
        assertThat(mapper.toEntity(authoritative("10.0.2.100", "10.0.2.200"), ALLOWED).getSubnetCidr()).isEqualTo(ALLOWED);
    }

    @Test
    @DisplayName("리스 범위: 대역 안 · 대역 밖 · 시작이 끝보다 큼 · proxyDHCP 는 보지 않음")
    void leaseRangeOutside() {
        assertThat(mapper.leaseRangeOutside(authoritative("10.0.2.100", "10.0.2.200"), ALLOWED)).isFalse();
        assertThat(mapper.leaseRangeOutside(authoritative("10.0.2.100", "10.0.3.5"), ALLOWED)).isTrue();
        assertThat(mapper.leaseRangeOutside(authoritative("10.0.2.200", "10.0.2.100"), ALLOWED)).isTrue();
        PxeNetworkConfigRequest proxy = new PxeNetworkConfigRequest(DhcpMode.PROXY, "", "", "", "", "", "10.1.1.17", null, "");
        assertThat(mapper.leaseRangeOutside(proxy, ALLOWED)).isFalse();
    }
}
