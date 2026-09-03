package com.example.serverprovision.provisioning.setting.exception;

import com.example.serverprovision.global.exception.FieldBoundBadRequestException;

/**
 * OS 설치 단계의 대상 OS 가 정책상 설치할 수 없는 상태인 경우 (400, {@code osMetadataId} 직결) — 리눅스 계열,
 * 또는 Windows 인데 설치 소스가 준비되지 않음. 문장은 {@code OsInstallTargetPolicy} 가 정본이며 옵션 tooltip 과 같다.
 * 정상 흐름은 UI 가 옵션을 disabled 로 1차 차단하므로 이 예외는 direct POST 안전망이다.
 */
public class UnsupportedOsInstallTargetException extends FieldBoundBadRequestException {

    public UnsupportedOsInstallTargetException(String reason) {
        super(reason, "osMetadataId");
    }
}
