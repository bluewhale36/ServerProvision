package com.example.serverprovision.execution.engine.setting;

import java.util.Map;

/**
 * 이 게스트에 적용할 BIOS 속성 목표(E3-1 D-3) — 할당 스냅샷의 동결 템플릿 중 감지 보드와 일치하는 것만 병합한
 * 결과. 키는 AMI 속성명, 값은 JSON 원시값 — Redfish PATCH {@code Attributes} 와 구조 동형이라 무변환 소비한다.
 * provisioning 의 VO 를 들지 않는 이유는 execution → provisioning 참조 방향을 만들지 않기 위해서다(U3-1 원칙).
 */
public record BiosSettingTarget(Map<String, Object> attributes) {

    public BiosSettingTarget {
        attributes = Map.copyOf(attributes);
    }

    public boolean isEmpty() {
        return attributes.isEmpty();
    }
}
