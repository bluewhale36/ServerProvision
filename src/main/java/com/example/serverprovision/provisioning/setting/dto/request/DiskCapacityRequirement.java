package com.example.serverprovision.provisioning.setting.dto.request;

import com.example.serverprovision.provisioning.setting.enums.CapacityRequirementMode;
import com.example.serverprovision.provisioning.setting.enums.DiskCapacityUnit;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;

/**
 * 디스크 묶음 규칙의 용량 축 — {@code {"mode":"AUTO"}} 또는 {@code {"mode":"SPECIFIED","size":480,"unit":"GB"}} (U4-1-1).
 *
 * <p>"자동 = null 크기" 같은 의미 있는 null 대신 선택 의도를 타입으로 명시한다
 * ({@link BoardModelSelectionRequest} 관용구). 단위는 판매 표기 십진 {@link DiskCapacityUnit} 다.</p>
 */
public record DiskCapacityRequirement(

        @NotNull(message = "용량 선택 방식은 필수 값입니다.")
        CapacityRequirementMode mode,

        Long size,

        DiskCapacityUnit unit
) {

    @JsonCreator
    public DiskCapacityRequirement(
            @JsonProperty("mode") CapacityRequirementMode mode,
            @JsonProperty("size") Long size,
            @JsonProperty("unit") DiskCapacityUnit unit
    ) {
        this.mode = mode;
        this.size = size;
        this.unit = unit;
    }

    /** 방식과 값의 정합 — SPECIFIED 는 크기·단위 필수(크기 1 이상), AUTO 는 둘 다 없음. 판정 메서드는 payload 에 싣지 않는다. */
    @JsonIgnore
    @AssertTrue(message = "용량을 직접 지정하려면 1 이상의 크기와 단위가 필요하며, 자동 탐지는 값을 보낼 수 없습니다.")
    public boolean isModeConsistent() {
        if (mode == null) return true;  // mode 자체의 @NotNull 위반이 이미 보고된다.
        boolean hasValue = size != null && unit != null;
        if (mode == CapacityRequirementMode.SPECIFIED) return hasValue && size >= 1;
        return size == null && unit == null;
    }

    @JsonIgnore
    public boolean isAuto() {
        return mode == CapacityRequirementMode.AUTO;
    }

    /** 화면 표기 — {@code 자동 탐지} 또는 {@code 480 GB}. */
    public String toDisplay() {
        return isAuto() || size == null || unit == null ? "자동 탐지" : size + " " + unit.getSymbol();
    }
}
