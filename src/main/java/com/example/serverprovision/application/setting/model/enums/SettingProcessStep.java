package com.example.serverprovision.application.setting.model.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum SettingProcessStep {

    BASIC_UPDATE(1, "BIOS/BMC 업데이트"),
    BASIC_SETTING(2, "BIOS 설정"),
//    BASIC_TEST(3, "기본 테스트"),
    OS_INSTALLATION(4, "OS 설치"),
    OS_SETTING(5, "OS 설정");

    private final int order;
    private final String description;
}
