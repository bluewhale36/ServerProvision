package com.example.serverprovision.domain.os.service.extractor;

import com.example.serverprovision.domain.os.model.enums.OSName;

// OS 타입별 환경·패키지 그룹 추출 전략 인터페이스
// 새로운 OS 계열 지원 시 이 인터페이스를 구현하는 @Component 를 추가한다
public interface CompsExtractorStrategy {

    boolean supports(OSName osName);

    // isoMountPath: os_metadata.iso_mount_path (HTTP URL 또는 로컬 경로)
    CompsExtractionResult extract(String isoMountPath);
}
