package com.example.serverprovision.execution.enums;

import lombok.Getter;

@Getter
public enum ProvisioningPhase {

    /**
     * 네트워크 대역 할당, 게스트 서버 DB 등록 등 최초 단계.
     */
    BOOTSTRAPPING,
    /**
     * 진단 리눅스를 적재하여 활용하는 단계.
     */
    DIAGNOSE_LINUX,
    /**
     * BIOS 또는 BMC 를 업데이트하는 단계.
     */
    FIRMWARE_UPDATING,
    /**
     * BIOS 또는 BMC 를 세팅하는 단계.
     */
    FIRMWARE_SETTING,
    OS_INSTALLING,
    OS_SETTING,
    TESTING
}
