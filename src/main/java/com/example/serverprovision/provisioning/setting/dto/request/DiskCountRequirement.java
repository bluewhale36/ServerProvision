package com.example.serverprovision.provisioning.setting.dto.request;

import com.example.serverprovision.provisioning.setting.enums.DiskCountMode;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * 디스크 묶음 규칙의 개수 축 — 개 · 개씩 · 개 이상({@link DiskCountMode}, U4-1-1 → E3.5-7-a 3값).
 *
 * <p>값은 <b>묶음 하나에 들어가는 디스크 수</b>다 — RAID 규칙과 RAID 없음 규칙에서 같은 뜻이다(U4-1-1 D12).
 * 하한 1 은 여기서, 레벨별 최소 디스크 수(RAID1 은 2 등)는 카드의 캐시 유무가 관여하므로
 * {@code DiskGroupRules} 가 판정한다.</p>
 */
public record DiskCountRequirement(

        @NotNull(message = "개수 선택 방식은 필수 값입니다.")
        DiskCountMode mode,

        @Min(value = 1, message = "디스크 개수는 1 이상이어야 합니다.")
        int value
) {

    @JsonCreator
    public DiskCountRequirement(
            @JsonProperty("mode")  DiskCountMode mode,
            // boxed + null-coalesce: Jackson 3 FAIL_ON_NULL_FOR_PRIMITIVES 기본 활성 대응 — 누락은 0 으로 두어 @Min 이 잡는다.
            @JsonProperty("value") Integer value
    ) {
        this(mode, value != null ? value : 0);
    }

    /** 화면 표기 — {@code 2개} · {@code 2개씩} · {@code 3개 이상}. */
    public String toDisplay() {
        return mode == null ? String.valueOf(value) : value + mode.getSuffix();
    }
}
