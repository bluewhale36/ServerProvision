package com.example.serverprovision.management.raidcard.exception;

import com.example.serverprovision.global.exception.ConflictException;

/**
 * 요청 시점의 엔티티 상태가 해당 조작을 허용하지 않을 때 던진다.
 * 예: 활성 상태 레코드에 복구를 시도, 이미 삭제된 레코드에 lifecycle 전이를 직접 POST.
 */
public class IllegalRaidCardStateException extends ConflictException {

	public IllegalRaidCardStateException(String message) {
		super(message);
	}
}
