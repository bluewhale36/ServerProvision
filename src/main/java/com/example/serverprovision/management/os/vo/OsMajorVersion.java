package com.example.serverprovision.management.os.vo;

import java.util.Optional;

/**
 * OS 메이저 버전(S23-1). Rocky Linux 9 의 9, Windows Server 2025 의 2025 처럼 OS 카탈로그 상수가 고정한 번호다.
 * 버전 문자열에서 메이저를 읽는 규칙("9.6" 의 9)도 이 타입 한 곳에 둔다.
 */
public record OsMajorVersion(int value) {

	public OsMajorVersion {
		if (value <= 0) {
			throw new IllegalArgumentException("OS 메이저 버전은 1 이상이어야 합니다: " + value);
		}
	}

	public static OsMajorVersion of(int value) {
		return new OsMajorVersion(value);
	}

	/** 버전 문자열의 첫 '.' 앞 숫자. "9.6" 은 9 다. 숫자가 아니거나 0 이면 empty. */
	public static Optional<OsMajorVersion> parse(String version) {
		if (version == null) {
			return Optional.empty();
		}
		String head = version.strip();
		int dot = head.indexOf('.');
		if (dot >= 0) {
			head = head.substring(0, dot);
		}
		return PositiveNumberText.parse(head).map(OsMajorVersion::new);
	}

	/** 버전 문자열의 메이저가 이 메이저인가. "9" 와 "9.6" 은 9 에 맞고 "10.0" 은 맞지 않는다. */
	public boolean leads(String version) {
		return parse(version).map(this::equals).orElse(false);
	}

	/** 버전 문자열이 이 메이저 번호 그대로인가. "2025" 만 2025 에 맞는다. */
	public boolean isExactly(String version) {
		return version != null && toString().equals(version.strip());
	}

	@Override
	public String toString() {
		return String.valueOf(value);
	}
}
