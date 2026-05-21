package com.example.serverprovision.global.lifecycle;

/**
 * MK2 — 자원 lifecycle 어휘.
 *
 * <p>DB 컬럼이 아니라 {@code is_enabled} / {@code is_deprecated} / {@code is_deleted} 세 boolean 의
 * 조합을 사람이 읽을 수 있게 매핑한 enum 이다. {@code PURGED} 는 row 부재 상태이므로 enum 값으로
 * 표현하지 않는다.</p>
 *
 * @see LifecycleManageable#currentStage()
 */
public enum LifecycleStage {
	ACTIVE,
	DEPRECATED,
	SOFT_DELETED;

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
