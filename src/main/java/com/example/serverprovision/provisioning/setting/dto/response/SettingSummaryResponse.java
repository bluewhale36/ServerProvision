package com.example.serverprovision.provisioning.setting.dto.response;

import com.example.serverprovision.provisioning.setting.enums.SettingProcessType;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 세팅 정의서 목록 행 응답. ({@code GET /provisioning/setting})
 */
public record SettingSummaryResponse(
        Long id,
        String name,
        /** 정의서에 포함된 단계 타입 목록 — 목록 화면의 단계 요약 배지용. */
        List<SettingProcessType> processTypes,
        LocalDateTime createdAt
) {
}
