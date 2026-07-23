package com.example.serverprovision.execution.asset.dto.response;

import java.util.List;

/**
 * 진단 자산 대시보드 전체. 서빙 준비 상태와 6 슬롯 현황을 담는다.
 *
 * @param servingActive 자산 서빙 활성 여부 ({@code pxe.assets.root} 설정 = 빈 존재). false 면 슬롯은 조회 불가 상태로 표시
 * @param assetsRoot    자산 서빙 루트 절대경로 — 비활성 시 null
 * @param baseUrl       게스트 콜백 base URL — 비활성 시 null
 * @param slots         6 슬롯 현황
 * @param okCount       무결성 일치(ORIGINAL) 슬롯 수
 * @param totalCount    전체 슬롯 수
 */
public record SystemAssetDashboardResponse(
        boolean servingActive,
        String assetsRoot,
        String baseUrl,
        List<SystemAssetSlotResponse> slots,
        int okCount,
        int totalCount
) {
}
