package com.example.serverprovision.execution.vo;

import java.util.regex.Pattern;

/**
 * 게스트 서버 네트워크 카드(NIC)의 MAC 주소 VO.
 * DB 테이블에 직접 매핑되지 않는 어플리케이션 내 단순 VO 로, {@code HostNicBinding.macAddress} 필드의 타입으로 쓰인다.
 * <p>입력의 구분자(콜론 / 하이픈 / 무구분)와 대소문자 표기를 흡수해
 * 소문자 콜론 구분 형식(예: {@code aa:bb:cc:dd:ee:ff}, 17자)으로 정규화한다 —
 * 동일 NIC 가 표기 차이로 서로 다른 값처럼 비교되는 사고를 타입 차원에서 차단한다.</p>
 */
public record MacAddressVO(String value) {

	// 콜론 / 하이픈 / 무구분 세 표기를 허용. 내용은 항상 6 옥텟(16진수 2자리).
	private static final Pattern MAC_PATTERN = Pattern.compile(
			"^[0-9A-Fa-f]{2}(:[0-9A-Fa-f]{2}){5}$"
					+ "|^[0-9A-Fa-f]{2}(-[0-9A-Fa-f]{2}){5}$"
					+ "|^[0-9A-Fa-f]{12}$");

	private static final Pattern SEPARATORS = Pattern.compile("[:-]");

	// 어떤 생성 경로(of / 정규 생성자)로 들어와도 검증·정규화를 강제한다.
	public MacAddressVO {
		if (value == null || value.isBlank()) {
			throw new IllegalArgumentException("MAC 주소는 빈 값일 수 없습니다.");
		}
		String trimmed = value.trim();
		if (!MAC_PATTERN.matcher(trimmed).matches()) {
			throw new IllegalArgumentException("MAC 주소 형식이 올바르지 않습니다 : " + value);
		}
		value = normalize(trimmed);
	}

	public static MacAddressVO of(String raw) {
		return new MacAddressVO(raw);
	}

	// 구분자를 제거해 12 hex 로 환원한 뒤 소문자 콜론 형식으로 재조립한다.
	private static String normalize(String validated) {
		String hex = SEPARATORS.matcher(validated).replaceAll("").toLowerCase();
		StringBuilder sb = new StringBuilder(17);
		for (int i = 0; i < hex.length(); i += 2) {
			if (i > 0) sb.append(':');
			sb.append(hex, i, i + 2);
		}
		return sb.toString();
	}

}
