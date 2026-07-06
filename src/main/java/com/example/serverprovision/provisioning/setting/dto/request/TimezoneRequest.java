package com.example.serverprovision.provisioning.setting.dto.request;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;

// boolean 명시 getter 의 @JsonProperty 는 직렬화 키를 wire 계약("isUTC")에 고정하기 위함 —
// Jackson 기본 명명은 is-접두를 벗겨 "utc" 로 써서 pre-fill JSON 이 폼 계약과 어긋난다.

/**
 * 설치 시스템의 타임존 설정 (리눅스 스코프 — IANA 문자열 + RTC-UTC 플래그는 Kickstart
 * {@code timezone --utc} 의 표현이다. Windows 슬라이스는 자체 타임존 표현을 갖는다).
 */
@Getter
public class TimezoneRequest {

    @NotBlank(message = "시간대는 필수 입력값입니다.")
    private final String timezone;

    /** 하드웨어 시계(RTC)를 UTC 로 간주할지 여부. */
    private final boolean isUTC;

    @JsonCreator
    public TimezoneRequest(
            @JsonProperty("timezone") String timezone,
            // boxed + null-coalesce: Jackson 3 은 FAIL_ON_NULL_FOR_PRIMITIVES 가 기본 활성이라
            // primitive 파라미터는 키 누락만으로 요청 전체가 깨진다 — 누락=false(레거시 의미)를 유지한다.
            @JsonProperty("isUTC")    Boolean isUTC
    ) {
        this.timezone = timezone;
        this.isUTC    = isUTC != null && isUTC;
    }

    @JsonProperty("isUTC")
    public boolean isUTC() {
        return isUTC;
    }
}
