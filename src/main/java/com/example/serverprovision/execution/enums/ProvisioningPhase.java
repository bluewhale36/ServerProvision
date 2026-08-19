package com.example.serverprovision.execution.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
@Getter
public enum ProvisioningPhase {

    /**
     * 네트워크 대역 할당, 게스트 서버 DB 등록 등 최초 단계.
     */
    BOOTSTRAPPING("부트스트래핑"),
    /**
     * 진단 리눅스를 적재하여 활용하는 단계.
     */
    DIAGNOSE_LINUX("진단 리눅스"),
    /**
     * BIOS 또는 BMC 를 업데이트하는 단계.
     */
    FIRMWARE_UPDATING("펌웨어 업데이트"),
    /**
     * BIOS 또는 BMC 를 세팅하는 단계.
     */
    FIRMWARE_SETTING("펌웨어 설정"),
    /**
     * RAID 카드로 디스크 묶음 규칙을 집행하는 단계(U4-1-1 v2 — 정의서 단계 {@code RAID_CONFIGURATION} 에 대응).
     * executor 는 아직 없다 — 이 phase 에 이르면 dispatch 6행 HOLD 로 대기한다(E 슬라이스가 채운다).
     */
    RAID_CONFIGURATION("RAID 구성"),
    OS_INSTALLING("OS 설치"),
    OS_SETTING("OS 설정"),
    TESTING("테스트");

    private final String description;
}
