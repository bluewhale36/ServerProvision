package com.example.serverprovision.management.os.exception;

import com.example.serverprovision.global.exception.DomainException;

/**
 * ISO 파일 업로드/저장 중 I/O 수준의 실패가 발생했을 때 던진다.
 * {@code @ControllerAdvice} 가 500 으로 매핑한다.
 */
public class ISOFileStorageException extends DomainException {

    public ISOFileStorageException(String message, Throwable cause) {
        super(message, cause);
    }
}
