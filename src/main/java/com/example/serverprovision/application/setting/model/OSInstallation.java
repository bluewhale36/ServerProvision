package com.example.serverprovision.application.setting.model;

import com.example.serverprovision.application.setting.model.enums.SettingProcessStep;
import lombok.Getter;

@Getter
public class OSInstallation extends AbstractSettingProcess {

    public OSInstallation() {
        super(SettingProcessStep.OS_INSTALLATION);
    }
}
