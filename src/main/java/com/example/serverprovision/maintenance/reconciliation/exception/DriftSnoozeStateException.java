package com.example.serverprovision.maintenance.reconciliation.exception;

import com.example.serverprovision.global.exception.ConflictException;

/**
 * 보관 상태 전이가 지금 상태에서 성립하지 않을 때 — 걸기와 풀기가 함께 쓴다.
 *
 * <p>MK4-1 이 '지금은 두고 보기' 를 만들며 걸기 전용({@code DriftSnoozeNotAllowedException})으로
 * 두었는데, MK4-4-3 이 수동 해제를 이으면서 같은 성질의 거절이 하나 더 생겼다. 상태코드도(409)
 * 응답 형태도 같아 실질 차이가 이름뿐이라, 새 예외를 만드는 대신 이름을 넓혀 둘이 공유한다 —
 * 새 예외는 규약상 통합 시나리오를 동반하는데, 같은 것을 두 이름으로 나누는 데 치를 비용이 아니다.</p>
 *
 * <p>정상 흐름에서는 화면이 버튼을 비활성으로 1 차 차단하므로 이 예외는 direct POST 나 오래된
 * 화면에서만 발동한다. 차단 사유의 단일 소스는 {@code Drift.snoozeBlockReason()} ·
 * {@code Drift.unsnoozeBlockReason()} 이며 화면과 서버 가드가 그 메서드를 함께 본다.</p>
 */
public class DriftSnoozeStateException extends ConflictException {

	private DriftSnoozeStateException(String message) {
		super(message);
	}

	public static DriftSnoozeStateException of(String reason) {
		return new DriftSnoozeStateException(reason);
	}
}
