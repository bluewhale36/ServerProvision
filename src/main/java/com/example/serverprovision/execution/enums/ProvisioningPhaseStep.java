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
     * 진단 리눅스가 게스트에서 실제 부팅되어 에이전트가 기동한 단계(E1-1) — "체인로드를 줬다"(서버 관점)와
     * "진짜 부팅됐다"(게스트 사실)를 원장에서 구분하는 부팅 마일스톤. agent.sh 가 체크인 직후 보고한다.
     */
    DIAGNOSTIC_BOOTING(ProvisioningPhase.DIAGNOSE_LINUX),
    /**
     * {@code dmidecode} 등의 명령으로 메인보드 시리얼번호 등의 2차 정보를 수집하는 단계.
     */
    INFORMATION_COLLECTING(ProvisioningPhase.DIAGNOSE_LINUX),
    /**
     * 수집된 2차 정보를 DB 에 추가 등록하는 단계.
     */
    INFORMATION_PERSISTING(ProvisioningPhase.DIAGNOSE_LINUX),
    /**
     * {@code ipmitool} 명령으로 커스텀 정보를 저장하는 단계.
     * <p><b>보류(DEC-11, 2026-07-12)</b> — 소비자 없음. 공정 표준상 프로비저닝 종료 후 게스트 NIC 이
     * DHCP auto 로 전환되고 BMC IP 가 고정되어 프로비저닝망 연결이 끊기므로 재진입 자동화가 성립하지
     * 않고, 사람이 최종 점검하는 업무 프로세스가 존재한다. modelName/serialNumber 는 운영자 입력
     * 필드로만 관리한다. 공정 여건이 바뀌면 재개 — 상수는 유지(원장 이력 호환).</p>
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
