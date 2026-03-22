package com.example.serverprovision.application.setting.model;

import com.example.serverprovision.application.setting.model.enums.SettingProcessStep;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import lombok.Getter;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "type")
@JsonSubTypes({
        @JsonSubTypes.Type(value = BasicUpdate.class, name = "BASIC_UPDATES"),
        @JsonSubTypes.Type(value = BasicSetting.class, name = "BASIC_SETTING"),
        @JsonSubTypes.Type(value = OSInstallation.class, name = "OS_INSTALLATION"),
        @JsonSubTypes.Type(value = OSSetting.class, name = "OS_SETTING")
})
public abstract class AbstractSettingProcess implements Comparable<AbstractSettingProcess> {

    private final SettingProcessStep processStep;

    protected AbstractSettingProcess(SettingProcessStep processStep) {
        this.processStep = processStep;
    }

    public final SettingProcessStep getProcessStep() {
        return processStep;
    }

    @Override
    public final int compareTo(AbstractSettingProcess o) {
        return Integer.compare(this.processStep.getOrder(), o.getProcessStep().getOrder());
    }
}
