package com.example.serverprovision.management.raidcard.exception;

import com.example.serverprovision.global.exception.ConflictException;

/**
 * [관측값으로 확정] 을 판정이 허용하지 않는 상태에서 요청했을 때(E3.5-5-b D4). 화면은 같은 판정으로 버튼을 숨기거나
 * 잠그므로 정상 흐름에서는 나오지 않는다 — direct POST · stale 화면이 대상이다. 메시지는 tooltip 과 같은 문장이다.
 */
public class RaidCardObservationConfirmRejectedException extends ConflictException {

	public RaidCardObservationConfirmRejectedException(String blockReason) {
		super(blockReason);
	}
}
