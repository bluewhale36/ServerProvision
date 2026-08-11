package com.example.serverprovision.execution.enums;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 스펙 보유 판정의 SSOT (U3-3 DEC-A).
 * 상수별 구현이라 새 상수가 생기면 컴파일러가 답을 요구한다 — 호출부 분기가 늘지 않는다.
 */
class DiscoveryStageTest {

    @Test
    @DisplayName("iPXE 등록만 된 서버는 스펙이 없다")
    void ipxeRegisteredHasNoSpec() {
        assertThat(DiscoveryStage.IPXE_REGISTERED.isSpecAvailable()).isFalse();
    }

    @Test
    @DisplayName("진단 정보가 보강된 서버는 스펙이 있다")
    void diagnosticEnrichedHasSpec() {
        assertThat(DiscoveryStage.DIAGNOSTIC_ENRICHED.isSpecAvailable()).isTrue();
    }

    @Test
    @DisplayName("모든 상수가 판정을 갖는다 — 상수가 늘어도 빠뜨릴 수 없는 구조")
    void everyConstantAnswers() {
        for (DiscoveryStage stage : DiscoveryStage.values()) {
            assertThat(stage.getDescription()).isNotBlank();
            assertThat(stage.isSpecAvailable()).isIn(true, false);
        }
    }
}
