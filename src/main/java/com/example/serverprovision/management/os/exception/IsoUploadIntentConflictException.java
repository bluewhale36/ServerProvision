package com.example.serverprovision.management.os.exception;

import com.example.serverprovision.global.exception.ConflictException;
import com.example.serverprovision.global.registration.FailureDisposition;
import com.example.serverprovision.global.registration.RegistrationFailure;

/**
 * Intent 핸드셰이크 단계에서 하드 거절 조건 (예: 동일 isoPath 에 활성 ISO 존재) 에 부합할 때 던진다.
 * 이 경우 실제 업로드 자체가 시작되지 않으므로 네트워크/디스크 낭비가 발생하지 않는다.
 */
public class IsoUploadIntentConflictException extends ConflictException implements RegistrationFailure {

	public IsoUploadIntentConflictException(String message) {
		super(message);
	}

	/** 콘텐츠/영구 실패 — 업로드 자체가 없거나 finalize 가 이미 정리. 격리하지 않는다. */
	@Override
	public FailureDisposition disposition() {
		return FailureDisposition.CLEANUP;
	}
}
