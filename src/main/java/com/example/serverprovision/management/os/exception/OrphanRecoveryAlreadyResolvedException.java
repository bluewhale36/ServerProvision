package com.example.serverprovision.management.os.exception;

import com.example.serverprovision.global.exception.ConflictException;

/**
 * 이미 재시도(RECOVERED) 또는 폐기(DISCARDED)된 격리 ISO 에 재차 복구 액션이 들어온 경우 (409).
 * 동시 클릭 / stale 화면에서 발생.
 */
public class OrphanRecoveryAlreadyResolvedException extends ConflictException {

	public OrphanRecoveryAlreadyResolvedException(String recoveryId, String state) {
		super("이미 처리된 격리 ISO 입니다. recoveryId=" + recoveryId + ", state=" + state);
	}
}
