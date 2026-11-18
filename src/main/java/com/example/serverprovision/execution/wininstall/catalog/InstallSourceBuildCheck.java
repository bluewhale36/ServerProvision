package com.example.serverprovision.execution.wininstall.catalog;

import com.example.serverprovision.management.os.enums.OSName;
import com.example.serverprovision.management.os.enums.WindowsServerRelease;

import java.util.Optional;

/**
 * 설치 소스의 install.wim 빌드가 정의서 OS 의 Windows Server 버전과 맞는지 판정한다(S23-1). 정의서 화면과
 * 저장 가드(계열 검사기)와 실행 게이트(준비도)가 같은 판정을 쓴다. 소스가 준비되지 않은 경우는 각자의 소스 사유가 먼저 말하므로
 * 여기서는 판정하지 않는다.
 */
public final class InstallSourceBuildCheck {

	/** 실행 게이트의 대기 스크립트에 싣는 짧은 코드. */
	public static final String WIRE = "install source build mismatch";

	private InstallSourceBuildCheck() {
	}

	/** 맞으면 empty, 맞지 않으면 화면에 보일 사유. OS 를 모르거나 소스가 준비되지 않았으면 empty. */
	public static Optional<String> blockReason(OSName osName, InstallSourceSnapshot snapshot) {
		if (osName == null || !snapshot.ready()) {
			return Optional.empty();
		}
		Optional<WindowsServerRelease> release = WindowsServerRelease.of(osName);
		if (release.isEmpty()) {
			return Optional.empty();   // 미지원 OS 는 정책이 먼저 막는다
		}
		String build = snapshot.build().orElse("");
		if (release.get().matchesBuild(build)) {
			return Optional.empty();
		}
		String shown = build.isBlank() ? "알 수 없음" : build;
		return Optional.of("설치 소스의 Windows 빌드(" + shown + ")가 " + osName.label() + "(" + release.get().build()
				+ ")와 다릅니다. 시스템 자산 대시보드의 'Windows 설치 소스' 영역을 확인해야 합니다.");
	}
}
