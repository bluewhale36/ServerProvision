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

	/** U2-2-1 — 템플릿에 담을 수 없는 타입(PASSWORD). UI 가 위젯을 미출력하므로 direct POST 안전망. */
	public static InvalidBiosValueException notTemplatable(BiosAttributeName name) {
		return new InvalidBiosValueException(name,
				"BIOS 세팅 템플릿에 담을 수 없는 속성 타입입니다 (비밀번호는 BMC 비밀번호 변경 흐름에서 별도 처리).");
	}

	/** U2-2-1 — 유효 변경분 0건(빈 diff). 템플릿은 최소 1개 변경 속성을 가져야 한다. */
	public static InvalidBiosValueException emptyDiff() {
		return new InvalidBiosValueException("attributes", "기본값에서 변경된 속성이 없습니다. 최소 1개 속성을 변경해야 합니다.");
	}
}
