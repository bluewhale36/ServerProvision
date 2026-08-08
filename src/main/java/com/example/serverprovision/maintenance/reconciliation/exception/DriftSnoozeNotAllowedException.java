package com.example.serverprovision.maintenance.reconciliation.exception;

import com.example.serverprovision.global.exception.ConflictException;

/**
 * MK4-1 — '지금은 두고 보기' 를 걸 수 없는 상태에서 요청이 들어왔을 때.
 *
 * <p>정상 흐름에서는 화면이 버튼을 비활성으로 1차 차단하므로 이 예외는 direct POST 나 오래된
 * 화면에서만 발동한다. 차단 사유의 단일 소스는 {@code Drift.snoozeBlockReason()} 이며 화면과
 * 서버 가드가 그 한 메서드를 함께 본다.</p>
 */
public class DriftSnoozeNotAllowedException extends ConflictException {

	private DriftSnoozeNotAllowedException(String message) {
		super(message);
	}

	public static DriftSnoozeNotAllowedException of(String reason) {
		return new DriftSnoozeNotAllowedException(reason);
	}
}
