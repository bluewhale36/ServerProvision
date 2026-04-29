package com.example.serverprovision.global.security.exception;

import org.springframework.http.HttpStatus;

/**
 * 업로드 size / 갯수 / 트리 byte 한도 초과. 413 으로 매핑.
 */
public class UploadLimitExceededException extends SecurityException {

    public UploadLimitExceededException(String reason) {
        super("업로드 한도 초과 : " + reason);
    }

    @Override
    public HttpStatus httpStatus() {
        return HttpStatus.PAYLOAD_TOO_LARGE;
    }
}
