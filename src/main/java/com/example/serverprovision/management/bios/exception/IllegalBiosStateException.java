package com.example.serverprovision.management.bios.exception;

import com.example.serverprovision.global.exception.ConflictException;

/**
 * BIOS 의 현재 상태와 요청이 맞지 않을 때 (예: 활성 상태에서 복구 시도, 삭제된 BIOS 에 수정 시도).
 * HTTP 409.
 */
public class IllegalBiosStateException extends ConflictException {

	public IllegalBiosStateException(String message) {
		super(message);
	}
}
