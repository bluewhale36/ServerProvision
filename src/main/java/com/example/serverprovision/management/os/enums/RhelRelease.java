package com.example.serverprovision.management.os.enums;

import com.example.serverprovision.management.os.vo.OsMajorVersion;

import java.util.Arrays;
import java.util.Optional;
import java.util.Set;

/**
 * 설치를 지원하는 RHEL 계열 메이저(S23-1). 같은 메이저의 배포판(Rocky Linux 9, RHEL 9)은 한 상수에 속하고,
 * kickstart 옵션처럼 메이저마다 다른 능력은 이 상수 한 곳에서 읽는다. 새 메이저가 나오면 상수와 {@link OSName} 상수를 함께 더한다.
 */
public enum RhelRelease {

	R8(OsMajorVersion.of(8), Set.of(OSName.ROCKY_LINUX_8, OSName.RHEL_8)),
	R9(OsMajorVersion.of(9), Set.of(OSName.ROCKY_LINUX_9, OSName.RHEL_9)),
	R10(OsMajorVersion.of(10), Set.of(OSName.ROCKY_LINUX_10, OSName.RHEL_10));

	/** {@code rootpw --allow-ssh} 가 생긴 메이저. */
	private static final int ROOT_SSH_FLAG_SINCE = 10;

	private final OsMajorVersion major;
	private final Set<OSName> osNames;

	RhelRelease(OsMajorVersion major, Set<OSName> osNames) {
		this.major = major;
		this.osNames = osNames;
	}

	/** 메이저 번호. UE 실행기(kickstart {@code #version=RHEL<n>})와 맞춘 계약이라 숫자로 돌려준다. */
	public int major() {
		return major.value();
	}

	public OsMajorVersion majorVersion() {
		return major;
	}

	/** 이 메이저에 속한 배포판 상수. */
	public Set<OSName> osNames() {
		return osNames;
	}

	/** kickstart 에 {@code rootpw --allow-ssh} 를 쓸 수 있는가. 메이저 10 이상이다. */
	public boolean supportsRootSshFlag() {
		return major.value() >= ROOT_SSH_FLAG_SINCE;
	}

	/** 이 메이저의 버전인가. "9" 와 "9.6" 은 R9 에 맞고 "10.0" 은 맞지 않는다. */
	public boolean covers(String version) {
		return major.leads(version);
	}

	/** OS 상수가 속한 메이저. 지원하는 RHEL 계열이 아니면 empty. */
	public static Optional<RhelRelease> of(OSName osName) {
		return Arrays.stream(values()).filter(r -> r.osNames.contains(osName)).findFirst();
	}

	/** OS 상수가 속한 메이저 가운데 버전이 그 메이저에 맞을 때만 돌려준다. 맞지 않거나 지원 밖이면 empty. */
	public static Optional<RhelRelease> of(OSName osName, String version) {
		return of(osName).filter(r -> r.covers(version));
	}

	/** 버전 문자열의 첫 '.' 앞 숫자. "9.6" 은 9 다. 숫자가 아니면 empty. UE 계약이라 숫자로 돌려준다. */
	public static Optional<Integer> majorOf(String version) {
		return OsMajorVersion.parse(version).map(OsMajorVersion::value);
	}
}
