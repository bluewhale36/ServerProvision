package com.example.serverprovision.provisioning.setting.dto.request;

import com.example.serverprovision.provisioning.setting.enums.FirmwareSelectionMode;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;

/**
 * BIOS/BMC 펌웨어 버전 선택 — {@code {"mode":"LATEST"}} 또는 {@code {"mode":"SPECIFIED","firmwareId":3}}.
 *
 * <p>"최신 = null id" 같은 의미 있는 null 대신 선택 의도를 타입으로 명시한다(Primitive Obsession 금지).
 * LATEST 해석(최신 버전 판정)은 execution 도메인의 몫이다.</p>
 */
public record FirmwareSelectionRequest(

        @NotNull(message = "펌웨어 선택 방식은 필수 값입니다.")
        FirmwareSelectionMode mode,

        Long firmwareId
) {

    @JsonCreator
    public FirmwareSelectionRequest(
            @JsonProperty("mode")       FirmwareSelectionMode mode,
            @JsonProperty("firmwareId") Long firmwareId
    ) {
        this.mode       = mode;
        this.firmwareId = firmwareId;
    }

    /** 방식과 id 의 정합 — SPECIFIED 는 id 필수, LATEST 는 id 없음(모호한 계약 차단). */
    @AssertTrue(message = "직접 지정은 펌웨어 ID가 필수이며, 최신 버전은 ID를 보낼 수 없습니다.")
    public boolean isModeConsistent() {
        if (mode == null) return true;  // mode 자체의 @NotNull 위반이 이미 보고된다 — 중복 오류 방지.
        return (mode == FirmwareSelectionMode.SPECIFIED) == (firmwareId != null);
    }

    public boolean isLatest() {
        return mode == FirmwareSelectionMode.LATEST;
    }
}
