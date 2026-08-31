package com.example.serverprovision.execution.engine.raid;

/**
 * 계획 산출의 결과 합(E3.5-2) — 성공(계획) 또는 거절(사유). 조회 시 재산출되는 파생물이라 저장하지 않는다.
 */
public sealed interface RaidPlanOutcome permits RaidPlan, RaidPlanRejection {
}
