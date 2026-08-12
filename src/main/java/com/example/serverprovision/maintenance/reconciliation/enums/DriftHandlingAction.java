package com.example.serverprovision.maintenance.reconciliation.enums;

import com.example.serverprovision.global.marker.DriftKind;
import lombok.Getter;

/**
 * MK4-1 — 문제에 가한 처리의 종류. 처리 이력({@code DriftHandling})의 분류축이다.
 *
 * <p>문자열로 두지 않는 이유는 이력 조회가 오타에 뚫리고, 사용자 문구와 되돌리기 가능 판정을
 * 함께 지녀야 하기 때문이다. 라벨과 판정을 상수가 직접 갖는 것은 {@link DriftKind} 의 선례를
 * 따른 것이다.</p>
 */
@Getter
public enum DriftHandlingAction {

	/**
	 * 표준 [적용] — 종류별 해결 전략이 수행한 처리. 되돌리기 가능 여부는 드리프트 종류가 정한다
	 * (기록을 지우는 종류는 되돌릴 근거가 없다).
	 */
	APPLY("적용") {
		@Override
		public boolean reversibleFor(DriftKind kind) {
			return kind.isReversible();
		}
	},

	/**
	 * MK4-3-1 — 점검 도중 시스템이 사람 확인 없이 처리해 닫힘.
	 *
	 * <p>{@link #APPLY} 와 가르는 이유는 원장이 사실을 말해야 하기 때문이다 — 운영자가 눌러서 처리한
	 * 것과 시스템이 스스로 처리한 것은 나중에 "왜 이렇게 됐는가" 를 되짚을 때 전혀 다른 정보다.
	 * 되돌릴 수 있는지는 무엇을 했는지가 정하므로 {@code APPLY} 와 같은 기준을 쓴다.</p>
	 */
	AUTO_APPLY("점검 중 자동 처리") {
		@Override
		public boolean reversibleFor(DriftKind kind) {
			return kind.isReversible();
		}
	},

	/**
	 * [다시 점검] 이 해소를 확인해 닫힘. 시스템이 무언가를 바꾼 것이 아니라 이미 해소된 것을
	 * 확인한 것이므로 되돌릴 대상이 없다.
	 */
	RECHECK_RESOLVED("다시 점검 — 해소 확인") {
		@Override
		public boolean reversibleFor(DriftKind kind) {
			return false;
		}
	},

	/**
	 * 내용 변경을 정본으로 수용. 등록 지문을 되돌리면 원상 복구되므로 가역이다.
	 */
	ACCEPT_HASH("현재 내용을 정본으로 수용") {
		@Override
		public boolean reversibleFor(DriftKind kind) {
			return true;
		}
	},

	/**
	 * 자원 중복을 택일로 해소. 선택되지 않은 쪽 파일은 이미 지워졌으므로 되돌릴 수 없고,
	 * 어느 쪽을 남겼는지 기록만 남는다.
	 */
	RESOLVE_DUPLICATE("자원 중복 해소") {
		@Override
		public boolean reversibleFor(DriftKind kind) {
			return false;
		}
	},

	/**
	 * 보관 — 목록에서 내렸을 뿐 자원과 파일은 그대로다. 되돌리기는 해제로 한다.
	 */
	SNOOZE("보관") {
		@Override
		public boolean reversibleFor(DriftKind kind) {
			return false;
		}
	},

	/**
	 * 보관 해제(만료 또는 수동). 상태 복귀 자체가 되돌림이라 별도 되돌리기가 없다.
	 */
	UNSNOOZE("보관 해제") {
		@Override
		public boolean reversibleFor(DriftKind kind) {
			return false;
		}
	},

	/**
	 * 점검이 그 종류를 커버했는데 더 이상 발견되지 않아 자동으로 닫힘. 운영자가 파일을 직접
	 * 되돌려 놓은 경우가 대표적이다. 시스템이 바꾼 것이 없으므로 되돌릴 대상이 없다.
	 */
	SCAN_UNOBSERVED("점검에서 사라짐 — 자동 해소") {
		@Override
		public boolean reversibleFor(DriftKind kind) {
			return false;
		}
	},

	/**
	 * S11-2 — 같은 회차에 같은 자원의 새 종류 문제가 열리며 이 문제가 재분류로 닫힘. 후임이
	 * {@code Drift.predecessor} 로 이 문제를 가리킨다. {@code SCAN_UNOBSERVED} 와 가르는 이유는
	 * 이력 문구가 사실을 말해야 하기 때문이다 — "점검에서 사라짐 — 자동 해소" 는 상황이 끝났다고
	 * 읽히지만, 재분류는 같은 사건이 다른 이름으로 계속되는 것이다. 시스템이 바꾼 것이 없으므로
	 * 되돌릴 대상은 없다.
	 */
	SUPERSEDED("재분류로 이어짐") {
		@Override
		public boolean reversibleFor(DriftKind kind) {
			return false;
		}
	};

	private final String label;

	DriftHandlingAction(String label) {
		this.label = label;
	}

	/**
	 * 이 처리를 그 종류의 문제에 가했을 때 되돌릴 수 있는가. 기록 시점의 판정을
	 * {@code DriftHandling.reversible} 컬럼에 얼려 둔다 — 판정 규칙이 나중에 바뀌어도
	 * 과거 이력의 사실은 변하지 않아야 하기 때문이다(탐지 수 스냅샷과 같은 원리).
	 */
	public abstract boolean reversibleFor(DriftKind kind);
}
