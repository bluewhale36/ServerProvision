package com.example.serverprovision.execution.pxeinfra.dto.response;

/**
 * 임대 목록 한 행의 뷰 계약 — 도메인 {@code LeaseEntry}(VO · Instant)를 뷰가 그대로 쓸 문자열로만 옮긴 것.
 * dnsmasq 는 시작 시각을 기록하지 않아 만료만 있다(R16).
 *
 * @param mac             하드웨어 주소 — 미기재면 대체 표기("—")
 * @param ends            만료 시각 표시 문자열(KST) — 무만료면 "무만료"
 * @param state           ACTIVE · EXPIRED (관측 시각 기준)
 * @param stateBadgeClass 배지 클래스(n-badge-*)
 */
public record DhcpLeaseView(
        String ip,
        String mac,
        String ends,
        String state,
        String stateBadgeClass
) {
}
