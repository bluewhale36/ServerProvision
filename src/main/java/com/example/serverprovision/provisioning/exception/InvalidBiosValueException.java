package com.example.serverprovision.provisioning.exception;

import com.example.serverprovision.global.exception.FieldBoundBadRequestException;
import com.example.serverprovision.provisioning.domain.vo.BiosAttributeName;

/**
 * 저장 값이 타입 검증(enum 비매칭 / 정수 범위·증분 / 패스워드 길이)에 실패했을 때 (400).
 * {@code fieldName} 에 AttributeName 을 실어 클라이언트 폼이 해당 위젯에 에러를 매핑할 수 있게 한다.
 */
public class InvalidBiosValueException extends FieldBoundBadRequestException {

	public InvalidBiosValueException(BiosAttributeName name, String reason) {
		super(reason, name.value());
	}

	private InvalidBiosValueException(String fieldName, String reason) {
		super(reason, fieldName);
	}

	/** 빈/공백 AttributeName 키(malformed 요청) — VO 생성 전에 던져 IllegalArgumentException → 500 누수를 막는다. */
	public static InvalidBiosValueException blankKey() {
		return new InvalidBiosValueException("attributes", "빈 속성 키(AttributeName)는 허용되지 않습니다.");
	}
}
