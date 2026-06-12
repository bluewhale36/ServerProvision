package com.example.serverprovision.management.os.exception;

import com.example.serverprovision.global.exception.NotFoundException;

/**
 * 복구 대상 격리 ISO(recoveryId)를 찾지 못함 — 미존재 또는 TTL reaper 가 이미 정리 (404).
 */
public class OrphanRecoveryNotFoundException extends NotFoundException {

	public OrphanRecoveryNotFoundException(String recoveryId) {
		super("복구 대상 격리 ISO 를 찾을 수 없습니다. recoveryId=" + recoveryId);
	}
}
