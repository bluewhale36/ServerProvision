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
 * <p>U2-2-2 — baseline({@code defaultValue}) 은 registry {@code DefaultValue} 단일 SSOT 다
 * (임시물이던 initial_settings 현재값 우선 로직 은퇴). {@code storedValue} 는 수정(edit) pre-fill 전용:
 * 위젯의 <b>선택값만</b> 저장값으로 세팅하고 diff 기준선(data-default)은 생성 때와 동일하게 둔다 —
 * 사용자가 기본값으로 되돌리면 PUT diff 에서 자연 탈락한다.</p>
 *
 * @param conditional null 이 아니면 "조건부 활성" orphan 위젯 — controller 속성 값에 따라 클라이언트가 활성/강제한다.
 * @param storedValue edit pre-fill 용 저장값(폼 문자열). 생성 화면·미저장 속성은 null.
 */
public record BiosWidgetRowResponse(
		String attributeName,
		String displayName,
		String helpText,
		String widgetKind,
		String defaultValue,
		String storedValue,
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

	public static BiosWidgetRowResponse of(BiosAttributeControl control, BiosAttribute attr, String storedValue) {
		return build(attr, storedValue, control.complex().name(), null);
	}

	/**
	 * XML 에 없는 조건부 orphan 위젯 생성 (예외 주입). controller 값에 종속해 활성/강제된다.
	 */
	public static BiosWidgetRowResponse conditionalOf(BiosAttribute attr, String storedValue, BiosConditionalInjection injection) {
		ConditionalEnable conditional = new ConditionalEnable(
				injection.afterAttribute().value(), injection.enableWhenValue(), injection.forcedValue());
		return build(attr, storedValue, "NONE", conditional);
	}

	private static BiosWidgetRowResponse build(BiosAttribute attr, String storedValue, String complex, ConditionalEnable conditional) {
		IntegerBounds b = attr.bounds();
		PasswordLength pw = attr.passwordLength();
		return new BiosWidgetRowResponse(
				attr.name().value(),
				attr.trimmedDisplayName(),
				attr.helpText(),
				attr.type().widgetKind(),
				baseline(attr),
				storedValue,
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

	// baseline = registry DefaultValue (기본 세팅 값의 SSOT). 비밀번호는 표시/기본값 없음(항상 빈 입력).
	private static String baseline(BiosAttribute attr) {
		return attr.type() == BiosAttributeType.PASSWORD ? null : attr.defaultValue();
	}
}
