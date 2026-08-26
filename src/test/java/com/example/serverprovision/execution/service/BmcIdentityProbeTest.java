package com.example.serverprovision.execution.service;

import com.example.serverprovision.execution.engine.firmware.BmcIdentity;
import com.example.serverprovision.execution.engine.firmware.FirmwareUpdateProvider;
import com.example.serverprovision.execution.entity.GuestServerDetail;
import com.example.serverprovision.execution.vo.IpAddressVO;
import com.example.serverprovision.execution.vo.MacAddressVO;
import com.example.serverprovision.global.redfish.RedfishTarget;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

/**
 * E3-1 D-6 — E2-2 Guard 에서 추출한 phase 무관 판정. 도달 불가일 때만 주소를 다시 찾고, 불일치는 손대지 않고
 * 돌려준다(도달 불가는 상태, 불일치는 사건). 원장 기록은 여기 없다 — phase 별 가드의 몫.
 */
@ExtendWith(MockitoExtension.class)
class BmcIdentityProbeTest {

    private static final RedfishTarget TARGET = new RedfishTarget("10.10.0.51", "QG260700082");

    @Mock BmcAddressRediscovery rediscovery;
    @Mock FirmwareUpdateProvider provider;
    @InjectMocks BmcIdentityProbe probe;

    @Test
    @DisplayName("일치 · 불일치는 그대로 돌려주고 주소를 다시 찾지 않는다")
    void matchedAndMismatchedPassThrough() {
        GuestServerDetail detail = detail();

        given(provider.verifyIdentity(any(), any())).willReturn(BmcIdentity.MATCHED);
        assertThat(probe.probe(provider, TARGET, "QG260700082", detail, "t")).isEqualTo(BmcIdentity.MATCHED);

        given(provider.verifyIdentity(any(), any())).willReturn(BmcIdentity.MISMATCHED);
        assertThat(probe.probe(provider, TARGET, "QG260700082", detail, "t")).isEqualTo(BmcIdentity.MISMATCHED);

        then(rediscovery).shouldHaveNoInteractions();
        assertThat(detail.getBmcIp()).isEqualTo(IpAddressVO.of("10.10.0.51"));
    }

    @Test
    @DisplayName("도달 불가 — 같은 MAC 이 다른 주소를 받았으면 상세를 갱신한다(다음 주기가 새 주소로 본다)")
    void unreachableUpdatesChangedAddress() {
        given(provider.verifyIdentity(any(), any())).willReturn(BmcIdentity.UNREACHABLE);
        given(rediscovery.currentAddressOf(MacAddressVO.of("00:1f:c6:e2:1b:01")))
                .willReturn(Optional.of(IpAddressVO.of("10.10.0.77")));
        GuestServerDetail detail = detail();

        assertThat(probe.probe(provider, TARGET, "QG260700082", detail, "t")).isEqualTo(BmcIdentity.UNREACHABLE);
        assertThat(detail.getBmcIp()).isEqualTo(IpAddressVO.of("10.10.0.77"));
    }

    @Test
    @DisplayName("도달 불가 — 찾은 주소가 같거나 없으면 상세는 그대로다")
    void unreachableKeepsAddressWhenSameOrMissing() {
        given(provider.verifyIdentity(any(), any())).willReturn(BmcIdentity.UNREACHABLE);
        GuestServerDetail detail = detail();

        given(rediscovery.currentAddressOf(any())).willReturn(Optional.of(IpAddressVO.of("10.10.0.51")));
        probe.probe(provider, TARGET, "QG260700082", detail, "t");
        assertThat(detail.getBmcIp()).isEqualTo(IpAddressVO.of("10.10.0.51"));

        given(rediscovery.currentAddressOf(any())).willReturn(Optional.empty());
        assertThat(probe.probe(provider, TARGET, "QG260700082", detail, "t")).isEqualTo(BmcIdentity.UNREACHABLE);
        assertThat(detail.getBmcIp()).isEqualTo(IpAddressVO.of("10.10.0.51"));
    }

    @Test
    @DisplayName("도달 불가 — 상세가 없으면 재발견 없이 돌려준다(갱신할 곳이 없다)")
    void unreachableWithoutDetailSkipsRediscovery() {
        given(provider.verifyIdentity(any(), any())).willReturn(BmcIdentity.UNREACHABLE);

        assertThat(probe.probe(provider, TARGET, "QG260700082", null, "t")).isEqualTo(BmcIdentity.UNREACHABLE);
        then(rediscovery).shouldHaveNoInteractions();
    }

    private static GuestServerDetail detail() {
        return GuestServerDetail.builder()
                .bmcIp(IpAddressVO.of("10.10.0.51"))
                .bmcMac(MacAddressVO.of("00:1f:c6:e2:1b:01"))
                .boardSerial("QG260700082")
                .build();
    }
}
