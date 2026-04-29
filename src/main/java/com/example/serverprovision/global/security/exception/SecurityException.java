package com.example.serverprovision.global.security.exception;

import org.springframework.http.HttpStatus;

/**
 * 보안 정책 위반의 최상위 super-class.
 *
 * <p>{@code DomainException} 하위가 아닌 별도 계층으로 분리된 이유 :
 * 컨트롤러의 {@code catch (DomainException)} 가 보안 예외를 흡수해 500 으로 새는 사고
 * (S3.3 silent-500 회귀) 를 차단하기 위함이다. 보안 예외는 framework 의 {@code @ExceptionHandler}
 * 매핑을 거쳐 적절한 HTTP status (400/403/413/415/500) 로 응답되어야 하므로 컨트롤러 try/catch 의
 * 일반 도메인 예외 처리 경로에 흡수되어서는 안 된다.</p>
 *
 * <p>HTTP status 매핑은 sub-class 가 {@link #httpStatus()} 를 구현해 다형적으로 결정한다.
 * 핸들러는 if-else / switch 분기 없이 단일 핸들러로 통합 가능하다.</p>
 *
 * <p>abstract 로 선언해 직접 throw 를 차단한다 — 의미가 모호한 raw {@code SecurityException} 던지기를 방지.</p>
 */
public abstract class SecurityException extends RuntimeException {

    protected SecurityException(String message) {
        super(message);
    }

    protected SecurityException(String message, Throwable cause) {
        super(message, cause);
    }

    /**
     * 본 예외가 응답으로 가져야 할 HTTP status.
     * 핸들러가 분기 없이 다형적으로 매핑하기 위한 메타데이터.
     */
    public abstract HttpStatus httpStatus();
}
