package com.example.serverprovision.management.common.nudge;

/**
 * MK2 — nudge confirm 사용자 결정.
 *
 * <p>업로드 후 해시 충돌이 발견되면 클라이언트에게 {@code 409 + nudgeId} 를 회신하고, 사용자가 modal
 * 에서 3택 중 하나를 선택해 confirm 엔드포인트를 호출한다. 본 enum 은 컨트롤러 ↔ 서비스 사이에서
 * 그 결정을 표현한다.</p>
 *
 * <ul>
 *   <li>{@link #PROCEED} — 기존 자원 유지 + 임시 row 를 ACTIVE 로 confirm</li>
 *   <li>{@link #REPLACE} — 기존 명시적 purge (별도 트랜잭션) 후 임시 row 를 ACTIVE 로 confirm</li>
 *   <li>{@link #CANCEL} — 임시 row + 업로드 파일 cleanup</li>
 * </ul>
 */
public enum NudgeAction {
    PROCEED,
    REPLACE,
    CANCEL
}
