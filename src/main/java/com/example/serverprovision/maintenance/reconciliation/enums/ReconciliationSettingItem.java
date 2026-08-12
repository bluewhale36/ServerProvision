package com.example.serverprovision.maintenance.reconciliation.enums;

import com.example.serverprovision.global.marker.DriftKind;
import lombok.Getter;

import java.util.Arrays;
import java.util.stream.Collectors;

/**
 * MK4-3-1 — 운영 설정의 <b>항목 카탈로그</b>. 어떤 설정이 있는지, 그것이 무엇을 정하는지, 어떤 형태의
 * 값을 갖는지, 손대지 않았을 때의 값은 무엇인지가 전부 여기 있다.
 *
 * <p>설정을 항목별 행으로 저장하기로 하면서(D5 개정) 이 열거형이 그 행들의 신원이 된다. 저장은
 * 이 상수의 이름을 키로 쓰므로 <b>키 오타가 생길 수 없고</b>, 어떤 항목이 있어야 하는지는 코드가 답한다.
 * 키-값 저장이 흔히 욕먹는 이유(키가 무엇인지 아무도 모른다)가 여기서는 성립하지 않는다.</p>
 *
 * <p>설명을 이 자리에 두는 이유는 따로 있다. 설정 이름만으로는 무엇이 달라지는지 알 수 없는 항목이
 * 대부분이다 — "시스템 해결" 이 탐지까지 멈추는지 아닌지가 이름에 드러나지 않는 것이 그 예다.
 * 템플릿에 문장을 직접 쓰면 문서 · 화면 · 코드가 각자 다른 말을 하게 되고 항목이 늘 때 하나를
 * 빠뜨려도 드러나지 않는다. 여기 두면 새 항목이 상수 하나로 늘면서 설명도 함께 온다.</p>
 */
@Getter
public enum ReconciliationSettingItem {

	AUTO_APPLY_KINDS(
			"자동 처리 대상",
			"점검 도중 시스템이 사람 확인 없이 스스로 처리할 드리프트 종류입니다. "
					+ "여기 없는 종류는 발견만 하고 그대로 둡니다.",
			EffectTiming.NEXT_SCAN, ValueType.KIND_SET, null) {
		/**
		 * 무인 처리를 기본으로 맡길 종류 — <b>되돌릴 수 있는 것만</b> 켠다.
		 *
		 * <p>무인에 맡길지의 기준을 "잘못돼도 되돌릴 수 있는가" 한 문장으로 둔다. 새 축을 만들지 않고
		 * 종류가 이미 들고 있는 성질을 쓰므로, 자동 처리 등급의 종류가 늘어도 여기에 이름을 더할 필요가
		 * 없다. 되돌릴 수 없는 종류(유령 DB 기록 — 남은 기록을 영구 삭제한다)는 기본으로 끈다.</p>
		 */
		@Override
		public String defaultValue() {
			return Arrays.stream(DriftKind.values())
					.filter(DriftKind::isAutoApplicable)
					.filter(DriftKind::isReversible)
					.map(Enum::name)
					.collect(Collectors.joining(","));
		}
	},

	RESOLUTION_ENABLED(
			"시스템 해결",
			"드리프트를 고치는 행위 전체의 차단기입니다. 끄면 수동 [해결] 과 점검 중 자동 처리가 "
					+ "모두 멈추고 탐지만 계속합니다.",
			EffectTiming.IMMEDIATE, ValueType.BOOLEAN, "true"),

	REPORT_RETENTION_COUNT(
			"보고서 보관 개수",
			"점검 보고서를 몇 회분까지 남길지 정합니다. 넘으면 오래된 것부터 지웁니다.",
			EffectTiming.NEXT_SCAN, ValueType.INTEGER, "100"),

	EXTRA_SCAN_ROOTS(
			"추가 점검 경로",
			"기본 점검 범위(활성 자원이 등록된 폴더들) 밖에서 추가로 뒤질 경로입니다. "
					+ "백업 폴더처럼 자원을 옮겨 두는 자리를 범위에 넣으면 '자원 소실' 대신 "
					+ "'경로 이동됨' 으로 잡힙니다.",
			EffectTiming.NEXT_SCAN, ValueType.PATH_LIST, ""),

	STARTUP_SCAN_ENABLED(
			"기동 직후 점검",
			"애플리케이션이 뜬 직후 한 번 점검할지 정합니다. 꺼져 있는 동안 디스크가 바뀌었을 수 있어 "
					+ "기동 시 한 번 맞춰 봅니다.",
			EffectTiming.NEXT_BOOT, ValueType.BOOLEAN, "true"),

	SCAN_INTERVAL(
			"일반 점검 주기",
			"일반 점검을 얼마나 자주 돌릴지 분 단위로 정합니다. 일반 점검은 마커와 위치만 보고 "
					+ "파일 내용은 보지 않습니다. 마지막 점검으로부터 이만큼 지나면 다시 돕니다.",
			EffectTiming.IMMEDIATE, ValueType.INTEGER, "60"),

	DEEP_SCAN_INTERVAL(
			"정밀 점검 주기",
			"정밀 점검을 얼마나 자주 돌릴지 분 단위로 정합니다. 정밀 점검은 파일 내용의 지문까지 다시 "
					+ "계산해 변조를 잡습니다. 마지막 정밀 점검으로부터 이만큼 지나면 다시 돕니다.",
			EffectTiming.IMMEDIATE, ValueType.INTEGER, "1440");

	private final String label;
	private final String description;
	private final EffectTiming effectTiming;
	private final ValueType valueType;
	private final String staticDefault;

	ReconciliationSettingItem(String label, String description, EffectTiming effectTiming,
			ValueType valueType, String staticDefault) {
		this.label = label;
		this.description = description;
		this.effectTiming = effectTiming;
		this.valueType = valueType;
		this.staticDefault = staticDefault;
	}

	/**
	 * 손대지 않았을 때의 값. 대부분은 선언에 적힌 값이고, 자동 처리 대상만 종류에서 파생하므로 재정의한다.
	 */
	public String defaultValue() {
		return staticDefault == null ? "" : staticDefault;
	}

	/** 값이 어떤 형태인가. 검증과 화면 입력 요소를 이 값으로 가른다. */
	public enum ValueType {
		BOOLEAN, INTEGER, KIND_SET, PATH_LIST
	}

	/**
	 * 저장이 언제부터 효과를 내는가. 화면이 항목 옆에 그대로 밝힌다.
	 */
	@Getter
	public enum EffectTiming {

		/** 저장 즉시. 다음 요청부터 새 값이 쓰인다. */
		IMMEDIATE("저장 즉시"),

		/** 다음 점검부터. 값은 점검이 시작될 때 읽힌다. */
		NEXT_SCAN("다음 점검부터"),

		/** 다음 기동부터. 이번 실행에는 영향이 없다. */
		NEXT_BOOT("다음 기동부터");

		private final String label;

		EffectTiming(String label) {
			this.label = label;
		}
	}
}
