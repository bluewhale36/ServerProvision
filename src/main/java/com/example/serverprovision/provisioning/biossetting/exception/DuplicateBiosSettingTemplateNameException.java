package com.example.serverprovision.provisioning.biossetting.exception;

import com.example.serverprovision.global.exception.ConflictException;

/**
 * 템플릿 명칭 전역 중복 (409). advice 가 base {@link ConflictException} 으로 다형 매핑한다.
 */
public class DuplicateBiosSettingTemplateNameException extends ConflictException {

    public DuplicateBiosSettingTemplateNameException(String name) {
        super("이미 사용 중인 템플릿 명칭입니다: " + name);
    }
}
