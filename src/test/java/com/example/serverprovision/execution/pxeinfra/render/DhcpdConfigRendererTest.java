package com.example.serverprovision.execution.pxeinfra.render;

import com.example.serverprovision.execution.pxeinfra.entity.PxeNetworkConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static com.example.serverprovision.execution.pxeinfra.PxeNetworkConfigFixtures.config;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * E1-I-3-c — dhcpd 조각 렌더러의 골든 검증. secondaryDns·domainName 의 유무 4조합을 정본 문자열과 바이트 단위로
 * 대조하고, 같은 입력이면 두 번 호출해도 동일함을 확인한다(결정성 규율 — 드리프트 판정이 이 결정성에 기댄다).
 */
class DhcpdConfigRendererTest {

    private final DhcpdConfigRenderer renderer = new DhcpdConfigRenderer();

    @Test
    @DisplayName("골든 — 보조 DNS 有 · 도메인 有")
    void golden_secondaryDns_and_domainName() {
        String rendered = renderer.render(config("8.8.4.4", "prov.example.com"));

        assertThat(rendered).isEqualTo("""
                # Managed by ServerProvision. Do not edit by hand.
                subnet 10.0.2.0 netmask 255.255.255.0 {
                  range 10.0.2.100 10.0.2.200;
                  option routers 10.0.2.1;
                  option domain-name-servers 8.8.8.8, 8.8.4.4;
                  option domain-name "prov.example.com";
                  default-lease-time 600;
                  max-lease-time 7200;

                  class "pxeclients" {
                    match if substring(option vendor-class-identifier, 0, 9) = "PXEClient";
                    next-server 10.0.2.2;
                    if exists user-class and option user-class = "iPXE" {
                      filename "boot.ipxe";
                    } else {
                      filename "ipxe.efi";
                    }
                  }
                }
                """);
    }

    @Test
    @DisplayName("골든 — 보조 DNS 有 · 도메인 無")
    void golden_secondaryDns_only() {
        String rendered = renderer.render(config("8.8.4.4", null));

        assertThat(rendered).isEqualTo("""
                # Managed by ServerProvision. Do not edit by hand.
                subnet 10.0.2.0 netmask 255.255.255.0 {
                  range 10.0.2.100 10.0.2.200;
                  option routers 10.0.2.1;
                  option domain-name-servers 8.8.8.8, 8.8.4.4;
                  default-lease-time 600;
                  max-lease-time 7200;

                  class "pxeclients" {
                    match if substring(option vendor-class-identifier, 0, 9) = "PXEClient";
                    next-server 10.0.2.2;
                    if exists user-class and option user-class = "iPXE" {
                      filename "boot.ipxe";
                    } else {
                      filename "ipxe.efi";
                    }
                  }
                }
                """);
    }

    @Test
    @DisplayName("골든 — 보조 DNS 無 · 도메인 有")
    void golden_domainName_only() {
        String rendered = renderer.render(config(null, "prov.example.com"));

        assertThat(rendered).isEqualTo("""
                # Managed by ServerProvision. Do not edit by hand.
                subnet 10.0.2.0 netmask 255.255.255.0 {
                  range 10.0.2.100 10.0.2.200;
                  option routers 10.0.2.1;
                  option domain-name-servers 8.8.8.8;
                  option domain-name "prov.example.com";
                  default-lease-time 600;
                  max-lease-time 7200;

                  class "pxeclients" {
                    match if substring(option vendor-class-identifier, 0, 9) = "PXEClient";
                    next-server 10.0.2.2;
                    if exists user-class and option user-class = "iPXE" {
                      filename "boot.ipxe";
                    } else {
                      filename "ipxe.efi";
                    }
                  }
                }
                """);
    }

    @Test
    @DisplayName("골든 — 보조 DNS 無 · 도메인 無")
    void golden_neither() {
        String rendered = renderer.render(config(null, null));

        assertThat(rendered).isEqualTo("""
                # Managed by ServerProvision. Do not edit by hand.
                subnet 10.0.2.0 netmask 255.255.255.0 {
                  range 10.0.2.100 10.0.2.200;
                  option routers 10.0.2.1;
                  option domain-name-servers 8.8.8.8;
                  default-lease-time 600;
                  max-lease-time 7200;

                  class "pxeclients" {
                    match if substring(option vendor-class-identifier, 0, 9) = "PXEClient";
                    next-server 10.0.2.2;
                    if exists user-class and option user-class = "iPXE" {
                      filename "boot.ipxe";
                    } else {
                      filename "ipxe.efi";
                    }
                  }
                }
                """);
    }

    @Test
    @DisplayName("결정성 — 같은 입력을 두 번 렌더하면 바이트 단위로 동일")
    void deterministic_sameInputSameOutput() {
        PxeNetworkConfig config = config("8.8.4.4", "prov.example.com");
        assertThat(renderer.render(config)).isEqualTo(renderer.render(config));
    }

    @Test
    @DisplayName("항상 trailing newline 으로 끝난다")
    void endsWithNewline() {
        assertThat(renderer.render(config(null, null))).endsWith("}\n");
    }
}
