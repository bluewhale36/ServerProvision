package com.example.serverprovision.execution.vo;

import com.example.serverprovision.management.raidcard.enums.RaidChipFamily;

/**
 * RAID 카드가 보이는데 카드 뒤 인벤토리가 없을 때 "왜 못 읽었는가" 의 판정(R17 — HF22 경고의 원인 분리).
 * 근거는 진단 리눅스가 관측한 커널 드라이버 하나다(SSOT). 상수가 화면 문구 키를 소유한다.
 *
 * <p>진리표: 관측 없음(옛 보고 · 옛 에이전트) → UNKNOWN / 계열 드라이버(megaraid_sas · mpt3sas) → SUPPORTED_NOT_COLLECTED /
 * 계열 밖 드라이버(mpi3mr 등) → UNSUPPORTED_CONTROLLER / 바인딩 없음 → DRIVER_NOT_BOUND.</p>
 */
public enum RaidCollectionNotice {

    /** 관측 없음 — 종전 HF22 문구를 그대로 쓴다. */
    UNKNOWN("execution.raid-inventory.uncollected"),
    /** 지원 계열 카드인데 채집 실패 — 도구 부재 · IR 아님(NOT_IR) 등, 사유는 서버 이력에 있다. */
    SUPPORTED_NOT_COLLECTED("execution.raid-inventory.uncollected.supported"),
    /** 커널이 붙인 드라이버가 지원 계열 밖 — RAID 를 만들 수 없는 컨트롤러(예: mpi3mr HBA). */
    UNSUPPORTED_CONTROLLER("execution.raid-inventory.uncollected.unsupported-controller"),
    /** 진단 이미지 커널이 이 칩을 몰라 드라이버가 붙지 않았다 — 진단 이미지 갱신이 답이다. */
    DRIVER_NOT_BOUND("execution.raid-inventory.uncollected.driver-not-bound");

    private final String messageKey;

    RaidCollectionNotice(String messageKey) {
        this.messageKey = messageKey;
    }

    public String messageKey() {
        return messageKey;
    }

    public static RaidCollectionNotice of(KernelDriverObservation observation) {
        if (observation == null) {
            return UNKNOWN;
        }
        if (!observation.isBound()) {
            return DRIVER_NOT_BOUND;
        }
        return RaidChipFamily.fromKernelDriver(observation.driver()).isPresent()
                ? SUPPORTED_NOT_COLLECTED : UNSUPPORTED_CONTROLLER;
    }
}
