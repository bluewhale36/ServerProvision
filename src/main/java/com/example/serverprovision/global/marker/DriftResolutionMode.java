package com.example.serverprovision.global.marker;

/**
 * S6-2-1 — drift 종류별 시스템 해결 방식 3단.
 *
 * <p>과거 {@code boolean autoApplicable} 1축은 "버튼 노출"과 "스캔 무인 자동 자격"을 겸해
 * MANUAL(사용자 확인 후 시스템이 해결) 개념을 표현할 수 없었다 — 위험해서 무인 자동은 안 되지만
 * 시스템이 해결 자체는 할 수 있는 종류(TRASH_LOST 의 row 정리 등)가 "해결 없음"과 같은 취급이 되어
 * "중요한 drift 일수록 시스템이 못 건드린다"는 문제를 만들었다.</p>
 *
 * <p>boolean 2개(manuallyResolvable + autoApplicable) 분리는 {@code manual=false·auto=true} 불법
 * 조합이 타입으로 표현 가능해 비채택 — 단일 enum 이 불법 상태를 원천 차단한다.</p>
 *
 * <ul>
 *   <li>{@link #NONE} — 시스템 해결 없음. 안내({@code operatorAction})만.</li>
 *   <li>{@link #MANUAL} — 사용자 확인([적용] 버튼) 후 시스템이 해결. 스캔 무인 적용 불가.</li>
 *   <li>{@link #AUTO} — MANUAL 에 더해 스캔 무인 적용 자격
 *       ({@code reconciliation.auto-apply.kinds} 옵트인 시 실제 발동).</li>
 * </ul>
 */
@lombok.Getter
public enum DriftResolutionMode {

	/**
	 * 시스템이 대신 처리할 수 없다. 사람이 직접 해야 한다.
	 */
	NONE("자동 해결 불가",
			"시스템이 대신 바꿔 줄 수 있는 것이 없습니다. 아래 '직접 해야 할 일' 을 사람이 수행해야 합니다.",
			"gray"),

	/**
	 * 사람이 확인한 뒤 시스템이 처리한다. 점검이 무인으로 처리하지는 않는다.
	 */
	MANUAL("확인 후 자동 해결 가능",
			"사람이 확인하면 시스템이 처리합니다. 되돌리기 어렵거나 사람의 판단이 필요한 종류라, 점검이 확인 없이 처리하지는 않습니다.",
			"yellow"),

	/**
	 * MANUAL 에 더해 점검이 무인으로 처리할 자격도 있다(옵트인 설정 시 실제 발동).
	 */
	AUTO("자동 해결 가능",
			"[해결] 을 누르면 시스템이 처리합니다. 설정에 따라 점검이 사람 확인 없이 처리하도록 맡길 수도 있습니다.",
			"green");

	/**
	 * 배지에 쓰는 한 줄 명칭. "자동으로 되는가" 를 권장 조치 문장 속에 묻어 두지 않고 한눈에 보이게 한다.
	 */
	private final String label;

	/**
	 * 배지 옆 한 줄 설명 — 그 등급이 실제로 무엇을 뜻하는지.
	 */
	private final String description;

	/**
	 * 배지 색 이름({@code n-badge-<color>}). 색을 템플릿의 조건식으로 흩지 않고 등급이 직접 들고 있다 —
	 * 등급이 늘면 여기 한 줄만 추가하면 된다.
	 */
	private final String badgeColor;

	DriftResolutionMode(String label, String description, String badgeColor) {
		this.label = label;
		this.description = description;
		this.badgeColor = badgeColor;
	}
}
