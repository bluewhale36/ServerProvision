package com.example.serverprovision.global.orphan.exception;

import com.example.serverprovision.global.exception.ConflictException;

/**
 * 이미 재시도(RECOVERED) 또는 폐기(DISCARDED)된 격리 자원에 재차 복구 액션이 들어온 경우 (409).
 * 동시 클릭 / stale 화면에서 발생.
 * <p>R1-4-4 — management/os/exception 에서 global/orphan/exception 으로 승격(도메인 무관).</p>
 */
public class OrphanRecoveryAlreadyResolvedException extends ConflictException {

	public OrphanRecoveryAlreadyResolvedException(String recoveryId, String state) {
		super("이미 처리된 격리 자원입니다. recoveryId=" + recoveryId + ", state=" + state);
	}
}
