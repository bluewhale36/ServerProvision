package com.example.serverprovision.execution.pxeinfra.dto.response;

/**
 * 임대 목록 한 행의 뷰 계약 — 도메인 {@code LeaseEntry}(VO·enum·Instant)를 뷰가 그대로 쓸 문자열로만 옮긴 것.
 * 뷰는 {@code IpAddressVO}·{@code LeaseBindingState} 같은 도메인 타입을 모른 채 표시 문자열과 배지 클래스만 받는다.
 *
 * @param mac            하드웨어 주소 — 미기재 블록이면 대체 표기("—")
 * @param ends           만료 시각 표시 문자열 — {@code never}(무만료)면 "무만료"
 * @param stateBadgeClass 바인딩 상태 배지 클래스(n-badge-*)
 */
public record DhcpLeaseView(
        String ip,
        String mac,
        String starts,
        String ends,
        String state,
        String stateBadgeClass
) {
}
