package com.example.serverprovision.execution.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 프로비저닝 하위 단계(step) — <b>선언 순서가 곧 실행 순서다(ES-2 계약)</b>. 커서가 step 단위가 되면서
 * phase 진입 step 판정({@link #entryOf})과 진행 비교가 선언 순서에 기대므로, 소속 phase 의 선언 순서를
 * 거스르는 상수 추가는 금지된다(전 상수 커버리지 테스트가 빌드에서 방어). 명시 순서 필드(int) 대안은
 * ordinal 과의 이중 권위라 기각했다(ES-2 plan D-1).
 */
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
     * 진단 중 BMC(IPMI) 측 설정 작업. 원 용도({@code ipmitool} 커스텀 정보 저장)는 DEC-11(2026-07-12)로
     * 보류됐고(소비자 없음 — 재진입 자동화 불성립 · 운영자 최종 점검 프로세스 존재), <b>E1.6 의 BMC 계정
     * 표준화가 첫 실소비자</b>다 — 최초 전환 성공과 운영자 개입 필요 실패를 instant 행으로 남긴다.
     * 원 용도가 재개되면 같은 step 아래 공존한다(원장 이력 호환 · DB ENUM 값 재사용으로 DDL 0).
     */
    IPMI_SETTING(ProvisioningPhase.DIAGNOSE_LINUX),


    BIOS_UPDATING(ProvisioningPhase.FIRMWARE_UPDATING),
    BMC_UPDATING(ProvisioningPhase.FIRMWARE_UPDATING),

    BIOS_SETTING(ProvisioningPhase.FIRMWARE_SETTING),
    BMC_SETTING(ProvisioningPhase.FIRMWARE_SETTING),

    /** RAID 카드로 디스크 묶음 규칙을 집행 — BMC_SETTING 다음 · OS_INSTALLING 전(U4-1-1 v2). */
    RAID_CONFIGURATION(ProvisioningPhase.RAID_CONFIGURATION),

    OS_INSTALLING(ProvisioningPhase.OS_INSTALLING),

    OS_SETTING(ProvisioningPhase.OS_SETTING),

    /**
     * 기본 세팅이 완료된 게스트 서버를 테스트 하는 단계.
     */
    TESTING(ProvisioningPhase.TESTING);

    private final ProvisioningPhase phaseType;

    /**
     * phase 의 진입 step — 선언 순서상 그 phase 의 첫 상수(ES-2 D-1). phase 완주 후 커서를 다음 보유
     * phase 로 미리 옮겨 세울 때(pre-position) 이 메서드가 유일한 계산 지점이다. 매핑 없는 phase 는
     * 프로그램 버그(모든 phase 는 step 을 가진다)이므로 명시 예외.
     */
    public static ProvisioningPhaseStep entryOf(ProvisioningPhase phase) {
        for (ProvisioningPhaseStep step : values()) {
            if (step.phaseType == phase) {
                return step;
            }
        }
        throw new IllegalStateException("진입 step 이 없는 phase 입니다: " + phase);
    }
}
