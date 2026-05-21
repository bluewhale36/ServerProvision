package com.example.serverprovision.global.security.exception;

import org.springframework.http.HttpStatus;

/**
 * 업로드 콘텐츠의 형식이 정책상 거절될 때의 보안 예외 super-class. 415 로 매핑.
 * <p>예: ZIP 모드인데 PK header 가 아님 / 실행 가능 binary / 위험 파일명.</p>
 */
public abstract class UnsupportedMediaTypeException extends SecurityException {

	protected UnsupportedMediaTypeException(String message) {
		super(message);
	}

	@Override
	public HttpStatus httpStatus() {
		return HttpStatus.UNSUPPORTED_MEDIA_TYPE;
	}
}
