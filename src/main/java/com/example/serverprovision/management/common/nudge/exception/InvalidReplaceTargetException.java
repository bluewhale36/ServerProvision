package com.example.serverprovision.management.common.nudge.exception;

import com.example.serverprovision.global.exception.FieldBoundBadRequestException;

/**
 * MK2 — nudge replace 시 사용자가 보낸 targetId 가 세션의 conflicts 목록에 없음.
 *
 * <p>{@link FieldBoundBadRequestException} 상속으로 응답 fieldErrors 에 매핑된다 (S4 정합).</p>
 */
public class InvalidReplaceTargetException extends FieldBoundBadRequestException {

	public InvalidReplaceTargetException(Long targetId) {
		super("replace 대상이 nudge 세션의 충돌 후보 목록에 없습니다 : " + targetId, "targetId");
	}
}
