package com.example.serverprovision.domain.node.model.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
@Getter
public enum JobType {
    IDLE("대기"),
    BIOS_UPDATE("BIOS 업데이트"),
    BMC_FIRMWARE_UPDATE("BMC 펌웨어 업데이트"),
    OS_INSTALLATION("OS 설치");

    private final String description;
}
