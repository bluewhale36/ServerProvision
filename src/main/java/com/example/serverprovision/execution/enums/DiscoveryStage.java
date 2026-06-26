package com.example.serverprovision.execution.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 게스트 서버에 대해 "얼마나 알고 있는지" 를 나타내는 수집 단계.
 * 프로비저닝 진행 단계({@link ProvisioningPhase})와는 별개 차원이다.
 */
@RequiredArgsConstructor
@Getter
public enum DiscoveryStage {

    IPXE_REGISTERED("iPXE 등록"),
    DIAGNOSTIC_ENRICHED("진단 정보 보강");

    private final String description;
}
