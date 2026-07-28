package com.example.serverprovision.execution.pxeinfra.dto.response;

import java.util.List;

/**
 * dhcpd 임대 목록 페이지의 뷰 계약. 미구성이면 {@code configured} 가 false 이고 목록은 비어 안내 문구만 노출된다.
 *
 * @param configured  dhcpd 관측 설정 존재 여부 — false 면 뷰가 임대 표 대신 미구성 안내를 렌더한다
 * @param activeCount  현재 활성(state==ACTIVE, 미만료) 임대 건수
 * @param totalCount   파싱된 전체 임대 블록 수
 * @param leases       임대 행 — 활성 우선·만료 내림차순 정렬
 */
public record PxeInfraOverviewResponse(
        boolean configured,
        int activeCount,
        int totalCount,
        List<DhcpLeaseView> leases
) {
}
