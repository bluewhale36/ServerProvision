package com.example.serverprovision.global.redfish;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** E2.5 D-4 — best effort 의 경계는 상수 속성이 든다. 상수가 늘면 이 진리표도 같이 는다. */
class RedfishErrorTest {

    @Test
    @DisplayName("resourceSpecific — 리소스 단위 거절 셋만 참(연결 · 자격증명은 다음 호출도 같은 이유로 실패)")
    void resourceSpecificTruthTable() {
        assertThat(RedfishError.CONNECT_FAILED.resourceSpecific()).isFalse();
        assertThat(RedfishError.AUTH_FAILED.resourceSpecific()).isFalse();
        assertThat(RedfishError.PRECONDITION_FAILED.resourceSpecific()).isTrue();
        assertThat(RedfishError.NOT_FOUND.resourceSpecific()).isTrue();
        assertThat(RedfishError.PROTOCOL.resourceSpecific()).isTrue();
    }
}
