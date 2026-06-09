package com.example.serverprovision.provisioning.domain;

import com.example.serverprovision.provisioning.domain.enums.RedfishMapToProperty;
import com.example.serverprovision.provisioning.domain.vo.BiosAttributeName;

/**
 * 동적 가시성 규칙 1건: {@code fromAttribute} 의 현재 값이 {@code fromValueRaw} 와 같으면(EQU)
 * {@code toAttribute} 에 {@code toProperty}(Hidden/GrayOut) 를 적용.
 * <p>PoC 에서는 메타로만 보유해 클라이언트가 라이브 평가한다(서버 강제는 후속).
 * {@code fromValueRaw} 는 bool/int/string 혼합 타입을 문자열로 보존한다.</p>
 */
public record BiosDependency(
		BiosAttributeName fromAttribute,
		String fromValueRaw,
		BiosAttributeName toAttribute,
		RedfishMapToProperty toProperty
) {
}
