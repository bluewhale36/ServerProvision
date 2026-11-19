package com.example.serverprovision.global.lifecycle;

/**
 * MK2 — 자원 lifecycle 어휘.
 *
 * <p>DB 컬럼이 아니라 {@code is_enabled} / {@code is_deprecated} / {@code is_deleted} 세 boolean 의
 * 조합을 사람이 읽을 수 있게 매핑한 enum 이다. {@code PURGED} 는 row 부재 상태이므로 enum 값으로
 * 표현하지 않는다.</p>
 *
 * <p>HF30 D2 — 정체(메인보드 (제조사, 모델명) · OS 버전 (이름, 버전) · RAID 카드 (제조사, 모델명))가 겹칠 때 기존 행의 상태에 따라
 * 사용자가 할 다음 행동이 갈린다. 그 문장을 자원마다 if 세 갈래로 복제하지 않고 상태 상수가 소유한다. 등록과 이름 수정은
 * 다음 행동이 달라(복구 · 해제 대 다른 이름) 메서드를 둘로 둔다.</p>
 *
 * @see LifecycleManageable#currentStage()
 */
public enum LifecycleStage {
	ACTIVE {
		@Override
		public String registrationConflict(IdentityConflictSubject s) {
			return "이미 등록된 %s입니다. %s".formatted(s.noun(), s.identity());
		}

		@Override
		public String renameConflict(IdentityConflictSubject s) {
			return registrationConflict(s);
		}
	},
	DEPRECATED {
		@Override
		public String registrationConflict(IdentityConflictSubject s) {
			return "사용 중단 권고 상태인 같은 %s 있습니다(%s). 목록에서 사용 중단 권고를 해제해야 합니다."
					.formatted(s.nounAsSubject(), s.identity());
		}

		@Override
		public String renameConflict(IdentityConflictSubject s) {
			return "사용 중단 권고 상태인 같은 이름의 %s 있습니다(%s). 다른 이름을 써야 합니다."
					.formatted(s.nounAsSubject(), s.identity());
		}
	},
	SOFT_DELETED {
		@Override
		public String registrationConflict(IdentityConflictSubject s) {
			return "휴지통에 같은 %s 있습니다(%s). 휴지통에서 복구하거나 영구 삭제한 뒤 등록해야 합니다."
					.formatted(s.nounAsSubject(), s.identity());
		}

		@Override
		public String renameConflict(IdentityConflictSubject s) {
			return "휴지통에 같은 이름의 %s 있습니다(%s). 다른 이름을 쓰거나 휴지통의 %s 영구 삭제해야 합니다."
					.formatted(s.nounAsSubject(), s.identity(), s.shortAsObject());
		}
	};

	/** 같은 정체로 새로 등록하려 할 때 — 기존 행이 이 상태면 무엇을 하라고 안내할지. */
	public abstract String registrationConflict(IdentityConflictSubject subject);

	/** 이름을 바꾸다 다른 행의 정체와 겹칠 때 — 기존 행이 이 상태면 무엇을 하라고 안내할지. */
	public abstract String renameConflict(IdentityConflictSubject subject);

	/**
	 * 두 boolean 조합을 단일 lifecycle 어휘로 환산한다.
	 * <ul>
	 *   <li>{@code is_deleted=true} → 무조건 {@link #SOFT_DELETED} ({@code is_deprecated} 보존)</li>
	 *   <li>{@code is_deprecated=true && is_deleted=false} → {@link #DEPRECATED}</li>
	 *   <li>그 외 → {@link #ACTIVE}</li>
	 * </ul>
	 */
	public static LifecycleStage of(boolean isDeprecated, boolean isDeleted) {
		if (isDeleted) return SOFT_DELETED;
		if (isDeprecated) return DEPRECATED;
		return ACTIVE;
	}
}
