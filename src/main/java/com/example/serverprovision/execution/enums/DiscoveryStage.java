package com.example.serverprovision.execution.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 게스트 서버에 대해 "얼마나 알고 있는지" 를 나타내는 수집 단계.
 * 프로비저닝 진행 단계({@link ProvisioningPhase})와는 별개 차원이다.
 *
 * <p><b>스펙 보유 판정의 SSOT</b>(U3-3 DEC-A) — "이 서버의 하드웨어 스펙을 알고 있는가" 는
 * {@link #isSpecAvailable()} 하나로만 답한다. 목록의 스펙 그룹 자격, 엔진의 수집 지시 판단,
 * 후속 U3-4 의 그룹 할당 가드가 모두 이 메서드를 부른다. 판정을 상수별 구현으로 둔 이유는
 * 수집 축이 늘어날 때 호출부의 분기가 아니라 <b>새 상수가 자기 답을 들고 오게</b> 하기 위함이다.</p>
 */
@RequiredArgsConstructor
@Getter
public enum DiscoveryStage {

    IPXE_REGISTERED("iPXE 등록") {
        @Override
        public boolean isSpecAvailable() {
            return false;
        }
    },
    DIAGNOSTIC_ENRICHED("진단 정보 보강") {
        @Override
        public boolean isSpecAvailable() {
            return true;
        }
    };

    private final String description;

    /** 하드웨어 스펙을 이미 수집해 두었는가 — 스펙 그룹에 들어갈 자격의 판정. */
    public abstract boolean isSpecAvailable();
}
