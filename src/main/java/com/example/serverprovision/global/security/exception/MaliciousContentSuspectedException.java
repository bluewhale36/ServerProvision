package com.example.serverprovision.global.security.exception;

/**
 * 업로드된 파일의 실제 콘텐츠가 선언된 모드/형식과 일치하지 않을 때.
 * 예: ZIP 모드인데 PK header 가 아닌 경우.
 */
public class MaliciousContentSuspectedException extends UnsupportedMediaTypeException {

    public MaliciousContentSuspectedException(String reason) {
        super("업로드 파일의 콘텐츠가 안전하지 않습니다 : " + reason);
    }
}
