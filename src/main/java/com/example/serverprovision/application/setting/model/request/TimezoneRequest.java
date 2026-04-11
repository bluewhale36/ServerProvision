package com.example.serverprovision.application.setting.model.request;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;

@Getter
public class TimezoneRequest {

    @NotBlank(message = "시간대는 필수 입력값입니다.")
    private final String timezone;

    private final boolean isUTC;

    @JsonCreator
    public TimezoneRequest(
            @JsonProperty("timezone") String timezone,
            @JsonProperty("isUTC")    boolean isUTC) {
        this.timezone = timezone;
        this.isUTC    = isUTC;
    }
}
