package com.example.serverprovision.provisioning.setting.exception;

import com.example.serverprovision.global.exception.FieldBoundBadRequestException;

/**
 * 등록 뒤 버전 확인에 실패한 ISO 를 설치 정의서에 지목한 경우 (400, {@code isoId} 직결, E4-1-b-2 D-20).
 * 정상 화면은 ISO 목록(Miller 1 열)이 그 ISO 항목의 radio 를 잠그므로 직접 POST · stale 화면에서만 생긴다. 문장은 ISO 도메인 메서드가 정본이다.
 */
public class IsoRegistrationFailedException extends FieldBoundBadRequestException {

    public IsoRegistrationFailedException(String reason) {
        super(reason, "isoId");
    }
}
