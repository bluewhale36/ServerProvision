package com.example.serverprovision.management.common.nudge.exception;

import com.example.serverprovision.global.exception.FieldBoundBadRequestException;

/**
 * MK2 — nudge replace 시 사용자가 보낸 targetId 가 세션의 conflicts 목록에 없음.
 *
 * <p>{@link FieldBoundBadRequestException} 상속으로 응답 fieldErrors 에 매핑된다 (S4 정합).</p>
 */
public class InvalidReplaceTargetException extends FieldBoundBadRequestException {

	public InvalidReplaceTargetException(Long targetId) {
		super("고른 기존 자원이 중복 확인 목록에 없습니다. 다시 골라야 합니다.", "targetId");
	}
}
