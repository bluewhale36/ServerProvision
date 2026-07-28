package com.example.serverprovision.execution.asset.spi;

/**
 * 영역 헤더에 함께 노출할 컨텍스트 관측치 1개(슬롯 아님 — okCount 불참). 서비스 상태·활성 임대 건수 등.
 */
public record AssetContextItem(String label, String value, ObservationSeverity severity) {
}
