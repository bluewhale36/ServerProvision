package com.example.serverprovision.provisioning.setting.exception;

import com.example.serverprovision.global.exception.ConflictException;

/**
 * 세팅 정의서 명칭 전역 중복 (409). advice 가 base {@link ConflictException} 으로 다형 매핑한다.
 */
public class DuplicateSettingDefinitionNameException extends ConflictException {

    public DuplicateSettingDefinitionNameException(String name) {
        super("이미 사용 중인 정의서 명칭입니다: " + name);
    }
}
