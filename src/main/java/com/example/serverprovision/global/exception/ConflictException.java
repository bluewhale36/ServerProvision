package com.example.serverprovision.global.exception;

/**
 * 현재 리소스 상태와 요청이 충돌할 때의 예외 (중복 등록, 잘못된 상태 전이 등).
 * {@code @ControllerAdvice} 가 409 로 매핑한다.
 */
public abstract class ConflictException extends DomainException {

    protected ConflictException(String message) {
        super(message);
    }
}
