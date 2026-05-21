package com.example.serverprovision.management.bmc.exception;

import com.example.serverprovision.global.exception.DomainException;

/**
 * BMC 펌웨어 파일 저장/해시 계산 중 I/O 실패가 발생했을 때.
 */
public class BmcFileStorageException extends DomainException {

	public BmcFileStorageException(String message, Throwable cause) {
		super(message, cause);
	}
}
