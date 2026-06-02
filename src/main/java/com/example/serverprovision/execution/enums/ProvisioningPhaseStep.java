package com.example.serverprovision.execution.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
@Getter
public enum ProvisioningPhaseStep {

    /**
     * Provisioning Server 내 DHCP 에 의해 LAN, Mgmt LAN 이 주소를 할당받는 단계.
     */
    NETWORK_ALLOCATING(ProvisioningPhase.BOOTSTRAPPING),
    /**
     * 감지된 게스트 서버가 DB 에 최초 등록되는 단계.
     */
    INIT_PERSISTING(ProvisioningPhase.BOOTSTRAPPING),

    /**
     * <code>dmidecode</code> 등의 명령으로 메인보드 시리얼번호 등의 2차 정보를 수집하는 단계.
     */
    INFORMATION_COLLECTING(ProvisioningPhase.DIAGNOSE_LINUX),
    /**
     * 수집된 2차 정보를 DB 에 추가 등록하는 단계.
     */
    INFORMATION_PERSISTING(ProvisioningPhase.DIAGNOSE_LINUX),
    /**
     * <code>ipmitool</code> 명령으로 커스텀 정보를 저장하는 단계.
     */
    IPMI_SETTING(ProvisioningPhase.DIAGNOSE_LINUX),


    BIOS_UPDATING(ProvisioningPhase.FIRMWARE_UPDATING),
    BMC_UPDATING(ProvisioningPhase.FIRMWARE_UPDATING),

    BIOS_SETTING(ProvisioningPhase.FIRMWARE_SETTING),
    BMC_SETTING(ProvisioningPhase.FIRMWARE_SETTING),

    OS_INSTALLING(ProvisioningPhase.OS_INSTALLING),

    OS_SETTING(ProvisioningPhase.OS_SETTING),

    /**
     * 기본 세팅이 완료된 게스트 서버를 테스트 하는 단계.
     */
    TESTING(ProvisioningPhase.TESTING);

    private final ProvisioningPhase phaseType;
}
