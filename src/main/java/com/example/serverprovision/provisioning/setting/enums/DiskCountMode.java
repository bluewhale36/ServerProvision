package com.example.serverprovision.provisioning.setting.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 디스크 묶음 규칙의 개수 축 — 정확히 n 개 또는 n 개 이상 (U4-1 토론 E3).
 * {@code AT_LEAST} 는 "그 스펙이 n 개 이상 있으면 있는 대로 다 묶는다" 다(E19 — RAID5 3개 이상 + 6장 → 볼륨 1개).
 */
@RequiredArgsConstructor
@Getter
public enum DiskCountMode {
    EXACT("개"),
    AT_LEAST("개 이상");

    private final String suffix;
}
