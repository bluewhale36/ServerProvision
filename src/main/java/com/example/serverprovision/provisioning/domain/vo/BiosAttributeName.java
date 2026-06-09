package com.example.serverprovision.provisioning.domain.vo;

/**
 * BIOS 속성 식별자 VO. XML {@code Control@AttributeName} 과 JSON {@code Attributes[].AttributeName} 의
 * 조인 키이며, Redfish {@code Attributes} 페이로드의 키로도 그대로 전달된다.
 * <p>byte-exact 키이므로 trim / 대소문자 정규화를 하지 않는다 — 표기를 바꾸면 조인이 깨진다.</p>
 */
public record BiosAttributeName(String value) {

	public BiosAttributeName {
		if (value == null || value.isBlank()) {
			throw new IllegalArgumentException("BIOS AttributeName 은 빈 값일 수 없습니다.");
		}
	}

	public static BiosAttributeName of(String value) {
		return new BiosAttributeName(value);
	}

	@Override
	public String toString() {
		return value;
	}
}
