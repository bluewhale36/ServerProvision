package com.example.serverprovision.management.raidcard.exception;

import com.example.serverprovision.management.common.nudge.dto.NudgeRequiredResponse;
import com.example.serverprovision.management.common.nudge.exception.NudgeRequiredException;

/**
 * RAID 카드 신규 등록에서 동일 (vendor, modelName) 이 soft-deleted / deprecated 상태로 이미 존재하는
 * 경우. 사용자 nudge 결정 (proceed / replace / cancel) 으로 흐름이 분기된다 (MK2 선례).
 */
public class RaidCardNudgeRequiredException extends NudgeRequiredException {

	public RaidCardNudgeRequiredException(NudgeRequiredResponse payload) {
		super("동일한 RAID 카드가 휴지통/Deprecated 에 존재합니다. (nudgeId=" + payload.nudgeId() + ")", payload);
	}
}
