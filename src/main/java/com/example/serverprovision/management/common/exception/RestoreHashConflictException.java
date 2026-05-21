package com.example.serverprovision.management.common.exception;

import com.example.serverprovision.global.exception.ConflictException;

/**
 * MK3 — restore 검증 (4) 실패 : 다른 active 자원과 manifestHash 충돌.
 * <p>nudge 흐름으로 사용자에 알림. 버튼 옵션 = "복원 취소" / "그래도 복원" 두 개. "기존 자원 삭제" 옵션 없음.
 * "그래도 복원" 선택 시 trash 의 timestamp suffix 가 포함된 이름 그대로 active 트리에 mv → DB.iso_path 갱신.</p>
 */
public class RestoreHashConflictException extends ConflictException {

	public RestoreHashConflictException(String message) {
		super(message);
	}
}
