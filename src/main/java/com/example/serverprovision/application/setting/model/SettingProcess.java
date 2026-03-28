package com.example.serverprovision.application.setting.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record SettingProcess(
        @NotEmpty(message = "하나 이상의 단계를 선택해야 합니다.") List<AbstractSettingProcess> processList
) {

    @JsonCreator
    public SettingProcess(
            @JsonProperty("processList")
            List<AbstractSettingProcess> processList
    ) {
        this.processList = processList;
    }
}
