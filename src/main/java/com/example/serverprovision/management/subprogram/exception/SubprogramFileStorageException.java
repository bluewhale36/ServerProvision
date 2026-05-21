package com.example.serverprovision.management.subprogram.exception;

import com.example.serverprovision.global.exception.DomainException;

/**
 * Subprogram 번들 저장/해시 계산 중 I/O 실패.
 */
public class SubprogramFileStorageException extends DomainException {

	public SubprogramFileStorageException(String message, Throwable cause) {
		super(message, cause);
	}
}
