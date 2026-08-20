package com.example.serverprovision.provisioning.setting.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 볼륨 우선순위 행의 용량 순서 축 (U4-1-2) — 같은 종류 · 전송 안에서 볼륨을 어느 용량부터 세울지. */
@RequiredArgsConstructor
@Getter
public enum CapacityOrder {
    SMALLER_FIRST("작은 용량부터"),
    LARGER_FIRST("큰 용량부터");

    private final String displayName;
}
