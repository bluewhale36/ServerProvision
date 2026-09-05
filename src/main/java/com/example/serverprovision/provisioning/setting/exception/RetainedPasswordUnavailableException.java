package com.example.serverprovision.provisioning.setting.exception;

import com.example.serverprovision.global.exception.FieldBoundBadRequestException;

/**
 * "기존 비밀번호 유지" 로 저장하려는데 저장본에 유지할 값이 없는 경우 (400, {@code administratorPassword} 직결) —
 * 구 저장본(값 없음) · 단계 신규 추가에서 유지 플래그를 보낸 direct POST. 정상 UX 는 값이 있을 때만 유지 체크를 보인다.
 */
public class RetainedPasswordUnavailableException extends FieldBoundBadRequestException {
    /** Windows Administrator(E4-1-a-2). */
    public RetainedPasswordUnavailableException() {
        this("administratorPassword", "Administrator");
    }

    /** 리눅스 root · 사용자 등 — 직결 필드와 대상 이름을 받아 같은 문장으로(HF12). */
    public RetainedPasswordUnavailableException(String fieldName, String subject) {
        super("유지할 기존 " + subject + " 비밀번호가 없습니다. 비밀번호를 입력하십시오.", fieldName);
    }
}
