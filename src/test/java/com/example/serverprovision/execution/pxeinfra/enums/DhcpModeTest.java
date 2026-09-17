package com.example.serverprovision.execution.pxeinfra.enums;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** R16 D-2 — 모드가 필수 집합과 표기를 정한다(UI 차단 · Validator · invariant 의 SSOT). */
class DhcpModeTest {

    @Test
    @DisplayName("AUTHORITATIVE 만 주소 배정 필드를 요구한다")
    void requiresAddressing() {
        assertThat(DhcpMode.AUTHORITATIVE.requiresAddressing()).isTrue();
        assertThat(DhcpMode.PROXY.requiresAddressing()).isFalse();
    }

    @Test
    @DisplayName("표기 — 자체 DHCP · proxyDHCP (Q-4 권장안)")
    void labels() {
        assertThat(DhcpMode.AUTHORITATIVE.label()).isEqualTo("자체 DHCP");
        assertThat(DhcpMode.PROXY.label()).isEqualTo("proxyDHCP");
    }
}
