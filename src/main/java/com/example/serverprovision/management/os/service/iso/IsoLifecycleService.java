package com.example.serverprovision.management.os.service.iso;

import com.example.serverprovision.global.exception.ChildLifecycleBlockedByParentException;
import com.example.serverprovision.global.lifecycle.DeleteAction;
import com.example.serverprovision.global.lifecycle.LifecycleService;
import com.example.serverprovision.global.lifecycle.LifecycleStage;
import com.example.serverprovision.global.lifecycle.SoftDeleteIntentService;
import com.example.serverprovision.global.marker.MarkerLayout;
import com.example.serverprovision.global.marker.ResourceType;
import com.example.serverprovision.global.marker.service.ProvisionMarkerService;
import com.example.serverprovision.global.trash.TrashLifecycleService;
import com.example.serverprovision.global.trash.service.TypedNameGuard;
import com.example.serverprovision.management.common.dto.response.RestoreResponse;
import com.example.serverprovision.management.os.entity.ISO;
import com.example.serverprovision.management.os.entity.OSMetadata;
import com.example.serverprovision.management.os.exception.ISONotFoundException;
import com.example.serverprovision.management.os.exception.IllegalOSMetadataStateException;
import com.example.serverprovision.management.os.exception.OSMetadataNotFoundException;
import com.example.serverprovision.management.os.repository.ISORepository;
import com.example.serverprovision.management.os.repository.OSMetadataRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

