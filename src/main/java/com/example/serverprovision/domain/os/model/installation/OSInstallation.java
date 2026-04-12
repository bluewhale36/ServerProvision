package com.example.serverprovision.domain.os.model.installation;

import com.example.serverprovision.domain.os.model.enums.OSName;
import com.example.serverprovision.domain.os.model.OSTemplate;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

import java.util.List;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "osType")
@JsonSubTypes({
        @JsonSubTypes.Type(value = RockyLinuxInstallation.class, name = "ROCKY_LINUX")
})
public abstract class OSInstallation extends OSTemplate {

    protected OSInstallation(OSName compatibleOS, List<String> compatibleOSVersion) {
        super(compatibleOS, compatibleOSVersion);
    }

    public abstract String getKickstartScript();
}
