package com.example.serverprovision.management.common.nudge;

/**
 * MK2 — nudge 세션의 pending 데이터 공통 super.
 *
 * <p>업로드 흐름 phase 별로 보관해야 할 메타가 다르므로 sealed interface 로 분기 :
 * <ul>
 *   <li>{@link ContentNudgePayload} : 단계 B — 파일이 이미 임시 경로에 업로드된 후 해시 충돌 nudge.
 *       confirm 시 정식 경로로 이동 + entity 저장.</li>
 *   <li>{@link IntentMetaNudgePayload} : 단계 A — intent 시점 메타 키 (boardId, version 등) 충돌 nudge.
 *       파일은 아직 업로드되지 않음. confirm 시 intent token 재발급 후 사용자에게 반환 → 클라이언트가
 *       그제야 정식 업로드 시작.</li>
 * </ul>
 *
 * <p>도메인 NudgeService 의 proceed / replace 가 {@code switch (payload)} pattern matching 으로
 * 두 phase 를 분기하며, 새 phase 추가 시 sealed permits 에 추가하면 컴파일러가 누락된 case 를 강제한다.
 * if-else 분기문 무분별 확장 anti-pattern 회피 (CLAUDE.md §조건 분기문 정합).</p>
 */
public sealed interface NudgePayload permits ContentNudgePayload, IntentMetaNudgePayload, RestoreHashConflictPayload {

}
