package com.example.serverprovision.provisioning.biossetting.exception;

import com.example.serverprovision.global.exception.NotFoundException;

/**
 * 지정 ID 의 BIOS 세팅 템플릿이 존재하지 않을 때 던진다. (advice 가 base {@link NotFoundException} 으로 404 매핑)
 */
public class BiosSettingTemplateNotFoundException extends NotFoundException {

    public BiosSettingTemplateNotFoundException(Long id) {
        super("BIOS 세팅 템플릿을 찾을 수 없습니다. id=" + id);
    }
}
