package com.example.serverprovision.management.common.nudge.exception;

import com.example.serverprovision.global.exception.ConflictException;

/**
 * 같은 중복 확인을 다른 요청이 처리하고 있을 때(HF30-3). 예: [그래도 등록] 이 트리 해시를 계산하는 동안 다른 탭에서 온 [취소].
 * 처리 중인 요청이 끝나면 결과가 목록에 남으므로, 화면은 이 문구를 보이고 창을 닫는다.
 */
public class NudgeInProgressException extends ConflictException {

	public NudgeInProgressException() {
		super("이 중복 확인을 다른 요청이 처리하고 있습니다. 잠시 뒤 목록에서 결과를 확인해야 합니다.");
	}
}
