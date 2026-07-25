package com.example.serverprovision.global.history.dto.response;

import java.time.Instant;

/**
 * 현재 버전 이력 보존 설정(E1-I-2-b-2) — 대시보드 표시용.
 *
 * @param retentionCount 자산별 보존 상한(최근 N개)
 * @param updatedAt      마지막 변경 시각(최초 시드 후 미변경이면 시드 시각)
 */
public record AssetHistorySettingsResponse(int retentionCount, Instant updatedAt) {
}
