package com.example.serverprovision.execution.pxeinfra;

import com.example.serverprovision.execution.pxeinfra.entity.PxeNetworkConfig;
import com.example.serverprovision.execution.pxeinfra.enums.DhcpMode;
import com.example.serverprovision.execution.pxeinfra.vo.LeaseSeconds;
import com.example.serverprovision.execution.pxeinfra.vo.SubnetCidr;
import com.example.serverprovision.execution.vo.IpAddressVO;

/**
 * pxeinfra 테스트 공용 픽스처(E1-I-3-c → R16) — 렌더러 · 적용 서비스 · 영역 테스트가 공유하는 유효한 desired 를 만든다.
 * 골든/상태기계 검증이 입력 잡음 없이 관심 축(모드 · 보조 DNS · 도메인 유무 · 명령 결과)만 흔들 수 있게 한다.
 */
public final class PxeNetworkConfigFixtures {

    private PxeNetworkConfigFixtures() {
    }

    /** 자체 DHCP 모드의 표준 유효 desired. secondaryDns · domainName 유무를 인자로 흔든다. */
    public static PxeNetworkConfig authoritative(String secondaryDns, String domainName) {
        return PxeNetworkConfig.create(
                DhcpMode.AUTHORITATIVE,
                SubnetCidr.of("10.0.2.0/24"),
                IpAddressVO.of("10.0.2.100"),
                IpAddressVO.of("10.0.2.200"),
                IpAddressVO.of("10.0.2.1"),
                IpAddressVO.of("8.8.8.8"),
                secondaryDns == null ? null : IpAddressVO.of(secondaryDns),
                IpAddressVO.of("10.0.2.2"),
                LeaseSeconds.of(600),
                domainName);
    }

    /** 종전 이름 호환 — 보조 DNS · 도메인 유무를 흔드는 자체 DHCP desired. */
    public static PxeNetworkConfig config(String secondaryDns, String domainName) {
        return authoritative(secondaryDns, domainName);
    }

    /** 보조 DNS · 도메인 모두 있는 자체 DHCP 완전본. */
    public static PxeNetworkConfig full() {
        return authoritative("8.8.4.4", "prov.example.com");
    }

    /** proxyDHCP desired — 사내망 게스트 대역 10.1.1.0/24 · 부트 서버 10.1.1.17(spv-01). 주소 배정 필드는 없다. */
    public static PxeNetworkConfig proxy() {
        return PxeNetworkConfig.create(
                DhcpMode.PROXY,
                SubnetCidr.of("10.1.1.0/24"),
                null, null, null, null, null,
                IpAddressVO.of("10.1.1.17"),
                null, null);
    }
}
