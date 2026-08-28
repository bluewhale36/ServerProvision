package com.example.serverprovision.global.redfish;

/**
 * BMC 가 내준 메시지 레지스트리 전문(E3-3) — {@code registryId} 는 {@code Bios.AttributeRegistry} 가 가리킨 이름,
 * {@code rawJson} 은 그 {@code Location[].Uri} 의 응답 원문이다. 해석(속성 · 허용값)은 상위 파서가 한다.
 */
public record RedfishRegistry(String registryId, String uri, String rawJson) {
}
