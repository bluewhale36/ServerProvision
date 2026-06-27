package com.example.serverprovision.management.os.service.metadata;

import com.example.serverprovision.global.trash.TrashLifecycleService;
import com.example.serverprovision.global.trash.service.TypedNameGuard;
import com.example.serverprovision.management.common.dto.response.RestoreResponse;
import com.example.serverprovision.management.os.entity.ISO;
import com.example.serverprovision.management.os.entity.OSMetadata;
import com.example.serverprovision.management.os.exception.DuplicateOSMetadataException;
import com.example.serverprovision.management.os.exception.IllegalOSMetadataStateException;
import com.example.serverprovision.management.os.exception.OSMetadataNotFoundException;
import com.example.serverprovision.management.os.repository.OSMetadataRepository;
import com.example.serverprovision.management.os.service.iso.IsoLifecycleService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * R1-3 — OSMetadata 의 lifecycle 책임을 응집한 단일 service.
 *
 * <p>enabled / deprecated / soft-deleted 필드 전이 + purge 종착점의 8 메서드를 한 곳에 모은다.
 * 자식 ISO 의 lifecycle cascade 가 필요한 경우 ({@link #restore(Long, boolean)} cascade,
 * {@link #purge(Long)} 의 sidecar 정리) 는 {@link IsoLifecycleService} 의 ISO 측 메서드
 * ({@code restore} / {@code cleanupArtifacts}) 에 위임한다 — 이 의존은 단방향
 * (parent → leaf) 으로 cycle 없다 (R1-4-1).</p>
 *
 * <p>잔류 사유 (본 service 에 안 포함된 lifecycle 인접 항목) :
 * <ul>
 *   <li>{@code purgeIsoWithTypedNameCheck} — ISO 도메인의 lifecycle (R2-iso 영역)</li>
 * </ul></p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class OSMetadataLifecycleService implements com.example.serverprovision.global.lifecycle.LifecycleService {

	private final OSMetadataRepository osMetadataRepository;
	private final TrashLifecycleService trashLifecycleService;
	// R1-4-1 — 자식 ISO 의 cascade restore / sidecar 정리는 IsoLifecycleService 에 위임.
	// 옛 R1-3 의 OSMetadataService 의존을 IsoLifecycleService 로 교체 — parent → leaf 단방향, cycle 없음.
	private final IsoLifecycleService isoLifecycleService;

	// ==== enabled 토글 =================================================

	/**
	 * S5-2-3-1 / HF-2 — OS 활성/비활성 토글 + 자식 ISO 비대칭 cascade.
	 * <p><b>disable</b> (부모 → 비활성) : enabled 자식 전부(trash / deprecated 포함) 를 비활성화 →
	 * invariant "자식 enabled ≤ 부모 enabled" 유지. soft-deleted 자식이 부모 disable 을 우회해 stale enabled 로
	 * 남았다가 restore 시 모순으로 부활하던 결함(HF-2 Leg A) 을 차단한다.</p>
	 * <p><b>enable</b> (부모 → 활성) : 자식 cascade 안 함. 부모 ceiling 만 상승하고, 개별 자식 활성화는 운영자가
	 * 명시적으로 수행한다 — 부모 활성화가 운영자가 개별 비활성한 자식을 임의로 되살리지 않도록 한다.</p>
	 */
	@Override
	@Transactional
	public void toggleEnabled(Long id) {
		OSMetadata parent = requireActiveImage(id);
		parent.toggleEnabled();
		recomputeIsos(parent);   // R4-1 — 양방향 : own 보존, 부모 effective 변화만 자식에 반영
		log.info("[lifecycle.toggle] resource=OS_IMAGE#{} enabled={} outcome=toggled", id, parent.isEnabled());
	}

	/** R4-1 — 자식 ISO effective 재계산. own 보존, soft-deleted ISO 제외(restore 시 개별 재계산). */
	private void recomputeIsos(OSMetadata parent) {
		parent.getIsos().stream()
				.filter(iso -> !iso.isDeleted())
				.forEach(ISO::recomputeEffective);
	}

	// ==== soft delete / restore ========================================

	/**
	 * OS 메타데이터 soft delete. S5-2-3 정합화 — 자식 ISO 동반 trash 이동.
	 *
	 * <p>OSMetadata 자체는 메타 자원 (디스크 파일 없음) → entity.softDelete() + markTrashed(null) 로 lifecycle 메타만 갱신.
	 * 자식 ISO 들 (활성) 은 {@link TrashLifecycleService#softDeleteToTrash} 위임 — 정상 trash 이동 + DB 갱신.</p>
	 */
	@Override
	@Transactional
	public void softDelete(Long id) {
		OSMetadata image = requireActiveImage(id);
		for (ISO iso : image.getIsos()) {
			if (!iso.isDeleted()) {
				trashLifecycleService.softDeleteToTrash(iso);
			}
		}
		image.softDelete();
		image.markTrashed(null);
	}

	// R1-3 — restore(Long id) 는 LifecycleService default 메서드 (restore(id, false) 위임) 가 자동 처리.

	/**
	 * S5-2-3 — OS 메타데이터 restore + 하위 ISO 일괄 복구 옵션.
	 *
	 * <p>cascade=true 면 soft-deleted 자식 ISO 를 일괄 복구. 각 ISO 의 restore 는
	 * {@link IsoLifecycleService#restore(Long)} 위임 (R1-4-1) — trashLifecycleService.restoreFromTrash 흐름 재사용.</p>
	 *
	 * <p>OSEnvironment / OSPackageGroup 은 ISO soft-delete 시 DB hard-delete 됐으므로 cascade 대상 외.</p>
	 */
	@Override
	@Transactional
	public RestoreResponse restore(Long id, boolean cascade) {
		OSMetadata image = osMetadataRepository.findByIdAndIsDeletedTrue(id)
				.orElseThrow(() -> new IllegalOSMetadataStateException(
						"이미 활성 상태이거나 존재하지 않는 OS 버전입니다. id=" + id));
		if (osMetadataRepository.existsByOsNameAndOsVersionAndIsDeletedFalse(image.getOsName(), image.getOsVersion())) {
			throw new DuplicateOSMetadataException(image.getOsName(), image.getOsVersion());
		}
		image.restore();
		image.clearTrashed();
		if (!cascade) {
			return RestoreResponse.none();
		}
		int restored = 0;
		for (ISO iso : image.getIsos()) {
			if (iso.isDeleted()) {
				isoLifecycleService.restore(iso.getId());   // R1-4-1 — interface default 활용 (cascade=false)
				restored++;
			}
		}
		log.info("[restore] OSMetadata id={} cascade=true → 하위 ISO {}건 복구", id, restored);
		return new RestoreResponse(restored);
	}

	// ==== deprecate / undeprecate ======================================

	/**
	 * R4-1 — OS deprecate + 자식 ISO effective 재계산.
	 */
	@Override
	@Transactional
	public void deprecate(Long id) {
		OSMetadata parent = requireActiveImage(id);
		parent.deprecate();
		recomputeIsos(parent);
		log.info("[lifecycle.deprecate] resource=OS_IMAGE#{} outcome=deprecated", id);
	}

	/**
	 * R4-1 — OS undeprecate + 자식 ISO effective 재계산.
	 * <p>own 불변 → 운영자가 직접 deprecate 한 ISO(own_deprecated=true)는 보존, 부모 추종 ISO 만 활성 복원.
	 * 기존 "deprecated ISO 전량 환원" 결함 해소.</p>
	 */
	@Override
	@Transactional
	public void undeprecate(Long id) {
		OSMetadata parent = requireActiveImage(id);
		parent.undeprecate();
		recomputeIsos(parent);
		log.info("[lifecycle.undeprecate] resource=OS_IMAGE#{} outcome=undeprecated", id);
	}

	// ==== purge ========================================================

	/**
	 * S5-2-2 — OS 메타데이터 typed-name 검증 후 영구 삭제.
	 * <p>합성식은 {@link OSMetadata#displayName()} 에 응집 — service 가 entity 합성 책임을 갖지 않는다.</p>
	 */
	@Override
	@Transactional
	public void purgeWithTypedNameCheck(Long id, String typedName) {
		OSMetadata image = osMetadataRepository.findByIdAndIsDeletedTrue(id)
				.orElseThrow(() -> new IllegalOSMetadataStateException(
						"soft-deleted 상태가 아니어서 영구 삭제할 수 없습니다. id=" + id));
		// R7-2 — 이미 로딩한 엔티티로 검증(재조회·verifier 빈 미사용 → service→verifier 변 소멸).
		TypedNameGuard.verify(image, typedName);
		purge(id);
	}

	/**
	 * OSMetadata 영구 삭제. soft-deleted 상태에서만 호출 가능.
	 * <p>자식 ISO 들의 sidecar 파일 정리 후 row 삭제. cascade 가 ISO 행을 자동 제거하지만, sidecar 파일은
	 * 어플리케이션이 직접 정리해야 한다 — {@link IsoLifecycleService#cleanupArtifacts(ISO)} 위임 (R1-4-1).</p>
	 */
	@Override
	@Transactional
	public void purge(Long id) {
		OSMetadata image = osMetadataRepository.findByIdAndIsDeletedTrue(id)
				.orElseThrow(() -> new IllegalOSMetadataStateException(
						"soft-deleted 상태가 아니어서 영구 삭제할 수 없습니다. id=" + id));
		for (ISO iso : image.getIsos()) {
			isoLifecycleService.cleanupArtifacts(iso);   // R1-4-1 — IsoLifecycleService 로 위임
		}
		osMetadataRepository.delete(image);
		log.info("[lifecycle.purge] resource=OS_IMAGE#{} outcome=purged", id);
	}

	// ==== helper =======================================================

	/**
	 * D-1 안 A — over-abstraction 회피 차원에서 OSMetadataService 의 동명 helper 사본.
	 */
	private OSMetadata requireActiveImage(Long id) {
		return osMetadataRepository.findByIdAndIsDeletedFalse(id)
				.orElseThrow(() -> new OSMetadataNotFoundException(id));
	}
}
