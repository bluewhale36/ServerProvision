package com.example.serverprovision.provisioning.dto.request;

import com.example.serverprovision.global.redfish.RedfishResetType;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;

/**
 * 게스트 전원 제어 요청 (E1.5) — 화면 · REST 로 허용되는 것은 4 종(OQ1 확정).
 * {@code POWER_CYCLE} 은 켜짐 검증의 폴백 전용 Java API 라 여기서는 거절한다.
 */
public record PowerResetRequest(
        @NotNull(message = "resetType 은 필수 값입니다.")
        RedfishResetType resetType
) {

    @JsonIgnore
    @AssertTrue(message = "PowerCycle 은 화면 · REST 로 쓸 수 없습니다 — 켜짐 검증의 폴백 전용입니다.")
    public boolean isScreenAllowed() {
        return resetType != RedfishResetType.POWER_CYCLE;
    }
}
