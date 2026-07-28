package com.example.serverprovision.execution.asset.dto.response;

/**
 * 관측 chip 의 뷰 계약 — 뷰는 도메인 {@code ObservationSeverity} 를 모른 채 badgeClass 문자열만 받는다.
 */
public record AssetContextItemResponse(String label, String value, String badgeClass) {
}
