package com.example.serverprovision.global.security.exception;

import org.springframework.http.HttpStatus;

/**
 * 요청이 정책상 거절될 때의 보안 예외 super-class. 403 으로 매핑.
 * <p>예: 허용된 root 디렉토리 밖으로의 경로 접근 ({@link PathOutsideAllowedRootsException}).</p>
 */
public abstract class ForbiddenException extends SecurityException {

    protected ForbiddenException(String message) {
        super(message);
    }

    @Override
    public HttpStatus httpStatus() {
        return HttpStatus.FORBIDDEN;
    }
}
