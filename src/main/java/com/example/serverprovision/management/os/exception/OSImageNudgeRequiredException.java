package com.example.serverprovision.management.os.exception;

import com.example.serverprovision.management.common.nudge.dto.NudgeRequiredResponse;
import com.example.serverprovision.management.common.nudge.exception.NudgeRequiredException;

/**
 * MK2 WAVE 1 — OSImage 신규 등록에서 동일 (OSName, osVersion) 이 soft-deleted / deprecated 상태로
 * 이미 존재하는 경우. 사용자 nudge 결정 (proceed / replace / cancel) 으로 흐름이 분기된다.
 *
 * <p>file payload 가 없는 메타 단독 nudge 라 {@code conflicts[].hash} 는 null. 사용자에게는
 * 휴지통/Deprecated 후보의 id · 상태 · osVersion · 등록 시각만 제시된다.</p>
 */
public class OSImageNudgeRequiredException extends NudgeRequiredException {

	public OSImageNudgeRequiredException(NudgeRequiredResponse payload) {
		super("동일한 OS 버전이 휴지통/Deprecated 에 존재합니다. (nudgeId=" + payload.nudgeId() + ")", payload);
	}
}
