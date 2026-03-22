package com.example.serverprovision.application.setting.model;

import lombok.Getter;

@Getter
public class OSInstallation extends AbstractSettingProcess {

    private final Long osMetadataId;

    public OSInstallation(Long osMetadataId) {
        super(3);
        this.osMetadataId = osMetadataId;
    }
}
