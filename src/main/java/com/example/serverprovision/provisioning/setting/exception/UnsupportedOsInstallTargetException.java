package com.example.serverprovision.provisioning.setting.exception;

import com.example.serverprovision.global.exception.FieldBoundBadRequestException;

/**
 * 식별 전용(설치 예정 기록)의 대상 OS 가 정책상 허용되지 않는 계열인 경우 (400, {@code osMetadataId} 직결).
 * 정상 흐름은 UI 가 옵션을 disabled 로 1차 차단하므로 이 예외는 direct POST 안전망이다 (R11 D-R8).
 */
public class UnsupportedPlannedInstallTargetException extends FieldBoundBadRequestException {

    public UnsupportedPlannedInstallTargetException(String reason) {
        super(reason, "osMetadataId");
    }
}
