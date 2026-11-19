package com.example.serverprovision.management.os.vo;

import java.util.Optional;

/**
 * Windows 빌드 번호(S23-1). install.wim 의 버전 "10.0.26100.1742" 에서 세 번째 숫자 26100 이 빌드 번호이고,
 * Windows Server 버전마다 하나로 정해져 있다.
 */
public record WindowsBuildNumber(int value) {

	public WindowsBuildNumber {
		if (value <= 0) {
			throw new IllegalArgumentException("Windows 빌드 번호는 1 이상이어야 합니다: " + value);
		}
	}

	public static WindowsBuildNumber of(int value) {
		return new WindowsBuildNumber(value);
	}

	/** "10.0.26100.1742" 에서 빌드 번호를 꺼낸다. 형식이 다르면 empty. */
	public static Optional<WindowsBuildNumber> parse(String fullVersion) {
		if (fullVersion == null) {
			return Optional.empty();
		}
		String[] parts = fullVersion.strip().split("\\.");
		return parts.length < 3 ? Optional.empty() : PositiveNumberText.parse(parts[2]).map(WindowsBuildNumber::new);
	}

	@Override
	public String toString() {
		return String.valueOf(value);
	}
}
