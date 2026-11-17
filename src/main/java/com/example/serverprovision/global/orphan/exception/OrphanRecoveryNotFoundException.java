package com.example.serverprovision.global.orphan.exception;

import com.example.serverprovision.global.exception.NotFoundException;

/**
 * 복구 대상 격리 자원(recoveryId)을 찾지 못함 — 미존재 또는 TTL reaper 가 이미 정리 (404).
 * <p>R1-4-4 — management/os/exception 에서 global/orphan/exception 으로 승격(도메인 무관).</p>
 */
public class OrphanRecoveryNotFoundException extends NotFoundException {

	public OrphanRecoveryNotFoundException(String recoveryId) {
		// 복구 키는 화면에 내보내지 않는다(S22). 호출부 시그니처는 그대로 둔다.
		super("복구할 격리 항목을 찾을 수 없습니다.");
	}
}
