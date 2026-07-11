package com.example.serverprovision.provisioning.setting.dto.response;

import java.util.List;

/**
 * 타임존 선택지의 대륙(IANA region) 그룹 — 폼의 대륙/도시 2-select 에 대응(사용자 확정 2026-07-12).
 * 원천은 JVM 내장 IANA tzdb({@code ZoneId.getAvailableZoneIds()}) — ISO 추출·하드코딩 불필요
 * (tzdata 는 ISO 마다 사실상 동일한 IANA 목록이라 ISO 스코프 가치가 없음).
 */
public record TimezoneRegionResponse(
        String region,
        List<String> cities
) {
}
