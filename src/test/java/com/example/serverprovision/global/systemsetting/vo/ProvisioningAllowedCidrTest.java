package com.example.serverprovision.global.systemsetting.vo;

import com.example.serverprovision.execution.vo.IpAddressVO;
import com.example.serverprovision.global.systemsetting.enums.AllowedCidrViolation;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProvisioningAllowedCidrTest {

    @ParameterizedTest(name = "{0} → {1}")
    @CsvSource(delimiter = '|', value = {
            "'   '           | EMPTY",
            "10.1.1.0        | FORMAT",
            "10.1.1/24       | FORMAT",
            "abc/24          | FORMAT",
            "10.1.1.256/24   | ADDRESS_INVALID",
            "1234.1.1.1/24   | ADDRESS_INVALID",
            "10.1.1.0/33     | PREFIX_OUT_OF_RANGE",
            "8.8.8.0/24      | NOT_PRIVATE",
            "172.15.0.0/16   | NOT_PRIVATE",
            "172.32.0.0/16   | NOT_PRIVATE",
            "10.0.0.0/7      | WIDER_THAN_BLOCK",
            "172.16.0.0/11   | WIDER_THAN_BLOCK",
            "192.168.0.0/15  | WIDER_THAN_BLOCK",
            "192.168.1.0/27  | TOO_NARROW",
            "10.1.1.5/24     | NOT_NETWORK_ADDRESS",
            "10.1.1.64/25    | NOT_NETWORK_ADDRESS"
    })
    @DisplayName("위반은 검사 순서대로 처음 걸린 하나")
    void violations(String raw, AllowedCidrViolation expected) {
        assertThat(ProvisioningAllowedCidr.violationOf(raw)).contains(expected);
        assertThatThrownBy(() -> ProvisioningAllowedCidr.parse(raw))
                .isInstanceOf(IllegalArgumentException.class).hasMessage(expected.message());
    }

    @Test
    @DisplayName("null 은 EMPTY")
    void nullIsEmpty() {
        assertThat(ProvisioningAllowedCidr.violationOf(null)).contains(AllowedCidrViolation.EMPTY);
    }

    @ParameterizedTest
    @ValueSource(strings = {"10.1.1.0/24", "10.0.0.0/8", "172.16.0.0/12", "172.31.255.192/26", "192.168.0.0/16", "192.168.1.0/26"})
    @DisplayName("RFC 1918 블록 셋 모두 통과, /26 까지")
    void accepts(String raw) {
        assertThat(ProvisioningAllowedCidr.violationOf(raw)).isEmpty();
        assertThat(ProvisioningAllowedCidr.parse(raw).value()).isEqualTo(raw);
    }

    @Test
    @DisplayName("앞뒤 공백은 지운다")
    void trims() {
        assertThat(ProvisioningAllowedCidr.parse("  10.1.1.0/24 ").value()).isEqualTo("10.1.1.0/24");
    }

    @Test
    @DisplayName("contains = 네트워크 주소 · 브로드캐스트 포함, 밖은 제외")
    void contains() {
        ProvisioningAllowedCidr range = ProvisioningAllowedCidr.parse("10.1.1.0/24");
        assertThat(range.contains(IpAddressVO.of("10.1.1.0"))).isTrue();
        assertThat(range.contains(IpAddressVO.of("10.1.1.255"))).isTrue();
        assertThat(range.contains(IpAddressVO.of("10.1.2.1"))).isFalse();
    }
}
