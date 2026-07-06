package com.example.serverprovision.provisioning.setting.exception;

import com.example.serverprovision.global.exception.FieldBoundBadRequestException;

/**
 * 설치 후 시스템 접근 가능성 규칙 위반 (400, field-bound) — legacy NO_ACCESSIBLE_USER 이관.
 * UI 힌트가 1차 안내하고, 여기는 direct POST 안전망이다.
 */
public class InvalidUserAccessException extends FieldBoundBadRequestException {

    private InvalidUserAccessException(String message, String fieldName) {
        super(message, fieldName);
    }

    /** RHEL — root 비밀번호도 일반 사용자도 없으면 설치 후 접근 불가. */
    public static InvalidUserAccessException rhelNoAccessibleUser() {
        return new InvalidUserAccessException(
                "루트 비밀번호 또는 일반 사용자 중 하나 이상을 입력해야 합니다. "
                        + "둘 다 없으면 설치 후 시스템에 접근할 수 없습니다.", "rootPassword");
    }

    /** Ubuntu — autoinstall identity 사용자는 필수(root 잠금 기본). */
    public static InvalidUserAccessException ubuntuUserRequired() {
        return new InvalidUserAccessException(
                "Ubuntu 설치는 일반 사용자를 1명 이상 등록해야 합니다 "
                        + "(root 는 잠금 상태로 설치되며 접속 후 sudo 로 엽니다).", "users");
    }
}