/**
 * R1-4-1 — ISO 의 lifecycle 책임 단일 service. {@link LifecycleService} interface 의 두 번째 구현체.
 *
 * <p>ISO 는 leaf 자원 (자식 없음) 이므로 interface 의 default {@code restore(Long)} 가 자연 활용된다.
 * {@code restore(Long, boolean cascade)} 의 cascade 인자는 받지만 자식 0 복구 → {@code RestoreResponse.none()}
 * 반환 (silent 무시가 아닌 자연 무모순).</p>
 *
 * <p>부모-자식 가드 — toggleEnabled / restore / undeprecate 는 부모 OSMetadata 의 lifecycle 상태
 * (deleted / disabled / deprecated) 에 따라 자식 행위를 차단한다 ({@link ChildLifecycleBlockedByParentException}).
 * 가드 책임은 본 service 안에서 entity 의 부모 reference 로 자체 lookup.</p>
 *
 * <p>URL forging 가드 — controller 가 endpoint 진입 시 {@link #assertBelongsToOs(Long, Long)} 를
 * lifecycle 메서드 호출 전에 호출. URL 의 osMetadataId 와 entity 의 부모 id 일치 검증.</p>
 *
 * <p>의존 그래프 — parent {@code OSMetadataLifecycleService} 가 cascade restore / sidecar 정리 시
 * 본 service 의 메서드를 위임 호출 (단방향, cycle 없음).</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class IsoLifecycleService implements LifecycleService {

	private final ISORepository isoRepository;
	private final OSMetadataRepository osMetadataRepository;
	private final TrashLifecycleService trashLifecycleService;
	private final SoftDeleteIntentService softDeleteIntentService;
	private final ProvisionMarkerService markerService;

	// ==== URL forging 가드 (controller 가 lifecycle 호출 직전에 호출) =========

	/**
	 * URL 의 {@code osMetadataId} path variable 과 ISO entity 의 부모 id 일치 검증.
	 * 사용자가 URL 을 forging 해 다른 OS 의 ISO 에 접근 시도하면 차단.
	 *
	 * @throws ISONotFoundException entity 부재 또는 부모 mismatch
	 */
	public void assertBelongsToOs(Long isoId, Long expectedParentId) {
		ISO iso = isoRepository.findById(isoId)
				.orElseThrow(() -> new ISONotFoundException(expectedParentId, isoId));
		if (!iso.getOsMetadata().getId().equals(expectedParentId)) {
			throw new ISONotFoundException(expectedParentId, isoId);
		}
	}

	// ==== enabled 토글 =================================================

	/**
	 * S5-2-3-1 — 자식 ISO 단독 toggle. 부모 가드 : 부모가 disabled/deprecated 면 enable 거절.
	 * disable 은 자유.
	 */
	@Override
	@Transactional
	public void toggleEnabled(Long isoId) {
		ISO iso = requireLiveIso(isoId);
		OSMetadata parent = loadParent(iso);
		boolean nextEnabled = !iso.isEnabled();
		if (nextEnabled) {
			String parentState = parent.childEnableBlockReason();   // R2-2 — SSOT (DELETED comprehensive)
			if (parentState != null) {
				throw new ChildLifecycleBlockedByParentException(
						ResourceType.OS_IMAGE,
						parent.getId(), parentState,
						ResourceType.OS_ISO,
						isoId, "enable",
						parent.displayName()
				);
			}
		}
		iso.toggleEnabled();
		log.info("[lifecycle.toggle] resource=OS_ISO#{} enabled={} outcome=toggled", isoId, nextEnabled);
	}

	// ==== soft delete / restore ========================================

	/**
	 * MK3 — soft-delete ISO. 도메인 가드 후 공통 trash 흐름 위임. MK3-2 사전조건 검사 포함.
	 */
	@Override
	@Transactional
	public void softDelete(Long isoId) {
		ISO iso = requireLiveIso(isoId);
		softDeleteIntentService.checkPrecondition(iso);
		trashLifecycleService.softDeleteToTrash(iso);
	}

	/**
	 * MK3-2 (DCM3-2.3 ~ 2.5) — softDelete reject modal 의 두 번째 호출 진입점.
	 * controller 가 token 검증 후 호출. action 에 따라 saga 또는 forced clear 분기.
	 */
	@Transactional
	public void softDeleteWithIntent(Long isoId, DeleteAction action) {
		switch (action) {
			case CORRECT_PATH_THEN_DELETE -> softDeleteIntentService.reconcileThenDelete(
					ResourceType.OS_ISO, isoId,
					() -> {
						ISO refreshed = requireLiveIso(isoId);
						trashLifecycleService.softDeleteToTrash(refreshed);
					}
			);
			case FORCED_CLEAR -> softDeleteIntentService.forcedClear(ResourceType.OS_ISO, isoId);
		}
	}

	/**
	 * HF-1 (CP5) — {@code restore(Long)} override. {@link LifecycleService} 의 default 위임은 default 메서드
	 * 내부의 {@code restore(id, false)} 가 <b>self-invocation</b> 이라 Spring AOP {@code @Transactional} 프록시를
	 * 우회한다 → 트랜잭션 없이 실행되어 {@code entity.restore()/clearTrashed()} 변경이 flush 되지 않던 버그
	 * (self-heal 로그는 찍히나 DB 미반영, 정상 복원 시에도 FS 만 복구되고 DB 는 stale 로 남는 partial-restore 의 진짜 뿌리).
	 *
	 * <p>본 override 가 프록시 진입점이 되어 트랜잭션을 시작하고 그 안에서 2-arg 를 호출한다
	 * ({@code BoardModelService.restore(Long)} 와 동형). controller / scanner 의 단일 인자 restore 호출이
	 * 본 메서드를 거쳐 트랜잭션 경계 안에서 실행되도록 보장.</p>
	 */
	@Override
	@Transactional
	public void restore(Long isoId) {
		restore(isoId, false);
	}

	/**
	 * MK3 — restore ISO. S5-2-3-1 부모 가드 : 부모 OS 가 deleted 면 자식 단독 restore 거절.
	 *
	 * <p>ISO 는 leaf 자원 — cascade 인자 받지만 자식 0 복구라 항상 {@link RestoreResponse#none()} 반환.</p>
	 */
	@Override
	@Transactional
	public RestoreResponse restore(Long isoId, boolean cascade) {
		ISO iso = isoRepository.findById(isoId)
				.orElseThrow(() -> new ISONotFoundException(null, isoId));
		OSMetadata parent = osMetadataRepository.findById(iso.getOsMetadata().getId())
				.orElseThrow(() -> new IllegalOSMetadataStateException(
						"OS 버전이 존재하지 않습니다. id=" + iso.getOsMetadata().getId()));
		if (parent.blocksChildRestore()) {   // R2-2 — SSOT
			throw new ChildLifecycleBlockedByParentException(
					ResourceType.OS_IMAGE,
					parent.getId(), "DELETED",
					ResourceType.OS_ISO,
					isoId, "restore",
					parent.displayName()
			);
		}
		if (!iso.isDeleted()) {
			throw new IllegalOSMetadataStateException("이미 활성 상태인 ISO 입니다. isoId=" + isoId);
		}
		trashLifecycleService.restoreFromTrash(
				iso, isoEntity -> Map.of(
						"osMetadataId", String.valueOf(isoEntity.getOsMetadata().getId()),
						"originalFilename", isoEntity.getResourcePath().getFileName().toString()
				)
		);
		return RestoreResponse.none();
	}

	// ==== deprecate / undeprecate ======================================

	/**
	 * S5-2-3-1 — 자식 ISO 단독 deprecate. 부모 가드 없음 (자식 단독 deprecate 는 자유).
	 */
	@Override
	@Transactional
	public void deprecate(Long isoId) {
		requireLiveIso(isoId).deprecate();
		log.info("[lifecycle.deprecate] resource=OS_ISO#{} outcome=deprecated", isoId);
	}

	/**
	 * S5-2-3-1 — 자식 ISO 단독 undeprecate. 부모 가드 : 부모 OS 가 deprecated 인 상태에서 자식 단독 undeprecate 거절.
	 */
	@Override
	@Transactional
	public void undeprecate(Long isoId) {
		ISO iso = requireLiveIso(isoId);
		OSMetadata parent = loadParent(iso);
		if (parent.blocksChildUndeprecate()) {   // R2-2 — SSOT (DEPRECATED 또는 DELETED)
			throw new ChildLifecycleBlockedByParentException(
					ResourceType.OS_IMAGE,
					parent.getId(), parent.isDeleted() ? "DELETED" : "DEPRECATED",
					ResourceType.OS_ISO,
					isoId, "undeprecate",
					parent.displayName()
			);
		}
		iso.undeprecate();
		log.info("[lifecycle.undeprecate] resource=OS_ISO#{} outcome=undeprecated", isoId);
	}

	// ==== purge ========================================================

	/**
	 * S5-2-2 — ISO typed-name 검증 후 영구 삭제.
	 */
	@Override
	@Transactional
	public void purgeWithTypedNameCheck(Long isoId, String typedName) {
		ISO iso = isoRepository.findById(isoId)
				.orElseThrow(() -> new ISONotFoundException(null, isoId));
		// R7-2 — 이미 로딩한 엔티티로 검증(재조회·verifier 빈 미사용 → service→verifier 변 소멸).
		TypedNameGuard.verify(iso, typedName);
		purge(isoId);
	}

	/**
	 * ISO 영구 삭제. soft-deleted 상태에서만 호출 가능. sidecar + 본체 파일 모두 정리.
	 */
	@Override
	@Transactional
	public void purge(Long isoId) {
		ISO iso = isoRepository.findById(isoId)
				.orElseThrow(() -> new ISONotFoundException(null, isoId));
		if (iso.currentStage() != LifecycleStage.SOFT_DELETED) {
			throw new IllegalOSMetadataStateException(
					"soft-deleted 상태가 아니어서 영구 삭제할 수 없습니다. isoId=" + isoId);
		}
		cleanupArtifacts(iso);
		isoRepository.delete(iso);
		log.info("[lifecycle.purge] resource=OS_ISO#{} outcome=purged", isoId);
	}

	// ==== 외부 위임용 public helper ====================================

	/**
	 * ISO 의 디스크 부산물 (본체 파일 + sidecar) 정리. {@link #purge(Long)} 내부 + 외부 위임용.
	 *
	 * <p>위임 호출처 :
	 * <ul>
	 *   <li>{@code OSMetadataLifecycleService.purge} (R1-3) — 부모 purge 시 자식 ISO sidecar 정리</li>
	 *   <li>{@code IsoRegistrationService.purgeForNudge} (R1-4-2) — nudge replace 의 충돌 후보 정리</li>
	 * </ul>
	 * <p>R1-6 — {@code OSMetadataNudgeService.purgeForNudge} (부모 OSMetadata nudge replace) 는 본 helper 를
	 * 호출하지 않는다 (부모 자체는 디스크 파일 없는 메타 자원 + 자식 ISO row 는 DB FK cascade 처리).</p>
	 * 단방향 cycle 없음 — IsoRegistrationService → IsoLifecycleService (본 helper) → 외부 의존 없음.</p>
	 */
	public void cleanupArtifacts(ISO iso) {
		Path body = Path.of(iso.getIsoPath());
		deleteQuietly(markerService.resolveMarkerFile(body, MarkerLayout.SIDECAR));
		deleteQuietly(body);
	}

	// ==== private helpers ==============================================

	private ISO requireLiveIso(Long isoId) {
		ISO iso = isoRepository.findById(isoId)
				.orElseThrow(() -> new ISONotFoundException(null, isoId));
		if (iso.isDeleted()) {
			throw new IllegalOSMetadataStateException("삭제된 ISO 에는 수행할 수 없는 작업입니다. isoId=" + isoId);
		}
		return iso;
	}

	private OSMetadata loadParent(ISO iso) {
		Long parentId = iso.getOsMetadata().getId();
		return osMetadataRepository.findByIdAndIsDeletedFalse(parentId)
				.orElseThrow(() -> new OSMetadataNotFoundException(parentId));
	}

	private static void deleteQuietly(Path p) {
		try { Files.deleteIfExists(p); } catch (IOException ignored) { }
	}
}
