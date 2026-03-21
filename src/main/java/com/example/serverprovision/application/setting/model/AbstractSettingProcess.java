package com.example.serverprovision.application.setting.model;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "type")
@JsonSubTypes({
        @JsonSubTypes.Type(value = BasicUpdates.class, name = "BASIC_UPDATES"),
        @JsonSubTypes.Type(value = BasicSetting.class, name = "BASIC_SETTING"),
        @JsonSubTypes.Type(value = OSInstallation.class, name = "OS_INSTALLATION"),
        @JsonSubTypes.Type(value = OSSetting.class, name = "OS_SETTING")
})
public abstract class AbstractSettingProcess implements Comparable<AbstractSettingProcess> {

    private final int PROCESSING_ORDER;

    protected AbstractSettingProcess(int processingOrder) {
        this.PROCESSING_ORDER = processingOrder;
    }

    public final int getProcessingOrder() {
        return PROCESSING_ORDER;
    }

    @Override
    public final int compareTo(AbstractSettingProcess o) {
        return Integer.compare(this.PROCESSING_ORDER, o.PROCESSING_ORDER);
    }
}
