package com.example.serverprovision.management.bios.exception;

import com.example.serverprovision.global.exception.DomainException;

/**
 * BIOS 파일을 로컬 파일시스템에 저장하거나 체크섬을 계산하는 도중 I/O 실패가 발생했을 때.
 * HTTP 500 — 사용자 입력이 아닌 서버 측 문제.
 */
public class BiosFileStorageException extends DomainException {

	public BiosFileStorageException(String message, Throwable cause) {
		super(message, cause);
	}
}
