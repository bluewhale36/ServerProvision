package com.example.serverprovision.management.common.nudge.exception;

import com.example.serverprovision.global.exception.ConflictException;

import java.util.UUID;

/**
 * MK2 — nudge_session 5분 TTL 만료. 사용자가 modal 을 너무 오래 띄워두고 결정 안 한 케이스.
 */
public class NudgeSessionExpiredException extends ConflictException {

	/** R2-6/R2-5 — frontend 가 만료를 메시지 문자열 대신 안정 머신 code 로 판정하도록 응답에 동봉하는 code. */
	public static final String CODE = "NUDGE_SESSION_EXPIRED";

	public NudgeSessionExpiredException(UUID nudgeId) {
		super("nudge 세션이 만료되었습니다. 다시 업로드해주세요. (nudgeId=" + nudgeId + ")");
	}
}
