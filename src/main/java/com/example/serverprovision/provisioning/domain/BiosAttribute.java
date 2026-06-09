package com.example.serverprovision.provisioning.domain;

import com.example.serverprovision.provisioning.domain.enums.BiosAttributeType;
import com.example.serverprovision.provisioning.domain.vo.BiosAttributeName;
import com.example.serverprovision.provisioning.domain.vo.BiosEnumOption;
import com.example.serverprovision.provisioning.domain.vo.IntegerBounds;
import com.example.serverprovision.provisioning.domain.vo.PasswordLength;

import java.util.List;

/**
 * 레지스트리 1개 속성의 결합 메타데이터.
 * 타입별 nullable 필드(options / bounds / passwordLength)를 보유하지만, 위젯·검증·coerce 동작은
 * 어떤 소비자도 이 필드들로 직접 분기하지 않고 {@link BiosAttributeType} 에 위임한다 (다형성).
 *
 * @param options        Enumeration 일 때만 비어있지 않음
 * @param bounds         Integer 일 때만 non-null
 * @param passwordLength Password 일 때만 non-null
 * @param defaultValue   Enumeration=ValueName, Integer=숫자 문자열, Password=null
 */
public record BiosAttribute(
		BiosAttributeName name,
		BiosAttributeType type,
		String displayName,
		String helpText,
		String menuPath,
		boolean readOnly,
		boolean resetRequired,
		String defaultValue,
		List<BiosEnumOption> options,
		IntegerBounds bounds,
		PasswordLength passwordLength
) {

	/** AMI 가 들여쓰기 표현용으로 앞에 붙인 공백을 제거한 표시명. */
	public String trimmedDisplayName() {
		return displayName == null ? "" : displayName.trim();
	}

	public boolean hasDefault() {
		return defaultValue != null && !defaultValue.isBlank();
	}

	/** DisplayName 의 선행 공백 수로 추정한 들여쓰기 깊이(0~4). 실제 BIOS 의 하위 항목 들여쓰기 재현용. */
	public int indentDepth() {
		if (displayName == null) {
			return 0;
		}
		int spaces = 0;
		while (spaces < displayName.length() && displayName.charAt(spaces) == ' ') {
			spaces++;
		}
		return Math.min(spaces / 2, 4);
	}
}
