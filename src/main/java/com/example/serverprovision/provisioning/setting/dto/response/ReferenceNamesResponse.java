package com.example.serverprovision.provisioning.setting.dto.response;

import java.util.Map;

/**
 * 상세 화면의 참조 id → 표시명 사상(사용자 지시 2026-07-05: id 가 아닌 사람이 식별 가능한 이름).
 * 조회 시점 해석이라 자원명 변경이 즉시 반영되고, 사상에 없는 id(자원 삭제 등)는 템플릿이
 * {@code #id} 로 폴백한다.
 */
public record ReferenceNamesResponse(
        Map<Long, String> boards,
        Map<Long, String> biosVersions,
        Map<Long, String> bmcVersions,
        Map<Long, String> osNames,
        Map<Long, String> environments,
        Map<Long, String> packageGroups,
        Map<Long, String> templates
) {
    public static ReferenceNamesResponse empty() {
        return new ReferenceNamesResponse(Map.of(), Map.of(), Map.of(), Map.of(), Map.of(), Map.of(), Map.of());
    }
}
