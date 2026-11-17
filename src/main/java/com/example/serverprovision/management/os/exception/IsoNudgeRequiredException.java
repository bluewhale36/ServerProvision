package com.example.serverprovision.management.os.exception;

import com.example.serverprovision.management.common.nudge.dto.NudgeRequiredResponse;
import com.example.serverprovision.management.common.nudge.exception.NudgeRequiredException;

/**
 * ISO 업로드 전 중복 확인 창 — 같은 OS 의 같은 경로 · 같은 내용 휴지통 후보가 있어 사용자의 3택을 기다린다.
 *
 * <p>WAVE 1 리팩터 — {@link NudgeRequiredException} 추상 super 상속. advice 는 super 매칭만으로 본 sub-class 도 자동 매핑된다.
 * HF30-2 — 배경 등록 단계에서 던지던 생성자와 등록 실패 처분(RegistrationFailure)은 없앴다. 이 예외는 이제 업로드 전에만 난다.</p>
 */
public class IsoNudgeRequiredException extends NudgeRequiredException {

	public IsoNudgeRequiredException(String message, NudgeRequiredResponse payload) {
		super(message, payload);
	}
}
