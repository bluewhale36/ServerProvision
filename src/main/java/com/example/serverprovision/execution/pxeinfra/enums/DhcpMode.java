package com.example.serverprovision.execution.pxeinfra.enums;

import com.example.serverprovision.execution.pxeinfra.entity.PxeNetworkConfig;

/**
 * PXE 인프라의 DHCP 모드(R16 D-2) — 한 데몬(dnsmasq)이 두 모드를 낸다. 어느 필드가 필수인지({@link #requiresAddressing()})는
 * 화면의 필드 그룹 차단 · Bean Validation 의 필수 집합 · 엔티티 invariant 셋이 함께 부르는 단일 SSOT 다.
 * 조각의 모드별 줄(주소 배정 · ROM 부팅 응답)은 상수가 든다 — 렌더러에 분기가 없다.
 */
public enum DhcpMode {

    /** 이 서버가 IP · 라우터 · DNS 까지 배정한다(실습 LAN). dnsmasq 가 authoritative 로 응답한다. */
    AUTHORITATIVE("자체 DHCP") {
        @Override
        public boolean requiresAddressing() {
            return true;
        }

        @Override
        public void appendAddressing(StringBuilder sb, PxeNetworkConfig config) {
            sb.append("dhcp-authoritative\n");
            sb.append("dhcp-range=").append(config.getRangeStart().value())
                    .append(',').append(config.getRangeEnd().value())
                    .append(',').append(config.getSubnetCidr().netmask())
                    .append(',').append(config.getLeaseSeconds().value()).append("s\n");
            sb.append("dhcp-option=option:router,").append(config.getRouters().value()).append('\n');
            sb.append("dhcp-option=option:dns-server,").append(config.getPrimaryDns().value());
            if (config.getSecondaryDns() != null) {
                sb.append(',').append(config.getSecondaryDns().value());
            }
            sb.append('\n');
            if (config.getDomainName() != null) {
                sb.append("dhcp-option=option:domain-name,").append(config.getDomainName()).append('\n');
            }
        }

        @Override
        public void appendRomBoot(StringBuilder sb, PxeNetworkConfig config, String bootFilename) {
            // ROM 은 option 67(filename)으로 부팅한다 — 종전 dhcpd 의 filename 과 같은 길.
            sb.append("dhcp-boot=tag:rom,").append(bootFilename).append(",,")
                    .append(config.getBootServerIp().value()).append('\n');
        }
    },

    /**
     * IP 는 사내 DHCP 가 주고 이 서버는 부팅 위치만 답한다(사내망 · PXE 규격의 proxyDHCP). 주소 배정 필드는 쓰지 않는다.
     * proxy 응답에서 ROM 은 PXE 메뉴 프로토콜(option 43 · 포트 4011)을 쓰므로 {@code pxe-service} 로 답한다(D-5).
     */
    PROXY("proxyDHCP") {
        @Override
        public boolean requiresAddressing() {
            return false;
        }

        @Override
        public void appendAddressing(StringBuilder sb, PxeNetworkConfig config) {
            sb.append("dhcp-no-override\n");
            sb.append("dhcp-range=").append(config.getSubnetCidr().networkAddress())
                    .append(",proxy,").append(config.getSubnetCidr().netmask()).append('\n');
        }

        @Override
        public void appendRomBoot(StringBuilder sb, PxeNetworkConfig config, String bootFilename) {
            sb.append("pxe-prompt=\"ServerProvision\",0\n");
            sb.append("pxe-service=tag:rom,X86-64_EFI,\"ServerProvision PXE\",").append(bootFilename)
                    .append(',').append(config.getBootServerIp().value()).append('\n');
        }
    };

    private final String label;

    DhcpMode(String label) {
        this.label = label;
    }

    /** 화면 · 대시보드 chip 표기. */
    public String label() {
        return label;
    }

    /** 주소 배정 필드(범위 · 라우터 · DNS · 임대)가 필수인가 — UI 차단 · Validator · invariant 의 SSOT. */
    public abstract boolean requiresAddressing();

    /** 조각의 주소 배정 구간(모드 선언 + range + 옵션). */
    public abstract void appendAddressing(StringBuilder sb, PxeNetworkConfig config);

    /** 조각의 ROM(첫 단) 부팅 응답 구간 — iPXE(둘째 단)의 {@code dhcp-boot} 는 두 모드 공통이라 렌더러가 붙인다. */
    public abstract void appendRomBoot(StringBuilder sb, PxeNetworkConfig config, String bootFilename);
}
