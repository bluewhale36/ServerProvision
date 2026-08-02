package com.example.serverprovision.provisioning.assignment.enums;

/**
 * 세팅 정의서 할당 스냅샷의 파생 상태(저장 컬럼 없음 — {@code SettingAssignment.state()} 가
 * {@code consumedAt} · {@code supersededAt} 두 시각에서 도출한다. {@code GuestServer} §D4 하우스스타일).
 *
 * <ul>
 *   <li>{@code ACTIVE_UNCONSUMED} — 활성(supersededAt=null) · 미소비(consumedAt=null). 개시 전 상태.</li>
 *   <li>{@code ACTIVE_CONSUMED} — 활성 · 소비됨(개시 시 markConsumed).</li>
 *   <li>{@code SUPERSEDED} — 재할당 등으로 논리 종료(supersededAt≠null). U3-2 reaper 수거 대상.</li>
 * </ul>
 */
public enum AssignmentState {
    ACTIVE_UNCONSUMED,
    ACTIVE_CONSUMED,
    SUPERSEDED
}
