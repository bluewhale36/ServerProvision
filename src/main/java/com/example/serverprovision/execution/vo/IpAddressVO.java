package com.example.serverprovision.execution.vo;

import java.util.regex.Pattern;

/**
 * 게스트 서버가 DHCP 로부터 할당받은 IPv4 주소 VO.
 * DB 테이블에 직접 매핑되지 않는 어플리케이션 내 단순 VO 로, {@code HostNicBinding.ipAddress} 필드의 타입으로 쓰인다.
 * <p>각 옥텟 0~255 범위를 생성자 가드로 검증해 "유효한 IPv4" 라는 도메인 의미를 타입 자체로 담보한다.
 * 프로비저닝 대상은 PXE/DHCP 기반 IPv4 환경이므로 IPv6 는 범위 밖이다 — 진입 시 거절한다.</p>
 */
public record IpAddressVO(String value) {

	// 각 옥텟이 0~255 (선행 0 패딩 없는 십진) 인 점-구분 IPv4.
	private static final Pattern IPV4_PATTERN = Pattern.compile(
			"^((25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)\\.){3}(25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)$");

	// 어떤 생성 경로(of / 정규 생성자)로 들어와도 검증을 강제한다.
	public IpAddressVO {
		if (value == null || value.isBlank()) {
			throw new IllegalArgumentException("IP 주소는 빈 값일 수 없습니다.");
		}
		String trimmed = value.trim();
		if (!IPV4_PATTERN.matcher(trimmed).matches()) {
			throw new IllegalArgumentException("IPv4 주소 형식이 올바르지 않습니다 : " + value);
		}
		value = trimmed;
	}

	public static IpAddressVO of(String raw) {
		return new IpAddressVO(raw);
	}

}
