package com.example.serverprovision.provisioning.dto.request;

import jakarta.validation.constraints.NotNull;

import java.util.Map;

/**
 * 저장 요청 — 사용자가 기본값에서 <b>변경한 (AttributeName → 값) 쌍만</b> 담는다.
 * 모든 위젯이 폼에서 문자열로 직렬화되므로 값은 String 이며, 타입 인식/검증은 서버가 레지스트리로 수행한다.
 */
public record BiosSettingsSaveRequest(@NotNull Map<String, String> attributes) {
}
