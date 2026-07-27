package com.example.serverprovision.execution.asset.dto.response;

import java.util.List;

/**
 * 통합 시스템 자산 대시보드 전체. 등록된 모든 자산 영역 묶음과 영역을 가로지른 전체 집계를 담는다.
 * 영역이 늘어도(향후 확장점 구현 추가) 이 응답 형태는 그대로다 — 대시보드 서비스가 조건분기 없이
 * 영역을 순회해 채운다.
 *
 * @param areas      영역 묶음 목록(영역 라우팅 키 선언 순서)
 * @param totalOk    전 영역 healthy 슬롯 합계
 * @param totalCount 전 영역 슬롯 합계
 */
public record SystemAssetOverviewResponse(
        List<SystemAssetAreaGroupResponse> areas,
        int totalOk,
        int totalCount
) {
}
