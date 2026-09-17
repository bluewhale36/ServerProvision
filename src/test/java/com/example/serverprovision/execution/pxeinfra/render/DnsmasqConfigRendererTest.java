package com.example.serverprovision.execution.pxeinfra.render;

import com.example.serverprovision.execution.pxeinfra.entity.PxeNetworkConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static com.example.serverprovision.execution.pxeinfra.PxeNetworkConfigFixtures.authoritative;
import static com.example.serverprovision.execution.pxeinfra.PxeNetworkConfigFixtures.proxy;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * R16 — dnsmasq 조각 렌더러의 골든 검증. 자체 DHCP 는 보조 DNS · 도메인 유무 4조합, proxyDHCP 는 한 벌을 정본 문자열과
 * 바이트 단위로 대조하고, 같은 입력이면 두 번 호출해도 동일함을 확인한다(결정성 규율 — 드리프트 판정이 이 결정성에 기댄다).
 * 골든은 plan §4-4 · §4-5 의 줄 순서다.
 */
class DnsmasqConfigRendererTest {

    private final DnsmasqConfigRenderer renderer = new DnsmasqConfigRenderer();

    private static final String COMMON_HEAD = """
            # Managed by ServerProvision. Do not edit by hand.
            port=0
            bind-interfaces
            listen-address=10.0.2.2
            log-dhcp
            dhcp-authoritative
            dhcp-range=10.0.2.100,10.0.2.200,255.255.255.0,600s
            dhcp-option=option:router,10.0.2.1
            """;

    private static final String COMMON_TAIL = """
            dhcp-match=set:efi,option:client-arch,7
            dhcp-match=set:efi,option:client-arch,9
            dhcp-userclass=set:ipxe,iPXE
            tag-if=set:rom,tag:efi,tag:!ipxe
            dhcp-boot=tag:rom,ipxe.efi,,10.0.2.2
            dhcp-boot=tag:ipxe,boot.ipxe,,10.0.2.2
            """;

    @Test
    @DisplayName("자체 DHCP 골든 — 보조 DNS 有 · 도메인 有")
    void authoritative_secondaryDns_and_domainName() {
        assertThat(renderer.render(authoritative("8.8.4.4", "prov.example.com"))).isEqualTo(COMMON_HEAD
                + "dhcp-option=option:dns-server,8.8.8.8,8.8.4.4\n"
                + "dhcp-option=option:domain-name,prov.example.com\n"
                + COMMON_TAIL);
    }

    @Test
    @DisplayName("자체 DHCP 골든 — 보조 DNS 有 · 도메인 無")
    void authoritative_secondaryDns_only() {
        assertThat(renderer.render(authoritative("8.8.4.4", null))).isEqualTo(COMMON_HEAD
                + "dhcp-option=option:dns-server,8.8.8.8,8.8.4.4\n"
                + COMMON_TAIL);
    }

    @Test
    @DisplayName("자체 DHCP 골든 — 보조 DNS 無 · 도메인 有")
    void authoritative_domainName_only() {
        assertThat(renderer.render(authoritative(null, "prov.example.com"))).isEqualTo(COMMON_HEAD
                + "dhcp-option=option:dns-server,8.8.8.8\n"
                + "dhcp-option=option:domain-name,prov.example.com\n"
                + COMMON_TAIL);
    }

    @Test
    @DisplayName("자체 DHCP 골든 — 보조 DNS 無 · 도메인 無")
    void authoritative_neither() {
        assertThat(renderer.render(authoritative(null, null))).isEqualTo(COMMON_HEAD
                + "dhcp-option=option:dns-server,8.8.8.8\n"
                + COMMON_TAIL);
    }

    @Test
    @DisplayName("proxyDHCP 골든 — 주소 배정 줄 없음 · dhcp-range proxy · ROM · iPXE 모두 pxe-service(BC_EFI + x86-64_EFI · dhcp-boot")
    void proxy_golden() {
        assertThat(renderer.render(proxy())).isEqualTo("""
                # Managed by ServerProvision. Do not edit by hand.
                port=0
                bind-interfaces
                listen-address=10.1.1.17
                log-dhcp
                dhcp-no-override
                dhcp-range=10.1.1.0,proxy,255.255.255.0
                dhcp-match=set:efi,option:client-arch,7
                dhcp-match=set:efi,option:client-arch,9
                dhcp-userclass=set:ipxe,iPXE
                tag-if=set:rom,tag:efi,tag:!ipxe
                pxe-prompt="ServerProvision",0
                pxe-service=tag:rom,BC_EFI,"ServerProvision PXE",ipxe.efi,10.1.1.17
                pxe-service=tag:rom,x86-64_EFI,"ServerProvision PXE",ipxe.efi,10.1.1.17
                pxe-service=tag:ipxe,BC_EFI,"ServerProvision iPXE",boot.ipxe,10.1.1.17
                pxe-service=tag:ipxe,x86-64_EFI,"ServerProvision iPXE",boot.ipxe,10.1.1.17
                """);
    }

    @Test
    @DisplayName("어느 모드에도 Continuous · dhcpd 문법이 섞이지 않는다(데몬 교체의 잔존 0)")
    void noLegacySyntax() {
        for (PxeNetworkConfig config : new PxeNetworkConfig[] {authoritative("8.8.4.4", "x.example"), proxy()}) {
            String rendered = renderer.render(config);
            assertThat(rendered).doesNotContain("subnet ").doesNotContain("next-server").doesNotContain("filename");
            assertThat(rendered).endsWith("\n");
        }
    }

    @Test
    @DisplayName("결정성 — 같은 입력을 두 번 렌더하면 바이트 단위로 동일")
    void deterministic_sameInputSameOutput() {
        PxeNetworkConfig config = authoritative("8.8.4.4", "prov.example.com");
        assertThat(renderer.render(config)).isEqualTo(renderer.render(config));
        assertThat(renderer.render(proxy())).isEqualTo(renderer.render(proxy()));
    }

    @Test
    @DisplayName("빈 조각 — 머리말 + 구성 없음 안내(복원본)")
    void emptyFragment() {
        assertThat(renderer.renderEmpty()).isEqualTo(
                "# Managed by ServerProvision. Do not edit by hand.\n# (구성 없음 — 유효한 빈 조각)\n");
    }
}
