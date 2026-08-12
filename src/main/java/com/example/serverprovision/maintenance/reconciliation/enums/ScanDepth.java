package com.example.serverprovision.maintenance.reconciliation.enums;

import lombok.Getter;

/**
 * MK4-3-2 — 점검이 어디까지 보는가.
 *
 * <p>종전에는 {@code triggerScan(false)} · {@code triggerScan(true)} 로 불렀다. 호출부만 보면 참이
 * 무엇인지 알 수 없어, 가독성 규약이 금지하는 무명 boolean 인자에 해당했다. 트리거 경계에서만 이 타입을
 * 쓰고 내부 배관({@code performScan} · {@code runAsync} · {@code DriftReport.deep})은 그대로 둔다 —
 * 그것까지 걷어내는 것은 이 슬라이스가 아니라 별도 정비다(CP1 Q2 확정).</p>
 *
 * <p>{@link #covers(ScanDepth)} 가 이 열거형의 존재 이유다. 정밀 점검은 일반 점검이 보는 것을 모두
 * 보므로, 둘 다 밀렸을 때 정밀 하나만 돌려도 일반의 요구가 함께 채워진다. 심박의 결정 규칙이 이 한 문장에서 나온다.</p>
 */
@Getter
public enum ScanDepth {

	QUICK("일반 점검", "자원 무결성 점검", "마커 서명 검증"),
	DEEP("정밀 점검", "정밀 점검", "파일 내용 해시 재계산 포함");

	/** 화면 표기. */
	private final String label;

	/** 백그라운드 작업 목록에 뜨는 이름. */
	private final String jobTitle;

	/** 그 작업이 무엇을 하는지. */
	private final String jobDetail;

	ScanDepth(String label, String jobTitle, String jobDetail) {
		this.label = label;
		this.jobTitle = jobTitle;
		this.jobDetail = jobDetail;
	}

	/**
	 * 이 깊이가 다른 깊이를 덮는가. 정밀 점검은 일반 점검이 확인하는 것을 모두 확인한다.
	 *
	 * <p>선언 순서(ordinal)에 기대지 않고 명시적으로 판정한다 — 상수 순서를 바꾸는 것만으로 도메인
	 * 규칙이 뒤집히면 안 된다.</p>
	 */
	public boolean covers(ScanDepth other) {
		return this == DEEP || this == other;
	}

	/** 내부 배관이 아직 boolean 을 쓰므로 경계에서 되돌린다. */
	public boolean isDeep() {
		return this == DEEP;
	}

	/** 외부에서 boolean 으로 들어온 값을 경계에서 타입으로 올린다(수동 점검 요청 파라미터). */
	public static ScanDepth of(boolean deep) {
		return deep ? DEEP : QUICK;
	}
}
