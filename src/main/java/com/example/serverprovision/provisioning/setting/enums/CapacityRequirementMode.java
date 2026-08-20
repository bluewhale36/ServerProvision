package com.example.serverprovision.provisioning.setting.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 디스크 묶음 규칙의 용량 축 선택 방식 — 자동 탐지 또는 값 지정 (U4-1-1). */
@RequiredArgsConstructor
@Getter
public enum CapacityRequirementMode {
    AUTO("자동 탐지"),
    SPECIFIED("직접 지정");

    private final String displayName;
}
