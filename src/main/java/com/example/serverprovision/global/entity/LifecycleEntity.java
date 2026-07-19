package com.example.serverprovision.global.entity;

import com.example.serverprovision.global.lifecycle.LifecycleManageable;
import com.example.serverprovision.global.lifecycle.exception.IllegalDeprecationStateException;
import com.example.serverprovision.global.lifecycle.exception.IllegalLifecycleTransitionException;
import jakarta.persistence.Column;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.PrePersist;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;

/**
 * MK2 — 운영자가 직접 관리하는 자원 (BIOS / BMC / Subprogram / OS / ISO) 의 lifecycle 공통 super.
 *
 * <p>{@code created_at} / {@code updated_at} (JPA Auditing) + lifecycle 4 boolean
 * ({@code is_enabled} / {@code is_deprecated} / {@code is_deleted}) + {@code deprecated_at} timestamp 와
 * {@link LifecycleManageable} 메서드 (가드 포함) 를 모은다. 5 엔티티가 본 super 를 상속하면서 코드 중복을
 * 제거한다 (CLAUDE.md §코딩 스타일 §중복된 코드와 가독성이 떨어지는 코드는 절대 지양 정합).</p>
 *
 * <p>{@link BaseTimeEntity} 가 별도 존재하나 본 클래스는 그것을 상속하지 않는다 — {@link BaseTimeEntity} 가
 * SuperBuilder 가 아니라 본 클래스의 {@code @SuperBuilder} 와 호환되지 않기 때문. 본 클래스가 자체 audit 필드를
 * 직접 보유한다 (created_at / updated_at 같은 컬럼).</p>
 *
 * <p><b>엔티티별 가드 메시지가 다른 경우</b> — sub-class 가 {@link #resourceLabel()} 만 override 하면 본 super 의
 * 가드 메시지가 자동으로 도메인별 라벨로 매핑된다. boolean 토글 + 가드 로직 자체는 한 곳에서만 관리된다.</p>
 */
@Getter
@MappedSuperclass
@SuperBuilder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EntityListeners(AuditingEntityListener.class)
public abstract class LifecycleEntity implements LifecycleManageable {

	@CreatedDate
	@Column(name = "created_at", nullable = false, updatable = false)
	private LocalDateTime createdAt;

	@LastModifiedDate
	@Column(name = "updated_at", nullable = false)
	private LocalDateTime updatedAt;

	// ── R4-1 — own(운영자 본래 의도) / effective(부모 제약 반영 현재값) 분리 (report 26-06-01 Option A) ──
	// is_enabled / is_deprecated = EFFECTIVE 캐시 (= own ⊕ 부모). derived query · 뷰 · R2-2 가드가 읽는 값.
	// own_enabled / own_deprecated = 운영자 의도. cascade 는 own 을 절대 갱신하지 않는다.

	@Column(name = "is_enabled", nullable = false)
	@Builder.Default
	private boolean isEnabled = true;            // effective

	@Column(name = "is_deleted", nullable = false)
	@Builder.Default
	private boolean isDeleted = false;

	@Column(name = "is_deprecated", nullable = false)
	@Builder.Default
	private boolean isDeprecated = false;        // effective

	@Column(name = "own_enabled", nullable = false)
	@Builder.Default
	private boolean ownEnabled = true;           // 운영자 의도

	@Column(name = "own_deprecated", nullable = false)
	@Builder.Default
	private boolean ownDeprecated = false;       // 운영자 의도

	@Column(name = "deprecated_at")
	private Instant deprecatedAt;

	/**
	 * MK3 — soft-delete 시각. trash 이동 동시 기록.
	 * <p>{@code is_deleted=true} && {@code trashed_path != null} 조합이 정상 soft-deleted 상태.</p>
	 */
	@Column(name = "trashed_at")
	private Instant trashedAt;

	/**
	 * MK3 — 휴지통 내 절대 경로 (timestamp + UUID8 suffix 포함).
	 * <p>{@code is_deleted=true} && {@code trashed_path=null} 조합은 자원 부재 상태 (A2 케이스).</p>
	 * <p>운영자 의도 보존을 위해 기존 도메인의 path 컬럼 ({@code iso_path} 등) 은 soft-delete 시 변경하지 않는다.</p>
	 */
	@Column(name = "trashed_path", length = 1024)
	private String trashedPath;

	/**
	 * HF4-1 — 보존기간 연장 누적 일수. 연장 1회 = +step 일 가산 (step = 운영 설정 TTL 일수).
	 * <p>만료 = {@code trashedAt + TTL + ttlExtensionDays일} ({@link #trashExpiresAt(Duration)} 가 SSOT).
	 * trashed_at 재기준 방식(구 구현)이 "휴지통 이동 시각" 표시까지 왜곡하던 것의 해소 — 이동 시각 불변.
	 * TTL 자체는 컬럼에 굳히지 않아 운영 설정 변경이 기존 행에도 동적 반영된다.</p>
	 */
	@Column(name = "ttl_extension_days", nullable = false)
	@Builder.Default
	private int ttlExtensionDays = 0;

