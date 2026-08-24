package com.example.serverprovision.execution.service;

import com.example.serverprovision.execution.pxeinfra.inspect.DhcpLeaseReader;
import com.example.serverprovision.execution.pxeinfra.inspect.LeaseEntry;
import com.example.serverprovision.execution.pxeinfra.inspect.LeaseSnapshot;
import com.example.serverprovision.execution.pxeinfra.spi.LeaseBindingState;
import com.example.serverprovision.execution.vo.IpAddressVO;
import com.example.serverprovision.execution.vo.MacAddressVO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

/**
 * E2-2 D-11 — 저장된 주소로 닿지 않을 때 같은 MAC 이 지금 쓰는 주소를 찾는다.
 *
 * <p>lease 파일을 읽는 인프라는 이미 있었고 화면과 자산 대시보드만 쓰고 있었다 — 집행 경로가
 * 첫 소비자다. DHCP 를 우리가 운영하지 않는 환경에서는 비어 있어 재발견이 되지 않으며, 그때는
 * 신원 확인이 도달 불가로 남아 시한이 정리한다.</p>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class BmcAddressRediscoveryTest {

    private static final MacAddressVO BMC_MAC = MacAddressVO.of("00:1f:c6:e2:1b:01");
    private static final Instant T = Instant.parse("2026-08-23T03:00:00Z");

    @Mock DhcpLeaseReader leaseReader;
    @InjectMocks BmcAddressRediscovery rediscovery;

    @Test
    @DisplayName("같은 MAC 의 활성 lease 주소를 돌려준다")
    void findsCurrentAddress() {
        given(leaseReader.read()).willReturn(new LeaseSnapshot(List.of(
                lease("10.10.0.77", BMC_MAC, T.plusSeconds(3600), LeaseBindingState.ACTIVE))));

        assertThat(rediscovery.currentAddressOf(BMC_MAC)).contains(IpAddressVO.of("10.10.0.77"));
    }

    @Test
    @DisplayName("같은 MAC 이 여러 개면 가장 늦게 끝나는 것이 현행이다(갱신마다 블록이 쌓인다)")
    void picksLatestLease() {
        given(leaseReader.read()).willReturn(new LeaseSnapshot(List.of(
                lease("10.10.0.51", BMC_MAC, T.plusSeconds(600), LeaseBindingState.ACTIVE),
                lease("10.10.0.77", BMC_MAC, T.plusSeconds(3600), LeaseBindingState.ACTIVE))));

        assertThat(rediscovery.currentAddressOf(BMC_MAC)).contains(IpAddressVO.of("10.10.0.77"));
    }

    @Test
    @DisplayName("만료된 lease 는 현재 주소가 아니다")
    void ignoresExpiredLease() {
        given(leaseReader.read()).willReturn(new LeaseSnapshot(List.of(
                lease("10.10.0.51", BMC_MAC, T.minusSeconds(60), LeaseBindingState.EXPIRED))));

        assertThat(rediscovery.currentAddressOf(BMC_MAC)).isEmpty();
    }

    @Test
    @DisplayName("다른 MAC 의 lease 는 보지 않는다 — 남의 주소를 우리 것으로 삼지 않는다")
    void ignoresOtherMac() {
        given(leaseReader.read()).willReturn(new LeaseSnapshot(List.of(
                lease("10.10.0.90", MacAddressVO.of("00:1f:c6:e2:1b:99"), T.plusSeconds(3600),
                        LeaseBindingState.ACTIVE))));

        assertThat(rediscovery.currentAddressOf(BMC_MAC)).isEmpty();
    }

    @Test
    @DisplayName("MAC 을 모르거나 lease 가 비어 있으면 찾지 못한다(DHCP 를 우리가 운영하지 않는 환경)")
    void emptyWhenNoInput() {
        given(leaseReader.read()).willReturn(LeaseSnapshot.empty());

        assertThat(rediscovery.currentAddressOf(null)).isEmpty();
        assertThat(rediscovery.currentAddressOf(BMC_MAC)).isEmpty();
    }

    private static LeaseEntry lease(String ip, MacAddressVO mac, Instant ends, LeaseBindingState state) {
        return new LeaseEntry(IpAddressVO.of(ip), mac, T.minusSeconds(600), ends, state);
    }
}
