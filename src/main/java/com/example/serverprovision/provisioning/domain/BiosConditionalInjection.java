package com.example.serverprovision.provisioning.domain;

import com.example.serverprovision.provisioning.domain.vo.BiosAttributeName;

import java.util.List;

/**
 * 조건부 orphan 속성 주입 정의 (예외 처리).
 * <p>레지스트리(JSON)에는 있으나 SetupData(XML) 페이지 트리에는 없는 속성 중, 실제 BIOS 에서 다른 속성 값에 따라
 * 동적으로 활성화되는 항목을 표현한다. 이 종속은 XML 구조에도, 레지스트리 {@code Dependencies} 에도 드러나지 않는
 * 펌웨어 암시적 규칙이라, 전수 탐색 대신 알려진 케이스를 예외로 등록해 처리한다.</p>
 *
 * <p>대표 케이스: {@code Power Performance Tuning} 값이 "BIOS Controls EPB" 일 때만
 * {@code ENERGY_PERF_BIAS_CFG mode} 선택이 활성화되고, 그 외 값에서는 {@code forcedValue} 로 강제(비활성)된다.
 * 칩셋 세대마다 AttributeName 이 달라(BirchStream=Xeon6: {@code BirchStream0064/0065},
 * EagleStream=Sapphire/Emerald Rapids: {@code EagleStream0088/0089}) 세대별로 항목을 등록한다.</p>
 *
 * @param afterAttribute  이 속성 위젯 바로 뒤에 orphan 을 주입한다 (=종속의 controller)
 * @param orphanAttribute XML 에 없는 주입 대상 속성
 * @param enableWhenValue controller 가 이 값일 때만 orphan 활성화
 * @param forcedValue     controller 가 다른 값일 때 orphan 이 강제되는 값
 */
public record BiosConditionalInjection(
		BiosAttributeName afterAttribute,
		BiosAttributeName orphanAttribute,
		String enableWhenValue,
		String forcedValue
) {

	/**
	 * 알려진 예외 목록. lookup 키가 attribute 이름이라 해당 속성이 없는 보드에는 자연히 적용되지 않는다
	 * (예: {@code BirchStream0064} 는 MS74-HB0 에만 존재).
	 */
	private static final List<BiosConditionalInjection> KNOWN = List.of(
			// BirchStream (Xeon 6) — MS04-CE0 / MS74-HB0
			new BiosConditionalInjection(
					BiosAttributeName.of("BirchStream0064_PowerPerformanceTuning"),
					BiosAttributeName.of("BirchStream0065_ENERGYPERFBIASCFGmode"),
					"BIOS Controls EPB",
					"Balanced Performance"),
			// EagleStream (Sapphire/Emerald Rapids) — MS03-CE0 / MS73-HB1 (동일 종속, 다른 AttributeName)
			new BiosConditionalInjection(
					BiosAttributeName.of("EagleStream0088"),
					BiosAttributeName.of("EagleStream0089"),
					"BIOS Controls EPB",
					"Balanced Performance")
	);

	/** 주어진 controller 뒤에 주입할 orphan 목록. */
	public static List<BiosConditionalInjection> after(BiosAttributeName controller) {
		return KNOWN.stream().filter(i -> i.afterAttribute().equals(controller)).toList();
	}
}
