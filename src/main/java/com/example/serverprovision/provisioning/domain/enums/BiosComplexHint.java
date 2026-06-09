package com.example.serverprovision.provisioning.domain.enums;

/**
 * AMI SetupData 의 {@code Complex} UI 힌트 타입화.
 * leaf / submenu 양쪽 Control 에 모두 등장하는 단순 표시 힌트이며 구조 분류자가 아니다.
 */
public enum BiosComplexHint {

	NONE,
	HIGHLIGHT,
	INTERACTIVE;

	/**
	 * XML 의 {@code Complex="Complex:HighLight"} / {@code "Complex:Interactive"} 원본 문자열을 타입화한다.
	 * null / blank / 미인식 값은 {@link #NONE}.
	 */
	public static BiosComplexHint fromXml(String raw) {
		if (raw == null || raw.isBlank()) {
			return NONE;
		}
		String v = raw.trim();
		if (v.equalsIgnoreCase("Complex:HighLight")) {
			return HIGHLIGHT;
		}
		if (v.equalsIgnoreCase("Complex:Interactive")) {
			return INTERACTIVE;
		}
		return NONE;
	}
}
