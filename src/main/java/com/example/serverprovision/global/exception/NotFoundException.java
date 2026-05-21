package com.example.serverprovision.global.exception;

/**
 * 지정한 ID/키에 해당하는 리소스를 찾지 못했을 때의 예외.
 * {@code @ControllerAdvice} 가 404 로 매핑한다.
 */
public abstract class NotFoundException extends DomainException {

	protected NotFoundException(String message) {
		super(message);
	}
}
