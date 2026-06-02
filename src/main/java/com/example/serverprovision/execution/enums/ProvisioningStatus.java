package com.example.serverprovision.execution.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
@Getter
public enum ProvisioningStatus {

    PENDING("작업 대기"),
    RUNNING("작업 중"),
    SUCCEEDED("작업 완료"),
    FAILED("작업 실패"),
    SKIPPED("작업 건너뜀");

    private final String description;
}
