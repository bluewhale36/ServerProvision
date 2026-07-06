package com.example.serverprovision.provisioning.setting.dto.response;

import com.example.serverprovision.provisioning.setting.enums.SettingProcessType;

import java.util.List;

/**
 * 상세 화면의 "실행 시 건너뜀 가능" 경고(사용자 확정 2026-07-06) — 단계 카드 head 의 작은 뱃지.
 * 예: SPECIFIED 보드 + '최신 버전' 인데 그 보드에 등록된 펌웨어가 0개. 저장을 막지 않는
 * 안내이며(정의서=재사용 템플릿 — 실행 전 등록되면 자동 해소) 조회 시점 판정이다.
 * 실행 의미론: 해석 불가 축만 skip + PARTIAL_SUCCESS (Stage 4 — Q2 선례).
 */
public record ExecutionWarningResponse(
        SettingProcessType processType,
        List<String> warnings
) {
}
