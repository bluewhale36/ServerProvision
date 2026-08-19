package com.example.serverprovision.provisioning.setting.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 디스크 묶음 규칙의 전송 방식 축 (U4-1-1). NVMe 는 종류(SSD/HDD)가 아니라 전송 방식이라 축이 갈린다
 * (U4-1 토론 E22 — 단일 NVMe SSD 가 OS 영역인 서버가 실재한다).
 * {@code AUTO} 의 뜻은 {@link DiskTypeRequirement#AUTO} 와 같다.
 */
@RequiredArgsConstructor
@Getter
public enum DiskTransportRequirement {
    SATA("SATA"),
    SAS("SAS"),
    NVME("NVMe"),
    AUTO("자동 탐지");

    private final String displayName;
}
