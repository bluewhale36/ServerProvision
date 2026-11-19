package com.example.serverprovision.execution.vo;

import com.example.serverprovision.execution.enums.StartNetworkBlockKind;
import com.example.serverprovision.global.systemsetting.vo.ProvisioningAllowedCidr;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static com.example.serverprovision.execution.enums.StartNetworkBlockKind.BMC_OUTSIDE;
import static com.example.serverprovision.execution.enums.StartNetworkBlockKind.BMC_UNKNOWN;
import static com.example.serverprovision.execution.enums.StartNetworkBlockKind.LAN_OUTSIDE;
import static com.example.serverprovision.execution.enums.StartNetworkBlockKind.LAN_UNKNOWN;
import static com.example.serverprovision.execution.enums.StartNetworkBlockKind.RANGE_UNSET;
import static org.assertj.core.api.Assertions.assertThat;

class StartNetworkBlocksTest {

    private static final Optional<ProvisioningAllowedCidr> RANGE = Optional.of(ProvisioningAllowedCidr.parse("10.1.1.0/24"));
    private static final IpAddressVO IN_BMC = IpAddressVO.of("10.1.1.21");
    private static final IpAddressVO IN_LAN = IpAddressVO.of("10.1.1.101");
    private static final IpAddressVO OUT = IpAddressVO.of("192.168.10.21");

    private static List<StartNetworkBlockKind> kinds(Optional<ProvisioningAllowedCidr> range, IpAddressVO bmc, IpAddressVO lan) {
        return StartNetworkBlocks.judge(range, bmc, lan).stream().map(StartNetworkBlock::kind).toList();
    }

    @Test
    @DisplayName("둘 다 대역 안 → 막지 않음")
    void inside() {
        assertThat(kinds(RANGE, IN_BMC, IN_LAN)).isEmpty();
    }

    @Test
    @DisplayName("각각 밖 · 모름")
    void single() {
        assertThat(kinds(RANGE, OUT, IN_LAN)).containsExactly(BMC_OUTSIDE);
        assertThat(kinds(RANGE, IN_BMC, OUT)).containsExactly(LAN_OUTSIDE);
        assertThat(kinds(RANGE, null, IN_LAN)).containsExactly(BMC_UNKNOWN);
        assertThat(kinds(RANGE, IN_BMC, null)).containsExactly(LAN_UNKNOWN);
    }

    @Test
    @DisplayName("이유를 모두 모은다(BMC 먼저)")
    void collectsAll() {
        assertThat(kinds(RANGE, OUT, OUT)).containsExactly(BMC_OUTSIDE, LAN_OUTSIDE);
        assertThat(kinds(RANGE, null, null)).containsExactly(BMC_UNKNOWN, LAN_UNKNOWN);
    }

    @Test
    @DisplayName("허용 대역이 없으면 '밖' 은 계산하지 않고 '모름' 만 함께")
    void rangeUnset() {
        assertThat(kinds(Optional.empty(), OUT, OUT)).containsExactly(RANGE_UNSET);
        assertThat(kinds(Optional.empty(), null, IN_LAN)).containsExactly(RANGE_UNSET, BMC_UNKNOWN);
    }

    @Test
    @DisplayName("문구에 관측값과 대역이 들어가고 em dash 가 없다")
    void message() {
        String msg = StartNetworkBlocks.judge(RANGE, OUT, IN_LAN).getFirst().message();
        assertThat(msg).isEqualTo("BMC 주소가 프로비저닝 허용 대역 밖에 있습니다(BMC 192.168.10.21, 허용 대역 10.1.1.0/24). "
                + "BMC 가 프로비저닝 네트워크에 연결돼 있는지 확인해야 합니다.");
        for (StartNetworkBlockKind kind : StartNetworkBlockKind.values()) {
            assertThat(kind.message("10.0.0.1", "10.1.1.0/24")).doesNotContain("—");
        }
    }
}
