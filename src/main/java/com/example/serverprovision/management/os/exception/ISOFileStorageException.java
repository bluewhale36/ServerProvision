package com.example.serverprovision.management.os.exception;

import com.example.serverprovision.global.exception.DomainException;
import com.example.serverprovision.global.orphan.enums.OrphanFailureClass;
import com.example.serverprovision.global.registration.FailureDisposition;
import com.example.serverprovision.global.registration.RegistrationFailure;

/**
 * ISO 파일 업로드/저장 중 I/O 수준의 실패가 발생했을 때 던진다.
 * {@code @ControllerAdvice} 가 500 으로 매핑한다.
 */
public class ISOFileStorageException extends DomainException implements RegistrationFailure {

	public ISOFileStorageException(String message, Throwable cause) {
		super(message, cause);
	}

	/** 인프라/일시 실패 — 파일 보존 + 격리 (저장 IO). */
	@Override
	public FailureDisposition disposition() {
		return new FailureDisposition.Quarantine(OrphanFailureClass.STORAGE_IO);
	}
}
