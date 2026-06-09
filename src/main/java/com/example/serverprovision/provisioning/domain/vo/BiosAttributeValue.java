package com.example.serverprovision.provisioning.domain.vo;

/**
 * 검증을 통과해 타입화(coerce)된 BIOS 속성 값.
 * Redfish {@code Attributes} 페이로드에 들어갈 Java 객체({@link String} / {@link Long} / {@link Boolean})를 캡슐화해,
 * Jackson 이 Enumeration/Password 는 따옴표 문자열, Integer 는 따옴표 없는 숫자, Boolean 은 따옴표 없는 true/false 로
 * 직렬화하도록 보장한다 (BMC 의 strict 타입 검사 = Redfish {@code PropertyValueTypeError} 방어).
 */
public record BiosAttributeValue(Object jsonValue) {

	public BiosAttributeValue {
		if (!(jsonValue instanceof String || jsonValue instanceof Long || jsonValue instanceof Boolean)) {
			throw new IllegalArgumentException(
					"BiosAttributeValue 는 String / Long / Boolean 만 허용합니다: " + jsonValue);
		}
	}

	public static BiosAttributeValue ofString(String value) {
		return new BiosAttributeValue(value);
	}

	public static BiosAttributeValue ofLong(long value) {
		return new BiosAttributeValue(value);
	}

	public static BiosAttributeValue ofBoolean(boolean value) {
		return new BiosAttributeValue(value);
	}
}
