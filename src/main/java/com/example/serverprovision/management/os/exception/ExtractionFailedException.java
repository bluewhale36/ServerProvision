package com.example.serverprovision.management.os.exception;

import com.example.serverprovision.global.exception.DomainException;

/**
 * comps.xml 탐색 / 파싱 / 저장 단계에서 복구 불가 오류가 발생했을 때 던진다.
 * 사용자에게는 태스크 상태가 {@code FAILED} 로 먼저 전달되며, 이 예외는 서버 로그와 {@code @ControllerAdvice} 경유의 500 변환에만 쓰인다.
 */
public class ExtractionFailedException extends DomainException {

    public ExtractionFailedException(String message) {
        super(message);
    }

    public ExtractionFailedException(String message, Throwable cause) {
        super(message, cause);
    }
}
