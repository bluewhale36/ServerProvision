package com.example.serverprovision.global.exception;

/**
 * 비즈니스/도메인 규칙 위반 시 던지는 런타임 예외의 최상위 타입.
 * 구체 예외는 각 feature 패키지의 {@code exception/} 하위에 둔다.
 */
public abstract class DomainException extends RuntimeException {

    protected DomainException(String message) {
        super(message);
    }

    protected DomainException(String message, Throwable cause) {
        super(message, cause);
    }
}
