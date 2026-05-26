package com.example.serverprovision.management.os.service.metadata;

import com.example.serverprovision.global.exception.TypedNameMismatchException;
import com.example.serverprovision.global.marker.Markable;
import com.example.serverprovision.global.trash.TrashLifecycleService;
import com.example.serverprovision.management.common.dto.response.RestoreResponse;
import com.example.serverprovision.management.os.entity.ISO;
import com.example.serverprovision.management.os.entity.OSMetadata;
import com.example.serverprovision.management.os.exception.DuplicateOSMetadataException;
import com.example.serverprovision.management.os.exception.IllegalOSMetadataStateException;
import com.example.serverprovision.management.os.exception.OSMetadataNotFoundException;
import com.example.serverprovision.management.os.repository.OSMetadataRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * R1-3 — OSMetadata 의 lifecycle 책임을 응집한 단일 service.
 *
 * <p>enabled / deprecated / soft-deleted 필드 전이 + purge 종착점의 8 메서드를 한 곳에 모은다.
 * 자식 ISO 의 lifecycle cascade 가 필요한 경우 ({@link #restore(Long, boolean)} cascade,
 * {@link #purge(Long)} 의 sidecar 정리) 는 {@link OSMetadataService} 의 ISO 측 메서드
 * ({@code restoreISO} / {@code cleanupIsoArtifacts}) 에 위임한다 — 이 의존은 단방향
 * (Lifecycle → Service) 으로 cycle 없다.</p>
 *
 * <p>잔류 사유 (본 service 에 안 포함된 lifecycle 인접 항목) :
 * <ul>
 *   <li>{@code purgeIsoWithTypedNameCheck} — ISO 도메인의 lifecycle (R2-iso 영역)</li>
 *   <li>{@code purgeOSMetadataForNudge} — nudge replace 흐름의 의존이 강해 OSMetadataService 잔류</li>
 * </ul></p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class OSMetadataLifecycleService implements com.example.serverprovision.global.lifecycle.LifecycleService {

	private final OSMetadataRepository osMetadataRepository;
	private final TrashLifecycleService trashLifecycleService;
	// R1-3 — 자식 ISO 의 restore / sidecar 정리는 OSMetadataService 의 ISO 메서드에 위임.
	// 단방향 의존 (OSMetadataService 가 본 service 를 의존하지 않음) 으로 cycle 회피.
	private final OSMetadataService osMetadataService;

	// ==== enabled 토글 =================================================

	/**
	 * S5-2-3-1 — OS 활성/비활성 토글 + 자식 ISO 강제 cascade.
	 * 자식이 deprecated / deleted 면 skip. 부모 활성 상태와 자식 활성 상태가 다른 자식만 동기화.
	 */
	@Override
	@Transactional
	public void toggleEnabled(Long id) {
		OSMetadata parent = requireActiveImage(id);
		parent.toggleEnabled();
		boolean target = parent.isEnabled();
		parent.getIsos().stream()
				.filter(iso -> !iso.isDeleted() && !iso.isDeprecated())
				.filter(iso -> iso.isEnabled() != target)
				.forEach(ISO::toggleEnabled);
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
	 * {@link OSMetadataService#restoreISO(Long, Long)} 위임 — trashLifecycleService.restoreFromTrash 흐름 재사용.</p>
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
				osMetadataService.restoreISO(id, iso.getId());
				restored++;
			}
		}
		log.info("[restore] OSMetadata id={} cascade=true → 하위 ISO {}건 복구", id, restored);
		return new RestoreResponse(restored);
	}

	// ==== deprecate / undeprecate ======================================

	/**
	 * S5-2-3-1 — OS deprecate + 자식 ISO 강제 cascade.
	 * 자식이 이미 deprecated 거나 deleted 면 skip (entity 가드 회피).
	 */
	@Override
	@Transactional
	public void deprecate(Long id) {
		OSMetadata parent = requireActiveImage(id);
		parent.deprecate();
		parent.getIsos().stream()
				.filter(iso -> !iso.isDeleted() && !iso.isDeprecated())
				.forEach(ISO::deprecate);
	}

	/**
	 * S5-2-3-1 — OS undeprecate + 자식 ISO 강제 cascade.
	 * 자식이 deprecated 인 것만 undeprecate. active / deleted 자식은 skip.
	 */
	@Override
	@Transactional
	public void undeprecate(Long id) {
		OSMetadata parent = requireActiveImage(id);
		parent.undeprecate();
		parent.getIsos().stream()
				.filter(iso -> !iso.isDeleted() && iso.isDeprecated())
				.forEach(ISO::undeprecate);
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
		verifyTypedNameOrThrow(image, typedName);
		purge(id);
	}

	/**
	 * OSMetadata 영구 삭제. soft-deleted 상태에서만 호출 가능.
	 * <p>자식 ISO 들의 sidecar 파일 정리 후 row 삭제. cascade 가 ISO 행을 자동 제거하지만, sidecar 파일은
	 * 어플리케이션이 직접 정리해야 한다 — {@link OSMetadataService#cleanupIsoArtifacts(ISO)} 위임.</p>
	 */
	@Override
	@Transactional
	public void purge(Long id) {
		OSMetadata image = osMetadataRepository.findByIdAndIsDeletedTrue(id)
				.orElseThrow(() -> new IllegalOSMetadataStateException(
						"soft-deleted 상태가 아니어서 영구 삭제할 수 없습니다. id=" + id));
		for (ISO iso : image.getIsos()) {
			osMetadataService.cleanupIsoArtifacts(iso);
		}
		osMetadataRepository.delete(image);
		log.info("[purgeImage] OS 메타데이터 영구 삭제 완료. id={}", id);
	}

	// ==== helper =======================================================

	/**
	 * typed-name 일치 검증 — entity.displayName() 이 곧 기대값.
	 */
	private static void verifyTypedNameOrThrow(Markable resource, String typedName) {
		String expected = resource.displayName();
		if (!expected.equals(typedName)) {
			throw new TypedNameMismatchException(expected, typedName);
		}
	}

	/**
	 * D-1 안 A — over-abstraction 회피 차원에서 OSMetadataService 의 동명 helper 사본.
	 */
	private OSMetadata requireActiveImage(Long id) {
		return osMetadataRepository.findByIdAndIsDeletedFalse(id)
				.orElseThrow(() -> new OSMetadataNotFoundException(id));
	}
}
