package com.example.serverprovision.domain.os.model.installation;

import com.example.serverprovision.domain.os.model.enums.OSName;
import com.example.serverprovision.domain.os.model.OSTemplate;
import lombok.Getter;

import java.util.List;

@Getter
public abstract class OSInstallation extends OSTemplate {

    protected OSInstallation(OSName compatibleOS, List<String> compatibleOSVersion) {
        super(compatibleOS, compatibleOSVersion);
    }

    public abstract String getKickstartScript();
}
