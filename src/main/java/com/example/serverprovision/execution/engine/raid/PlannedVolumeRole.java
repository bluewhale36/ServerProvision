package com.example.serverprovision.execution.engine.raid;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 계획 항목의 판정된 역할(E3.5-2) — 입력측 {@code DiskGroupRole} 과 다른 물건이다. BY_PRIORITY 는
 * 우선순위 판정으로 소거되는 입력 값이라 여기에는 없고, OS 는 계획 전체에서 최대 1개다.
 */
@Getter
@RequiredArgsConstructor
public enum PlannedVolumeRole {

    OS("OS 영역"),
    DATA("Data 영역"),
    NONE("영역 할당 없음");

    private final String displayName;
}
