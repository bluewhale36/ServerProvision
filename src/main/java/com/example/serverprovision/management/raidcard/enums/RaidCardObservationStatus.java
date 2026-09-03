package com.example.serverprovision.management.raidcard.enums;

import java.util.Set;

/**
 * 카드 관측 상태(E3.5-5-b D2) — 화면의 배지 · 버튼 · tooltip 과 서비스의 409 가드가 같은 값을 본다(SSOT).
 *
 * <p>진리표(관측 distinct 수 × 확정값): 0 → NONE / 2 이상 → CONFLICTING / 1 → 확정값 없음 AGREED_UNCONFIRMED ·
 * 같음 MATCHES_CONFIRMED · 다름 DIFFERS_FROM_CONFIRMED. 확정은 AGREED_UNCONFIRMED 에서만 열린다 — 확정값이 있으면
 * 관측으로 덮어쓰지 않는다(정정은 수정 폼).</p>
 */
public enum RaidCardObservationStatus {

	NONE(false, null, "이 카드를 지정한 서버의 관측이 없습니다.") {
		@Override
		public boolean showsButton(boolean confirmed) {
			return false;
		}
	},
	AGREED_UNCONFIRMED(true, "n-badge-blue", null) {
		@Override
		public boolean showsButton(boolean confirmed) {
			return true;
		}
	},
	MATCHES_CONFIRMED(false, "n-badge-green", "이미 확정된 카드입니다 — 정정은 수정에서 하십시오.") {
		@Override
		public boolean showsButton(boolean confirmed) {
			return false;
		}
	},
	DIFFERS_FROM_CONFIRMED(false, "n-badge-red", "이미 확정된 카드입니다 — 정정은 수정에서 하십시오.") {
		@Override
		public boolean showsButton(boolean confirmed) {
			return false;
		}
	},
	/** 잠긴 버튼 + tooltip 은 미확인 카드에서만 — 확정된 카드에 "확인한 뒤 확정하십시오" 는 성립하지 않는다(CP5 F-2). */
	CONFLICTING(false, "n-badge-red", "관측이 서로 다릅니다 — 정의서의 카드 지정을 확인한 뒤 확정하십시오.") {
		@Override
		public boolean showsButton(boolean confirmed) {
			return !confirmed;
		}
	};

	private final boolean confirmable;
	private final String badgeClass;
	private final String blockReason;

	RaidCardObservationStatus(boolean confirmable, String badgeClass, String blockReason) {
		this.confirmable = confirmable;
		this.badgeClass = badgeClass;
		this.blockReason = blockReason;
	}

	/**
	 * @param confirmedDisplay 자원의 확정값({@code PciSubsystemId.toDisplay()}) 또는 null(미확인)
	 * @param distinctObserved 관측값의 집합(소문자 정규형)
	 */
	public static RaidCardObservationStatus of(String confirmedDisplay, Set<String> distinctObserved) {
		if (distinctObserved.isEmpty()) {
			return NONE;
		}
		if (distinctObserved.size() >= 2) {
			return CONFLICTING;
		}
		if (confirmedDisplay == null) {
			return AGREED_UNCONFIRMED;
		}
		return distinctObserved.iterator().next().equalsIgnoreCase(confirmedDisplay) ? MATCHES_CONFIRMED : DIFFERS_FROM_CONFIRMED;
	}

	/** [관측값으로 확정] 이 허용되는가 — 서비스 가드와 버튼 활성이 함께 본다. */
	public boolean confirmable() {
		return confirmable;
	}

	/**
	 * 버튼을 그리는가 — AGREED 는 활성, CONFLICTING 은 미확인 카드에서만 잠긴 채(UI 1차 차단 · tooltip 사유),
	 * 나머지 불허 상태는 숨긴다.
	 *
	 * @param confirmed 카드에 확정값이 있는가
	 */
	public abstract boolean showsButton(boolean confirmed);

	public String badgeClass() {
		return badgeClass;
	}

	/** 확정 불허 사유 — tooltip 과 409 응답이 같은 문장을 쓴다. 허용 상태는 null. */
	public String blockReason() {
		return blockReason;
	}
}
