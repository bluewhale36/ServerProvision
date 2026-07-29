package com.example.serverprovision.execution.pxeinfra;

import com.example.serverprovision.execution.pxeinfra.entity.PxeNetworkConfig;
import com.example.serverprovision.execution.pxeinfra.vo.LeaseSeconds;
import com.example.serverprovision.execution.pxeinfra.vo.SubnetCidr;
import com.example.serverprovision.execution.vo.IpAddressVO;

/**
 * E1-I-3-c 테스트 공용 픽스처 — 렌더러·적용 서비스 테스트가 공유하는 유효한 {@link PxeNetworkConfig} desired 를
 * 만든다. 골든/상태기계 검증이 입력 잡음 없이 관심 축(보조 DNS·도메인 유무, 명령 결과)만 흔들 수 있게 한다.
 */
public final class PxeNetworkConfigFixtures {

    private PxeNetworkConfigFixtures() {
    }

    /** 표준 유효 desired. secondaryDns·domainName 유무를 인자로 흔든다. */
    public static PxeNetworkConfig config(String secondaryDns, String domainName) {
        return PxeNetworkConfig.create(
                SubnetCidr.of("10.0.2.0/24"),
                IpAddressVO.of("10.0.2.100"),
                IpAddressVO.of("10.0.2.200"),
                IpAddressVO.of("10.0.2.1"),
                IpAddressVO.of("8.8.8.8"),
                secondaryDns == null ? null : IpAddressVO.of(secondaryDns),
                IpAddressVO.of("10.0.2.2"),
                LeaseSeconds.of(600),
                LeaseSeconds.of(7200),
                domainName);
    }

    /** 보조 DNS·도메인 모두 있는 완전본. */
    public static PxeNetworkConfig full() {
        return config("8.8.4.4", "prov.example.com");
    }
}
