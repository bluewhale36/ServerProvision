package com.example.serverprovision.provisioning.setting.exception;

import com.example.serverprovision.global.exception.NotFoundException;

/**
 * 지정 ID 의 세팅 정의서가 존재하지 않을 때 던진다. (advice 가 base {@link NotFoundException} 으로 404 매핑)
 */
public class SettingNotFoundException extends NotFoundException {

    public SettingNotFoundException(Long id) {
        super("세팅 정의서를 찾을 수 없습니다. id=" + id);
    }
}
