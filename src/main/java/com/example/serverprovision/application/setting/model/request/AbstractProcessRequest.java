package com.example.serverprovision.application.setting.model.request;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "type")
@JsonSubTypes({
        @JsonSubTypes.Type(value = BasicUpdateRequest.class,      name = "BASIC_UPDATES"),
        @JsonSubTypes.Type(value = BasicSettingRequest.class,     name = "BASIC_SETTING"),
        @JsonSubTypes.Type(value = OSInstallationRequest.class,   name = "OS_INSTALLATION"),
        @JsonSubTypes.Type(value = OSSettingRequest.class,        name = "OS_SETTING")
})
public abstract class AbstractProcessRequest {
}
