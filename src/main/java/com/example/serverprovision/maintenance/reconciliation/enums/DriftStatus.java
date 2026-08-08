package com.example.serverprovision.maintenance.reconciliation.enums;

import lombok.Getter;

/**
 * MK4-1 — 문제의 생애 상태.
 *
 * <p>종전에는 드리프트가 점검마다 새로 만들어지고 해소되면 보고서에서 떨어져 나가기만 했으므로
 * 상태라는 개념 자체가 없었다. 문제를 지속시키면서 "지금 목록에 떠야 하는가" 를 판정할 축이
 * 필요해졌다.</p>
 *
 * <p>{@code boolean resolved} 와 {@code boolean snoozed} 두 원시 필드로 쪼개지 않는 이유는
 * 두 값이 동시에 참인 불법 조합이 표현 가능해지기 때문이다({@code DriftResolutionMode} 를
 * 3단 열거로 도입할 때와 같은 논거). 화면 배지 · 목록 필터 · 전이 가드가 이 한 축을 함께 본다.</p>
 */
@Getter
public enum DriftStatus {

	/**
	 * 조치 필요 — 아직 해소되지 않았고 목록에 뜬다.
	 *
	 * <p>라벨이 '열림' 이었을 때는 운영자가 무엇을 해야 하는 상태인지 읽어 내기 어려웠다. 상수 이름은
	 * 상태 축의 이름이라 {@code OPEN} 으로 두고, 화면 문구만 할 일을 가리키게 바꿨다.</p>
	 */
	OPEN("조치 필요"),

	/**
	 * 해결됨 — 시스템 처리 · 재점검 확인 · 점검 미관측 중 하나로 닫혔다. 다시 열리지 않는다
	 * (같은 신원이 재발견되면 새 문제가 생긴다).
	 */
	RESOLVED("해결됨"),

	/**
	 * 보관 — 운영자가 알면서 미룬 상태. 보관 기간이 지나면 다시 조치 필요로 돌아온다.
	 */
	SNOOZED("보관");

	private final String label;

	DriftStatus(String label) {
		this.label = label;
	}
}
