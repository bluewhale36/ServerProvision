package com.example.serverprovision.provisioning.setting.dto.response;

import com.example.serverprovision.provisioning.setting.enums.SettingProcessType;

import java.util.List;

/**
 * 상세 화면의 "이 단계가 deprecated 자원을 사용 중" 표시 — 단계 카드 head 의 작은 뱃지 1개에 대응.
 * 조회 시점 판정(저장 안 함)이라 자원 상태 변화가 즉시 반영된다.
 */
public record DeprecatedUsageResponse(
        SettingProcessType processType,
        List<String> resourceNames
) {
}
