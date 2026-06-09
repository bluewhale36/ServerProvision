package com.example.serverprovision.provisioning.domain.enums;

/**
 * 레지스트리 {@code Dependencies[].Dependency.MapToProperty} 타입화.
 * 의존 조건이 충족될 때 대상 속성에 적용되는 동적 가시성 속성.
 */
public enum RedfishMapToProperty {

	/** 대상 위젯을 화면에서 숨긴다. */
	HIDDEN,
	/** 대상 위젯을 비활성(회색) 처리한다. */
	GRAYOUT,
	/** 인식하지 못한 속성 — 무시 대상. */
	UNKNOWN;

	public static RedfishMapToProperty from(String raw) {
		if (raw == null) {
			return UNKNOWN;
		}
		return switch (raw.trim()) {
			case "Hidden" -> HIDDEN;
			case "GrayOut" -> GRAYOUT;
			default -> UNKNOWN;
		};
	}
}
