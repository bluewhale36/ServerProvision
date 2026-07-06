package com.example.serverprovision.provisioning.biossetting.exception;

import com.example.serverprovision.global.exception.ConflictException;

/**
 * 세팅 정의서에서 사용 중인 템플릿의 삭제 거절 (409). UI 는 삭제 버튼을 disabled(1차 차단)하므로
 * direct DELETE / 편집 레이스에서만 발동하는 안전망이며, DB 의 FK RESTRICT 가 최후 방어선이다.
 */
public class BiosSettingTemplateInUseException extends ConflictException {

    public BiosSettingTemplateInUseException(Long templateId) {
        super("세팅 정의서에서 사용 중인 템플릿은 삭제할 수 없습니다. id=" + templateId);
    }
}
