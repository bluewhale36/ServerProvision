package com.example.serverprovision.global.lifecycle;

/**
 * MK3-2 (DCM3-2.3) — softDelete reject modal 의 사용자 액션.
 *
 * <ul>
 *   <li>{@link #CORRECT_PATH_THEN_DELETE} (default · 권장) — reconciliation 강제 실행 → PATH_DRIFT 자동 적용 → 정정된 위치에서 정상 trash mv</li>
 *   <li>{@link #FORCED_CLEAR} — 자원이 진짜 분실됐을 때. row hard-delete (MK3-1 의 applyGhostClear 경로 재사용)</li>
 * </ul>
 *
 * <p>CANCEL 은 별도 액션이 아니라 클라이언트가 token 을 사용하지 않고 자연 만료시키는 흐름이므로
 * enum 에 포함하지 않음.</p>
 */
public enum DeleteAction {

    CORRECT_PATH_THEN_DELETE,

    FORCED_CLEAR
}
