package com.example.serverprovision.provisioning.domain.vo;

/**
 * Enumeration 속성의 선택지 1건.
 * {@code valueName} 은 Redfish 로 전송되는 wire 값, {@code valueDisplayName} 은 화면 표시 라벨이다 (대개 동일).
 */
public record BiosEnumOption(String valueName, String valueDisplayName) {
}
