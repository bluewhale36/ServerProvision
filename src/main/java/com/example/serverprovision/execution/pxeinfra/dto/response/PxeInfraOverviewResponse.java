package com.example.serverprovision.execution.pxeinfra.dto.response;

import java.util.List;

/**
 * 임대 목록 페이지의 뷰 계약(R16). 미구성이면 {@code configured} 가 false 이고 목록은 비어 안내 문구만 노출된다.
 * {@code proxyMode} 면 임대가 존재하지 않으므로(IP 는 사내 DHCP 가 배정) 표 대신 그 안내를 렌더한다.
 *
 * @param configured     dnsmasq 관측 설정 존재 여부 — false 면 뷰가 임대 표 대신 미구성 안내를 렌더한다
 * @param dhcpModeLabel  저장된 구성의 모드 표기(자체 DHCP · proxyDHCP) — 저장 전이면 null
 * @param proxyMode      저장된 구성이 PROXY 인가 — true 면 임대 표 대신 안내
 * @param activeCount    현재 활성(미만료) 임대 건수
 * @param totalCount     파싱된 전체 임대 줄 수
 * @param leases         임대 행 — 활성 우선 · 만료 내림차순 정렬
 */
public record PxeInfraOverviewResponse(
        boolean configured,
        String dhcpModeLabel,
        boolean proxyMode,
        int activeCount,
        int totalCount,
        List<DhcpLeaseView> leases
) {
}
