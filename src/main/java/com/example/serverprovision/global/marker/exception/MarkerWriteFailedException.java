package com.example.serverprovision.global.marker.exception;

import com.example.serverprovision.global.exception.DomainException;

/**
 * marker 파일 IO 실패 (디스크 가득참, 권한 없음 등). → 500
 */
public class MarkerWriteFailedException extends DomainException {

    public MarkerWriteFailedException(String message, Throwable cause) {
        super(message);
        initCause(cause);
    }
}
