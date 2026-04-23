package com.example.serverprovision.maintenance.os.exception;

import com.example.serverprovision.global.exception.ConflictException;

/**
 * 업로드 요청의 {@code X-Upload-Token} 이 없거나 만료되었거나 intent 단계에서 약속한 값과 다를 때 던진다.
 * (인증 실패와 구별되는 의미이기 때문에 409 Conflict 로 매핑)
 */
public class InvalidUploadTokenException extends ConflictException {
    public InvalidUploadTokenException(String message) {
        super(message);
    }
}
