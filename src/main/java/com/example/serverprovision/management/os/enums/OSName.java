package com.example.serverprovision.management.os.enums;

import lombok.Getter;
import com.example.serverprovision.management.os.vo.OsMajorVersion;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

/**
 * 자동화를 지원하는 OS 를 배포판과 메이저 단위로 고정한 카탈로그(S23-1). 등록 화면은 지원 상수만 보이고,
 * 메이저마다 다른 설치 능력은 {@link RhelRelease} 와 {@link WindowsServerRelease} 가 맡는다.
 *
 * <p>{@code displayName} 은 배포판 이름이다. ISO 의 .treeinfo 배포판 이름과 대조하고, OS 버전과 이어
 * "Rocky Linux 9.6" 같은 자원 이름이 된다. 선택지 라벨은 {@link #label()} 이다. 선언 순서가 목록과 선택지의 순서다
 * ({@code OSMetadata.CATALOG_ORDER}).</p>
 */
@Getter
public enum OSName {

	ROCKY_LINUX_8("Rocky Linux", 8, OSFamily.RHEL_BASED, OSVersionRule.MAJOR_PREFIX, OSCatalogStatus.SUPPORTED),
	ROCKY_LINUX_9("Rocky Linux", 9, OSFamily.RHEL_BASED, OSVersionRule.MAJOR_PREFIX, OSCatalogStatus.SUPPORTED),
	ROCKY_LINUX_10("Rocky Linux", 10, OSFamily.RHEL_BASED, OSVersionRule.MAJOR_PREFIX, OSCatalogStatus.SUPPORTED),
	RHEL_8("Red Hat Enterprise Linux", 8, OSFamily.RHEL_BASED, OSVersionRule.MAJOR_PREFIX, OSCatalogStatus.SUPPORTED),
	RHEL_9("Red Hat Enterprise Linux", 9, OSFamily.RHEL_BASED, OSVersionRule.MAJOR_PREFIX, OSCatalogStatus.SUPPORTED),
	RHEL_10("Red Hat Enterprise Linux", 10, OSFamily.RHEL_BASED, OSVersionRule.MAJOR_PREFIX, OSCatalogStatus.SUPPORTED),
	WINDOWS_SERVER_2025("Windows Server", 2025, OSFamily.WINDOWS_BASED, OSVersionRule.EXACT_MAJOR, OSCatalogStatus.SUPPORTED),
	UBUNTU("Ubuntu", null, OSFamily.DEBIAN_BASED, OSVersionRule.FREE, OSCatalogStatus.SUPPORTED),

	ROCKY_LINUX("Rocky Linux", null, OSFamily.RHEL_BASED, OSVersionRule.FREE, OSCatalogStatus.LEGACY),
	CENTOS("CentOS", null, OSFamily.RHEL_BASED, OSVersionRule.FREE, OSCatalogStatus.LEGACY),
	WINDOWS("Windows", null, OSFamily.WINDOWS_BASED, OSVersionRule.FREE, OSCatalogStatus.LEGACY),
	WINDOWS_SERVER("Windows Server", null, OSFamily.WINDOWS_BASED, OSVersionRule.FREE, OSCatalogStatus.LEGACY);

	/**
	 * -- GETTER --
	 * 배포판 이름. "Rocky Linux" 처럼 메이저를 붙이지 않는다.
	 */
	private final String displayName;
	/**
	 * -- GETTER --
	 * 메이저 버전. 메이저로 나누지 않는 상수와 미지원 상수는 null 이다.
	 */
	private final OsMajorVersion major;
	private final OSFamily family;
	private final OSVersionRule versionRule;
	private final OSCatalogStatus status;

	OSName(String displayName, Integer major, OSFamily family, OSVersionRule versionRule, OSCatalogStatus status) {
		this.displayName = displayName;
		this.major = major == null ? null : OsMajorVersion.of(major);   // 상수 선언을 짧게 두려고 숫자로 받아 VO 로 감싼다
		this.family = family;
		this.versionRule = versionRule;
		this.status = status;
	}

	/** 새로 등록하거나 고를 수 있는 지원 상수인가. */
	public boolean registrable() {
		return status == OSCatalogStatus.SUPPORTED;
	}

	/** 선택지 라벨. 메이저가 있으면 "Rocky Linux 9" 처럼 붙인다. */
	public String label() {
		return major == null ? displayName : displayName + " " + major;
	}

	/** ISO 에서 설치 환경과 패키지 그룹을 추출할 수 있는가. 지원하는 RHEL 계열만 해당한다. */
	public boolean extractable() {
		return family == OSFamily.RHEL_BASED && registrable();
	}

	/** 이 OS 의 버전으로 받아들이는 값인가. */
	public boolean acceptsVersion(String version) {
		return versionRule.accepts(major, version);
	}

	/** 지원 상수를 선언 순서대로. 등록 화면과 드라이버 화면의 선택지다. */
	public static List<OSName> registrableValues() {
		return Arrays.stream(values()).filter(OSName::registrable).toList();
	}

	/**
	 * 수정 화면의 선택지. 지원 상수에 더해, 지금 값이 미지원 상수이면 그 값을 맨 앞에 둔다. 선택지에 지금 값이 없으면
	 * 화면이 다른 항목을 고른 것처럼 보이고 저장할 때 값이 조용히 바뀌기 때문이다. 미지원 값으로는 저장되지 않는다.
	 */
	public static List<OSName> selectableKeeping(OSName current) {
		if (current == null || current.registrable()) {
			return registrableValues();
		}
		return Stream.concat(Stream.of(current), registrableValues().stream()).toList();
	}
}
