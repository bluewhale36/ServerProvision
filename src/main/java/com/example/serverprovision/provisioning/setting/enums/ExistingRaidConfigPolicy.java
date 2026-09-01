package com.example.serverprovision.provisioning.setting.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * RAID 구성 단계의 "기존 구성 처리" 축(E3.5-4, 0-3 결정 D-7) — 기본값 없는 필수 선택.
 * 실행측 {@code RaidExistingConfigPolicy} 와 1:1 이지만 정의서 어휘(화면 라벨)는 U 도메인이 소유한다
 * (payload 직렬화 계약이 실행 타입에 묶이지 않게 — plan 결정 1).
 */
@Getter
@RequiredArgsConstructor
public enum ExistingRaidConfigPolicy {

    /** 카드에 남아 있는 외부 기존 구성을 지키고, 있으면 집행을 실패로 거절한다. */
    PRESERVE("기존 구성 보존"),

    /** 기존 구성을 전부 삭제한 뒤 이 정의서대로 재구성한다. */
    DESTROY("기존 구성 파괴");

    private final String displayName;
}
