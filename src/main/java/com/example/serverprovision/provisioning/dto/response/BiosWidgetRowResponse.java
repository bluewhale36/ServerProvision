package com.example.serverprovision.provisioning.dto.response;

import com.example.serverprovision.provisioning.domain.BiosAttribute;
import com.example.serverprovision.provisioning.domain.BiosAttributeControl;
import com.example.serverprovision.provisioning.domain.BiosConditionalInjection;
import com.example.serverprovision.provisioning.domain.enums.BiosAttributeType;
import com.example.serverprovision.provisioning.domain.vo.BiosEnumOption;
import com.example.serverprovision.provisioning.domain.vo.IntegerBounds;
import com.example.serverprovision.provisioning.domain.vo.PasswordLength;

import java.util.List;

/**
 * 속성 위젯 행. 클라이언트 diff 계약(data-default/data-type)과 위젯 렌더에 필요한 옵션/범위를 모두 포함한다.
 *
 * @param conditional null 이 아니면 "조건부 활성" orphan 위젯 — controller 속성 값에 따라 클라이언트가 활성/강제한다.
 */
public record BiosWidgetRowResponse(
		String attributeName,
		String displayName,
		String helpText,
		String widgetKind,
		String defaultValue,
		boolean resetRequired,
		boolean readOnly,
		List<BiosEnumOption> options,
		Long lower,
		Long upper,
		Long step,
		Integer minLength,
		Integer maxLength,
		int indentDepth,
		String complex,
		ConditionalEnable conditional
) implements BiosRowResponse {

	/** 조건부 활성 계약: {@code controllerAttribute} 가 {@code enableValue} 일 때만 활성, 아니면 {@code forcedValue} 강제. */
	public record ConditionalEnable(String controllerAttribute, String enableValue, String forcedValue) {
	}

	@Override
	public String kind() {
		return "widget";
	}

	/**
	 * @param currentValue 보드 실제 현재값(initial_settings). null/blank 이면 레지스트리 기본값으로 표시.
	 *                     화면 표시값이자 "변경분만 전송" 의 diff 기준값(data-default).
	 */
	public static BiosWidgetRowResponse of(BiosAttributeControl control, BiosAttribute attr, String currentValue) {
		return build(attr, currentValue, control.complex().name(), null);
	}

	/**
	 * XML 에 없는 조건부 orphan 위젯 생성 (예외 주입). controller 값에 종속해 활성/강제된다.
	 */
	public static BiosWidgetRowResponse conditionalOf(BiosAttribute attr, String currentValue, BiosConditionalInjection injection) {
		ConditionalEnable conditional = new ConditionalEnable(
				injection.afterAttribute().value(), injection.enableWhenValue(), injection.forcedValue());
		return build(attr, currentValue, "NONE", conditional);
	}

	private static BiosWidgetRowResponse build(BiosAttribute attr, String currentValue, String complex, ConditionalEnable conditional) {
		IntegerBounds b = attr.bounds();
		PasswordLength pw = attr.passwordLength();
		return new BiosWidgetRowResponse(
				attr.name().value(),
				attr.trimmedDisplayName(),
				attr.helpText(),
				attr.type().widgetKind(),
				resolveBaseline(attr, currentValue),
				attr.resetRequired(),
				attr.readOnly(),
				attr.options(),
				b == null ? null : b.lower(),
				b == null ? null : b.upper(),
				b == null ? null : b.htmlStep(),
				pw == null ? null : pw.min(),
				pw == null ? null : pw.max(),
				attr.indentDepth(),
				complex,
				conditional
		);
	}

	// 화면에 표시할 baseline 값 결정: 현재값 우선, 없거나 부적합하면 레지스트리 기본값.
	// Enumeration 은 현재값을 옵션 ValueName 과 대소문자 무시 매칭해 canonical ValueName 으로 정규화
	// (예: 현재값 "AUTO" → 옵션 "Auto"). 그래야 select 가 올바른 옵션을 선택하고 diff 가 정확하다.
	private static String resolveBaseline(BiosAttribute attr, String currentValue) {
		if (attr.type() == BiosAttributeType.PASSWORD) {
			return null; // 비밀번호는 표시/기본값 없음 (항상 빈 입력).
		}
		if (currentValue == null || currentValue.isBlank()) {
			return attr.defaultValue();
		}
		if (attr.type() == BiosAttributeType.ENUMERATION) {
			for (BiosEnumOption option : attr.options()) {
				if (option.valueName().equalsIgnoreCase(currentValue)) {
					return option.valueName();
				}
			}
			return attr.defaultValue(); // 옵션에 없는 현재값 → 기본값으로 안전 fallback.
		}
		if (attr.type() == BiosAttributeType.INTEGER) {
			try {
				return String.valueOf(Long.parseLong(currentValue.trim()));
			} catch (NumberFormatException e) {
				return attr.defaultValue();
			}
		}
		if (attr.type() == BiosAttributeType.BOOLEAN) {
			String v = currentValue.trim();
			if (v.equalsIgnoreCase("true")) {
				return "true";
			}
			if (v.equalsIgnoreCase("false")) {
				return "false";
			}
			return attr.defaultValue(); // "true"/"false" 외 현재값 → 기본값.
		}
		return attr.defaultValue();
	}
}
