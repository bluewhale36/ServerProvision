package com.example.serverprovision.application.setting.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record SettingProcess(List<AbstractSettingProcess> processList) {

    @JsonCreator
    public SettingProcess(
            @JsonProperty("processList")
            List<AbstractSettingProcess> processList
    ) {
        this.processList = processList;
    }
}
