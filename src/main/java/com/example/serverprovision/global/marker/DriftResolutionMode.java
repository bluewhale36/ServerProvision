package com.example.serverprovision.global.marker;

/**
 * S6-2-1 — drift 종류별 시스템 해결 방식 3단.
 *
 * <p>과거 {@code boolean autoApplicable} 1축은 "버튼 노출"과 "스캔 무인 자동 자격"을 겸해
 * MANUAL(사용자 확인 후 시스템이 해결) 개념을 표현할 수 없었다 — 위험해서 무인 자동은 안 되지만
 * 시스템이 해결 자체는 할 수 있는 종류(TRASH_LOST 의 row 정리 등)가 "해결 없음"과 같은 취급이 되어
 * "중요한 drift 일수록 시스템이 못 건드린다"는 문제를 만들었다.</p>
 *
 * <p>boolean 2개(manuallyResolvable + autoApplicable) 분리는 {@code manual=false·auto=true} 불법
 * 조합이 타입으로 표현 가능해 비채택 — 단일 enum 이 불법 상태를 원천 차단한다.</p>
 *
 * <ul>
 *   <li>{@link #NONE} — 시스템 해결 없음. 안내({@code recommendedAction})만.</li>
 *   <li>{@link #MANUAL} — 사용자 확인([적용] 버튼) 후 시스템이 해결. 스캔 무인 적용 불가.</li>
 *   <li>{@link #AUTO} — MANUAL 에 더해 스캔 무인 적용 자격
 *       ({@code reconciliation.auto-apply.kinds} 옵트인 시 실제 발동).</li>
 * </ul>
 */
public enum DriftResolutionMode {
	NONE, MANUAL, AUTO
}
