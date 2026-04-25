package com.example.serverprovision.maintenance.reconciliation.dto.response;

import java.time.Instant;
import java.util.List;

/**
 * 스캔 1 회 결과 (보고서) 응답. {@code DriftReport} 엔티티 + N 개 {@code Drift} 자식을 담는다.
 *
 * @param id              DB PK
 * @param scannedAt       스캔 시작 시각
 * @param scanDuration    스캔 소요시간. ISO-8601 Duration 표기 ({@code "PT0.45S"} 등)
 * @param deep            deep scan 여부. true 면 manifestHash 재계산까지 수행한 결과
 * @param totalChecked    점검한 자원 총수 (드리프트 여부와 무관)
 * @param driftCount      드리프트 행 수. 보고서 목록(C1) 의 배지 표시용
 * @param failedScanRoots (권고6) walk 실패 root 목록 — 비어있지 않으면 부분 결과 경고
 * @param drifts          드리프트 상세 (C2/C3 표시용)
 */
public record DriftReportResponse(
        Long id,
        Instant scannedAt,
        String scanDuration,
        boolean deep,
        int totalChecked,
        int driftCount,
        List<String> failedScanRoots,
        List<DriftResponse> drifts
) {}
