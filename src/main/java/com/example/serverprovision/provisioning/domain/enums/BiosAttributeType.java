package com.example.serverprovision.provisioning.domain.enums;

import com.example.serverprovision.provisioning.domain.BiosAttribute;
import com.example.serverprovision.provisioning.domain.vo.BiosAttributeValue;
import com.example.serverprovision.provisioning.exception.InvalidBiosValueException;

/**
 * BIOS 속성 타입별 (위젯 종류 + 값 검증 + coerce) 를 상수별 메서드로 보유하는 다형성 코어.
 *
 * <p>GET 위젯 렌더와 POST 값 인식이 <b>같은 상수</b>를 호출하므로 UI 기본값과 서버 검증이 드리프트하지 않는다 (SSOT).
 * 신규 타입(Boolean / String / Map) 확장은 if/switch 줄 추가가 아니라 새 enum 상수 1개가 동일한 추상 메서드를
 * 구현하는 방식(OCP) 으로 흡수한다 — CLAUDE.md 의 "분기문 무분별 확장 금지" 원칙.</p>
 */
public enum BiosAttributeType {

	/** 고정 선택지 중 하나. 위젯=select, wire=ValueName 문자열. */
	ENUMERATION("Enumeration", "select") {
		@Override
		public void validate(BiosAttribute attr, String raw) {
			boolean match = attr.options().stream().anyMatch(o -> o.valueName().equals(raw));
			if (!match) {
				throw new InvalidBiosValueException(attr.name(), "허용되지 않은 값입니다: '" + raw + "'");
			}
		}

		@Override
		public BiosAttributeValue coerce(BiosAttribute attr, String raw) {
			return BiosAttributeValue.ofString(raw);
		}
	},

	/** 범위·증분 제약이 있는 정수. 위젯=number, wire=JSON 숫자(Long). */
	INTEGER("Integer", "number") {
		@Override
		public void validate(BiosAttribute attr, String raw) {
			long n = parseLongOrThrow(attr, raw);
			if (!attr.bounds().isValid(n)) {
				throw new InvalidBiosValueException(attr.name(),
						"허용 범위(" + attr.bounds().lower() + "~" + attr.bounds().upper()
								+ ", 증분 " + attr.bounds().htmlStep() + ")를 벗어났습니다: " + n);
			}
		}

		@Override
		public BiosAttributeValue coerce(BiosAttribute attr, String raw) {
			return BiosAttributeValue.ofLong(parseLongOrThrow(attr, raw));
		}
	},

	/** 비밀번호 문자열. 위젯=password, 빈 값은 '미변경' 으로 취급(전송 제외), wire=문자열. */
	PASSWORD("Password", "password") {
		/**
		 * U2-2-1 — 템플릿 배제 (승인 결정): 평문 BIOS 비밀번호를 DB 에 저장하지 않으며, Redfish 도
		 * Attributes 가 아닌 별도 Bios.ChangePassword 액션(BMC 연관)이라 flat values 에 실을 수 없다.
		 * 이 판정이 단일 SSOT — 템플릿 편집기 뷰모델은 위젯을 미출력(구조적 UI 차단)하고,
		 * 서버는 같은 판정으로 direct POST 를 400 거절(안전망)한다.
		 */
		@Override
		public boolean templatable() {
			return false;
		}

		@Override
		public void validate(BiosAttribute attr, String raw) {
			if (raw == null || raw.isEmpty()) {
				return; // 빈 값 = 미변경 — save 경로에서 이미 제외되지만 방어적으로 통과.
			}
			if (!attr.passwordLength().isValid(raw.length())) {
				throw new InvalidBiosValueException(attr.name(),
						"비밀번호 길이는 " + attr.passwordLength().min() + "~"
								+ attr.passwordLength().max() + " 자여야 합니다.");
			}
		}

		@Override
		public BiosAttributeValue coerce(BiosAttribute attr, String raw) {
			return BiosAttributeValue.ofString(raw);
		}
	},

	/** 참/거짓 토글. 위젯=boolean(Enabled/Disabled select), wire=JSON boolean(true/false). 레지스트리에 Value[] 없음. */
	BOOLEAN("Boolean", "boolean") {
		@Override
		public void validate(BiosAttribute attr, String raw) {
			// Boolean.parseBoolean 은 비-true 를 조용히 false 로 흡수하므로 명시적으로 "true"/"false" 만 허용.
			String v = raw == null ? "" : raw.trim();
			if (!v.equalsIgnoreCase("true") && !v.equalsIgnoreCase("false")) {
				throw new InvalidBiosValueException(attr.name(), "허용되지 않은 Boolean 값입니다: '" + raw + "' (true/false)");
			}
		}

		@Override
		public BiosAttributeValue coerce(BiosAttribute attr, String raw) {
			return BiosAttributeValue.ofBoolean(Boolean.parseBoolean(raw.trim()));
		}
	};

	private final String registryName;
	private final String widgetKind;

	BiosAttributeType(String registryName, String widgetKind) {
		this.registryName = registryName;
		this.widgetKind = widgetKind;
	}

	/** Thymeleaf 위젯 분기 및 클라이언트 {@code data-type} 값. */
	public String widgetKind() {
		return widgetKind;
	}

	/** BIOS 세팅 템플릿(정의서)에 담을 수 있는 타입인지 — PASSWORD 만 상수별 override 로 false. */
	public boolean templatable() {
		return true;
	}

	/** 검증 실패 시 {@link InvalidBiosValueException}(400). */
	public abstract void validate(BiosAttribute attr, String raw);

	/** 검증 통과 값을 Redfish 페이로드용 타입화 값으로 변환. */
	public abstract BiosAttributeValue coerce(BiosAttribute attr, String raw);

	/** 레지스트리 {@code Type} 문자열 → enum. 미지원 타입은 파서가 로드 실패로 래핑한다. */
	public static BiosAttributeType from(String registryType) {
		for (BiosAttributeType t : values()) {
			if (t.registryName.equalsIgnoreCase(registryType)) {
				return t;
			}
		}
		throw new IllegalArgumentException("지원하지 않는 BIOS 속성 타입: " + registryType);
	}

	// INTEGER 상수에서 공유하는 파싱 헬퍼. 32-bit 초과 값 대비 Long 파싱.
	private static long parseLongOrThrow(BiosAttribute attr, String raw) {
		try {
			return Long.parseLong(raw.trim());
		} catch (NumberFormatException | NullPointerException e) {
			throw new InvalidBiosValueException(attr.name(), "정수가 아닙니다: '" + raw + "'");
		}
	}
}
