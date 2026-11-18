package com.example.serverprovision.provisioning.setting.exception;

import com.example.serverprovision.global.exception.FieldBoundBadRequestException;

/**
 * 저장 · 수정 요청에 해시 비밀번호 키가 실린 경우 (400, E4-1-b-2 D-18) — 화면은 평문만 보내므로 직접 POST 에서만 생긴다.
 * 해시 값을 그대로 받으면 아무 문자열이나 해시인 척 저장할 수 있어 서버가 항상 해시한다는 계약이 깨진다.
 */
public class HashedPasswordInputException extends FieldBoundBadRequestException {

    public HashedPasswordInputException(String fieldName) {
        super("비밀번호는 해시가 아닌 원래 값으로 입력해야 합니다.", fieldName);
    }
}
