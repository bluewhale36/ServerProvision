package com.example.serverprovision.application.setting.model;

import com.example.serverprovision.application.setting.model.enums.SettingProcessStep;

public class BasicUpdate extends AbstractSettingProcess {


    protected BasicUpdate(SettingProcessStep processStep) {
        super(SettingProcessStep.BASIC_UPDATE);
    }
}
