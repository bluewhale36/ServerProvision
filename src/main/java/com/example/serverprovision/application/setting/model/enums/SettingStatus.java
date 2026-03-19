package com.example.serverprovision.application.setting.model.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum SettingStatus {
    PENDING("대기 중"),
    IN_PROGRESS("진행 중"),
    COMPLETED("전체 완료"),
    PARTIAL_SUCCESS("부분 성공"), // 일부 서버는 성공, 일부는 실패한 상태
    FAILED("전체 실패"),
    CANCELED("취소됨");

    private final String description;
}