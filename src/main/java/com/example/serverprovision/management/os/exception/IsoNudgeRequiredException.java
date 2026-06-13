package com.example.serverprovision.management.os.exception;

import com.example.serverprovision.global.registration.FailureDisposition;
import com.example.serverprovision.global.registration.RegistrationFailure;
import com.example.serverprovision.management.common.nudge.dto.NudgeRequiredResponse;
import com.example.serverprovision.management.common.nudge.exception.NudgeRequiredException;

/**
 * MK2 — ISO 업로드 후처리에서 해시 충돌이 발견되었고, 충돌 후보가 모두 soft-deleted 또는 deprecated 인
 * 경우. 사용자 confirm 으로 진행 여부를 결정해야 한다.
 *
 * <p>WAVE 1 리팩터 — {@link NudgeRequiredException} 추상 super 상속. advice 는 super 매칭만으로
 * 본 sub-class 도 자동 매핑된다.</p>
 *
 * <p>R1-4-4 — {@link RegistrationFailure} 를 <b>등록계 nudge 예외인 본 클래스에만</b> 부착한다(base 가 아님 —
 * restore/intent 등 비-등록 nudge 까지 "등록 후처리 처분" 의미가 번지지 않도록). 처분은 nudgeId 를 보유한
 * {@link FailureDisposition.Nudge} — Runner 가 {@code NUDGE_REQUIRED:{nudgeId}} marker 로 직렬화한다.</p>
 */
public class IsoNudgeRequiredException extends NudgeRequiredException implements RegistrationFailure {

	public IsoNudgeRequiredException(NudgeRequiredResponse payload) {
		super("동일한 해시의 자원이 휴지통/Deprecated 에 존재합니다. (nudgeId=" + payload.nudgeId() + ")", payload);
	}

	/**
	 * 단계 A (intent path 충돌) 생성자.
	 */
	public IsoNudgeRequiredException(String message, NudgeRequiredResponse payload) {
		super(message, payload);
	}

	/** nudge 결정 대기 — 임시 파일 보존 + nudge marker(nudgeId, UUID). */
	@Override
	public FailureDisposition disposition() {
		return new FailureDisposition.Nudge(payload().nudgeId());
	}
}