	/**
	 * 가드 메시지에 노출할 자원 라벨. sub-class 가 도메인 명칭으로 override (예: "BIOS", "BMC", "ISO").
	 * 기본값은 클래스 simple name 이라 override 안 해도 동작 가능.
	 */
	protected String resourceLabel() {
		return getClass().getSimpleName();
	}

	/**
	 * 자원 식별자 — 가드 메시지에 노출용. sub-class 가 자기 PK 를 반환.
	 */
	protected abstract Long resourceId();

	/**
	 * R4-1 — 이 자원의 부모 lifecycle 자원. 루트(BoardModel/OSMetadata)·공용 자원은 {@code null}.
	 * <p>{@link #recomputeEffective()} 가 부모 effective 를 읽어 own 과 결합한다.</p>
	 */
	protected abstract LifecycleEntity parentLifecycle();

	/**
	 * R4-1 — effective(is_enabled/is_deprecated) 를 own ⊕ 부모 제약으로 재계산한다. own 은 건드리지 않는다.
	 * <p><b>차원 독립</b> : 부모가 <b>비활성</b>이면 자식 effective 도 비활성(enabled 차원), 부모가 <b>deprecated</b>면
	 * 자식 effective 도 deprecated(deprecated 차원). 두 차원은 서로 간섭하지 않는다 — deprecated 는 enabled 를
	 * 끄지 않는다(deprecated ≠ disabled). 부모가 회복되면 보존된 own 으로 자동 복원(UP). 루트는 부모가 없어 effective = own.</p>
	 * <p>호출 지점 : 운영자 own 전이(아래 메서드) · 부모 cascade(service) · 자식 restore · 신규 생성 직후.</p>
	 */
	public final void recomputeEffective() {
		LifecycleEntity p = parentLifecycle();
		boolean pDeprecated = (p != null) && p.isDeprecated();
		boolean pEnabled    = (p == null) || p.isEnabled();
		this.isDeprecated = this.ownDeprecated || pDeprecated;   // 부모 deprecate → 자식 deprecate
		this.isEnabled    = this.ownEnabled && pEnabled;          // 부모 disable → 자식 disable (deprecated 는 무관)
	}

	/**
	 * R4-1 — 신규 영속(insert) 직전 effective 를 own ⊕ 부모 로 초기화한다.
	 * <p>모든 생성 경로(BIOS/BMC/ISO/Subprogram upload·create)를 한 곳에서 일관 처리 → "생성 시 recompute
	 * 누락" 드리프트 차단. 부모가 deprecated/disabled 상태에서 만든 자식도 즉시 effective 가 부모를 반영한다.
	 * (own 컬럼은 @Builder.Default true/false → 신규 자원의 의도는 활성·비deprecated.)</p>
	 */
	@PrePersist
	private void onCreateRecomputeEffective() {
		recomputeEffective();
	}

	/** 운영자 직접 활성/비활성 토글 — own 만 뒤집고 effective 재계산. */
	public void toggleEnabled() {
		this.ownEnabled = !this.ownEnabled;
		recomputeEffective();
	}

	@Override
	public void softDelete() {
		if (this.isDeleted) {
			throw new IllegalLifecycleTransitionException(
					"이미 삭제된 " + resourceLabel() + " 입니다 : " + resourceId());
		}
		this.isDeleted = true;
	}

	@Override
	public void restore() {
		if (!this.isDeleted) {
			throw new IllegalLifecycleTransitionException(
					"삭제 상태가 아닌 " + resourceLabel() + " 는 복구할 수 없습니다 : " + resourceId());
		}
		this.isDeleted = false;
		recomputeEffective();   // R4-1 — 복원 시 현재 부모 기준 effective 재계산 (own 보존)
	}

	@Override
	public void deprecate() {
		if (this.isDeleted) {
			throw new IllegalDeprecationStateException(
					"삭제된 " + resourceLabel() + " 는 deprecate 할 수 없습니다 : " + resourceId());
		}
		if (this.ownDeprecated) {   // R4-1 — own 기준 self-guard
			throw new IllegalDeprecationStateException(
					"이미 deprecated 상태입니다 : " + resourceId());
		}
		this.ownDeprecated = true;
		this.deprecatedAt = Instant.now();
		recomputeEffective();
	}

	@Override
	public void undeprecate() {
		if (!this.ownDeprecated) {   // R4-1 — own 기준 self-guard
			throw new IllegalDeprecationStateException(
					"deprecated 상태가 아닙니다 : " + resourceId());
		}
		this.ownDeprecated = false;
		this.deprecatedAt = null;
		recomputeEffective();
	}

