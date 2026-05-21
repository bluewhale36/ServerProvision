package com.example.serverprovision.management.common.nudge.exception;

import com.example.serverprovision.global.exception.ConflictException;
import com.example.serverprovision.management.common.nudge.dto.NudgeRequiredResponse;

/**
 * MK2 — nudge 결정 대기 상태를 의미하는 도메인 예외의 공통 super.
 *
 * <p>모든 도메인 (BIOS / BMC / Subprogram / ISO / OS Image / Board Model) 의 nudge required 예외가
 * 본 클래스를 상속하며, 단일 advice 핸들러가 polymorphic 하게 매핑한다 — sub-class 별 핸들러를
 * 늘리는 분기문 무분별 확장 anti-pattern 을 회피하기 위함 (CLAUDE.md §조건 분기문 legacy 무분별
 * 확장 절대 지양 정합).</p>
 *
 * <p>HTTP status 는 {@link ConflictException} 상속으로 자동 409 매핑. body 포맷은 일반
 * {@code ApiErrorResponse} 가 아닌 {@link NudgeRequiredResponse} (nudgeId · conflicts · expiresAt)
 * 이므로, advice 가 본 추상 super 매칭으로 payload 를 그대로 응답 body 로 사용한다.</p>
 */
public abstract class NudgeRequiredException extends ConflictException {

	private final NudgeRequiredResponse payload;

	protected NudgeRequiredException(String message, NudgeRequiredResponse payload) {
		super(message);
		this.payload = payload;
	}

	public NudgeRequiredResponse payload() {
		return payload;
	}
}
