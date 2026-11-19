package com.example.serverprovision.management.os.enums;

import com.example.serverprovision.management.os.vo.WindowsBuildNumber;

import java.util.Arrays;
import java.util.Optional;
import java.util.Set;

/**
 * 설치를 지원하는 Windows Server 버전과 그 빌드 번호(S23-1). 설치 소스의 install.wim 빌드가 이 번호와 같아야
 * 그 버전으로 설치할 수 있다. 새 버전이 나오면 상수와 {@link OSName} 상수를 함께 더한다.
 */
public enum WindowsServerRelease {

	W2025(WindowsBuildNumber.of(26100), Set.of(OSName.WINDOWS_SERVER_2025));

	private final WindowsBuildNumber build;
	private final Set<OSName> osNames;

	WindowsServerRelease(WindowsBuildNumber build, Set<OSName> osNames) {
		this.build = build;
		this.osNames = osNames;
	}

	public WindowsBuildNumber build() {
		return build;
	}

	/** install.wim 버전 문자열("10.0.26100.1742")의 빌드 번호가 이 버전의 빌드와 같은가. */
	public boolean matchesBuild(String fullVersion) {
		return WindowsBuildNumber.parse(fullVersion).map(build::equals).orElse(false);
	}

	/** OS 상수가 속한 버전. 지원하는 Windows Server 가 아니면 empty. */
	public static Optional<WindowsServerRelease> of(OSName osName) {
		return Arrays.stream(values()).filter(r -> r.osNames.contains(osName)).findFirst();
	}

	/** install.wim 버전 문자열에 맞는 지원 버전. 어느 버전과도 맞지 않으면 empty. */
	public static Optional<WindowsServerRelease> ofBuild(String fullVersion) {
		return Arrays.stream(values()).filter(r -> r.matchesBuild(fullVersion)).findFirst();
	}
}
