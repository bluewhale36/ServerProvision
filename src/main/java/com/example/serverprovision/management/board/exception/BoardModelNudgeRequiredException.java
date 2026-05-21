package com.example.serverprovision.management.board.exception;

import com.example.serverprovision.management.common.nudge.dto.NudgeRequiredResponse;
import com.example.serverprovision.management.common.nudge.exception.NudgeRequiredException;

/**
 * MK2 WAVE 1 — BoardModel 신규 등록에서 동일 (Vendor, modelName) 이 soft-deleted / deprecated 상태로
 * 이미 존재하는 경우. 사용자 nudge 결정 (proceed / replace / cancel) 으로 흐름이 분기된다.
 */
public class BoardModelNudgeRequiredException extends NudgeRequiredException {

	public BoardModelNudgeRequiredException(NudgeRequiredResponse payload) {
		super("동일한 메인보드 모델이 휴지통/Deprecated 에 존재합니다. (nudgeId=" + payload.nudgeId() + ")", payload);
	}
}
