package com.example.serverprovision.maintenance.os.exception;

import com.example.serverprovision.global.exception.ConflictException;
import com.example.serverprovision.maintenance.os.enums.OSName;

/**
 * 해당 OS 계열에 대응하는 {@code CompsExtractorStrategy} 구현이 없을 때 던진다.
 * 예: Ubuntu 등 comps.xml 을 사용하지 않는 배포판에 대해 추출 요청이 들어온 경우.
 */
public class UnsupportedExtractionException extends ConflictException {

    public UnsupportedExtractionException(OSName osName) {
        super("해당 OS 는 환경·패키지 그룹 자동 추출을 지원하지 않습니다: " + osName.getDisplayName());
    }
}