	// ==== R2-2 — 부모-자식 lifecycle 가드 SSOT =============================
	// "이 자원이 부모일 때 자식의 해당 액션을 막는가" 를 부모 자신의 lifecycle boolean 으로 판정.
	// (1) 서버 가드 throw 조건 (2) 뷰모델 capability 플래그가 같은 메서드 바디를 공유 → UI ↔ 서버 드리프트 0.

	/**
	 * 자식 enable(활성화) 차단 사유 라벨. {@code null} = 차단 안 함.
	 * 부모가 <b>비활성 또는 삭제</b> 상태면 차단한다. <b>deprecated 는 차단하지 않는다</b> — 차원 독립
	 * (deprecated ≠ disabled). 부모가 deprecated 이어도 enabled 면 자식은 양방향(활성↔비활성) 가능.
	 * <p>참고 : {@code is_enabled = own_en AND 부모_en} 이라 부모 비활성 시 자식 enable 은 effective 무효 → 차단 유지.</p>
	 * toggle 가드 + {@code ChildLifecycleBlockedByParentException} 의 parentState 가 공용으로 쓴다.
	 */
	public String childEnableBlockReason() {
		return isDeleted ? "DELETED" : !isEnabled ? "DISABLED" : null;
	}

	public boolean blocksChildEnable() {
		return childEnableBlockReason() != null;
	}

	/** 부모가 삭제 상태면 자식 restore 불가 (부모부터 복구해야 함). */
	public boolean blocksChildRestore() {
		return isDeleted;
	}

	/** 부모가 deprecated 또는 삭제 상태면 자식 undeprecate 불가. */
	public boolean blocksChildUndeprecate() {
		return isDeprecated || isDeleted;
	}

	// self-guard (휴지통 자원 자체의 toggle/deprecate/undeprecate 동결) 는 각 service 의 requireLive* 헬퍼가
	// 이미 enforce (deleted 자원에 Illegal*StateException) → 별도 isLifecycleFrozen 메서드 불필요.

	/**
	 * MK3 — soft-delete 시 trash 이동 직후 caller 가 호출. trashedAt + trashedPath 기록.
	 * <p>{@link #softDelete()} 와 별개 — 시그니처를 분리해 기존 호출자를 깨지 않는다.
	 * 정상 흐름 : {@code softDelete() → trashService.moveToTrash() → markTrashed(trashedPath)}.</p>
	 *
	 * @param trashedPath trash 내 절대 경로 (timestamp + UUID8 suffix 포함)
	 */
	public void markTrashed(String trashedPath) {
		this.trashedAt = Instant.now();
		this.trashedPath = trashedPath;
		this.ttlExtensionDays = 0;   // HF4-1 — 휴지통 진입 시 연장 누적 초기화 (재삭제 시 이전 연장 이월 방지)
	}

	/**
	 * MK3 — restore 시 trash 복원 직후 caller 가 호출. trashedAt / trashedPath 초기화.
	 * <p>{@link #restore()} 와 별개 — 정상 흐름 : {@code trashService.moveBack() → restore() → clearTrashed()}.</p>
	 */
	public void clearTrashed() {
		this.trashedAt = null;
		this.trashedPath = null;
		this.ttlExtensionDays = 0;   // HF4-1 — 복원 경로에서도 연장 누적 초기화
	}

	/**
	 * HF4-1 — 보존기간 +days 가산 연장. trashed_at 은 불변 — 만료일만 뒤로 민다.
	 * <p>trashed 상태가 아닌 자원 연장은 도메인 invariant 위반 — UI/서비스 가드를 우회한 비정상 경로 방어.</p>
	 */
	public void extendTrashTtl(int days) {
		if (this.trashedAt == null) {
			throw new IllegalLifecycleTransitionException(
					"휴지통에 없는 " + resourceLabel() + " 는 보존기간을 연장할 수 없습니다 : " + resourceId());
		}
		this.ttlExtensionDays += days;
	}

	/**
	 * HF4-1 — trash 만료 시각 SSOT : {@code trashedAt + baseTtl + ttlExtensionDays일}.
	 * <p>화면 표시({@code TrashController.toResponse})와 자동 영구삭제 판정({@code TrashTtlWorker})이
	 * 같은 식을 각자 계산하며 갈라지던 것을 본 메서드 하나로 통일. trashed 상태가 아니면 {@code null}.</p>
	 */
	public Instant trashExpiresAt(Duration baseTtl) {
		if (this.trashedAt == null) {
			return null;
		}
		return this.trashedAt.plus(baseTtl).plus(Duration.ofDays(this.ttlExtensionDays));
	}
}
