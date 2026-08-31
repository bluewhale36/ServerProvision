package com.example.serverprovision.execution.engine.raid;

/**
 * 기존 구성 처리 정책(E3.5-2) — 정의서의 "기존 구성 : 보존 / 파괴" 필수 선택 축(결정 D-7)이 E3.5-4 에서
 * 생기기 전까지는 planner 파라미터로만 존재한다. 기본값을 두지 않는다(U4-1 원칙).
 */
public enum RaidExistingConfigPolicy {

    /** 기존 볼륨이 하나라도 있으면 계획을 거절한다(EXISTING_CONFIG). */
    PRESERVE,

    /** 기존 볼륨을 전부 삭제하는 선행 명령을 계획에 넣고 전 디스크를 가용으로 본다. */
    DESTROY
}
