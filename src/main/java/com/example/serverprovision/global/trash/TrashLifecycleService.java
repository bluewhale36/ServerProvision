package com.example.serverprovision.global.trash;

import com.example.serverprovision.global.entity.LifecycleEntity;
import com.example.serverprovision.global.marker.Markable;
import com.example.serverprovision.global.marker.MarkerContent;
import com.example.serverprovision.global.marker.MarkerLayout;
import com.example.serverprovision.global.marker.service.ProvisionMarkerService;
import com.example.serverprovision.global.trash.exception.GhostRowRestoreNotAllowedException;
import com.example.serverprovision.management.common.exception.RestorePathOccupiedException;
import com.example.serverprovision.management.common.exception.RestoreTargetUnreachableException;
import com.example.serverprovision.management.common.exception.RestoreTrashLostException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Map;
import java.util.function.Function;

/**
 * MK3 — 4 자원 도메인 (ISO / BIOS / BMC / Subprogram) 이 공통으로 사용하는 trash 기반 lifecycle 흐름 헬퍼.
 *
 * <p>각 도메인 Service 의 softDelete / restore 메서드가 동일한 흐름 :
 * <ol>
 *   <li>마커 삭제</li>
 *   <li>자원 trash 이동</li>
 *   <li>{@code markTrashed} 기록</li>
 * </ol>
 * 으로 수렴하는 것을 단일 진입점으로 공통화. CLAUDE.md §중복된 코드와 가독성 (불가침) 정합.
 *
 * <p>도메인별 차이는 두 곳뿐 :
 * <ul>
 *   <li>마커 attributes 합성 (도메인 부속 메타) — caller 의 {@link Function} lambda 로 위임</li>
 *   <li>도메인-specific 가드 (이미 active 거절, 활성 자원 충돌 거절 등) — caller 가 본 메서드 호출 전 처리</li>
 * </ul>
 *
 * <p>본 헬퍼는 도메인을 모르므로 {@link Markable} 의 SPI 만 사용한다.
 * over-abstraction 우려 차단 — 새 도메인 자원이 추가되면 동일하게 본 헬퍼 호출 + lambda 만 작성하면 됨.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TrashLifecycleService {

	private final TrashService trashService;
	private final ProvisionMarkerService markerService;

	/**
	 * 공통 soft-delete 흐름.
	 * <ol>
	 *   <li>{@link LifecycleEntity#softDelete()} — DB lifecycle 전이</li>
	 *   <li>active 위치의 마커 파일 삭제 (sidecar 또는 in-tree)</li>
	 *   <li>자원 파일 존재 시 trash 로 mv + {@link LifecycleEntity#markTrashed(String)}. 부재 시 (A2) DB 만 정리</li>
	 *   <li>IN_TREE 마커가 함께 이동된 경우 trash 안에서 추가 삭제 (TRASH_MARKER_STALE 발생 방지)</li>
	 * </ol>
	 */
	public <T extends LifecycleEntity & Markable> void softDeleteToTrash(T entity) {
		Path resourcePath = entity.getResourcePath();
		MarkerLayout layout = entity.getMarkerLayout();

		entity.softDelete();

		Path activeMarker = markerService.resolveMarkerFile(resourcePath, layout);
		try {
			Files.deleteIfExists(activeMarker);
		} catch (IOException e) {
			log.warn(
					"[trash.markerDelete.fail] resource={}#{} path={} outcome=marker-stale-followup msg={}",
					entity.getResourceType(), entity.getResourceId(), activeMarker, e.getMessage()
			);
		}

		if (Files.exists(resourcePath)) {
			Path trashed = trashService.moveToTrash(resourcePath, entity.getResourceType(), entity.getResourceId());
			try {
				// IN_TREE 마커가 트리와 함께 trash 로 따라간 경우 정리.
				Path movedMarker = markerService.resolveMarkerFile(trashed, layout);
				try {
					Files.deleteIfExists(movedMarker);
				} catch (IOException e) {
					log.warn(
							"[trash.markerDelete.fail] resource={}#{} path={} outcome=in-trash-marker-stale msg={}",
							entity.getResourceType(), entity.getResourceId(), movedMarker, e.getMessage()
					);
				}
				entity.markTrashed(trashed.toString());
				log.info(
						"[lifecycle.softDelete] resource={}#{} outcome=trashed",
						entity.getResourceType(), entity.getResourceId()
				);
			} catch (RuntimeException e) {
				// HF-1 (요구 ③) — moveToTrash 와 markTrashed 사이 예외 시 대칭 역보상.
				// 자원을 원위치로 되돌려 @Transactional 롤백 (softDelete 취소) 과 FS 를 동방향으로 맞춘다.
				trashService.moveBackReverse(trashed, resourcePath);
				throw e;
			}
		} else {
			log.warn(
					"[trash.resourceMissing] resource={}#{} path={} outcome=db-only",
					entity.getResourceType(), entity.getResourceId(), resourcePath
			);
		}
	}

	/**
	 * 공통 restore 흐름 (HF-1 재구성).
	 * <ol>
	 *   <li>원래 경로 부모 접근 가능 검증 — 부재 시 {@link RestoreTargetUnreachableException}</li>
	 *   <li>① 멱등 사전게이트 {@link #healOrThrowBeforeRestore} — 원위치·휴지통 FS 2비트의 4상태 분류
	 *       (self-heal / 진짜 분실 → {@link RestoreTrashLostException} / 점유 → {@link RestorePathOccupiedException} / 정상)</li>
	 *   <li>② FS-first + 역보상 — trash → 원래 경로 mv 후 마커 합성·서명·재발급 + DB 전이. moveBack 이후
	 *       어느 단계든 실패하면 {@link #compensateRestore} 로 자원을 trash 로 되돌려 {@code @Transactional}
	 *       롤백과 FS 를 동방향으로 맞춘다.</li>
	 *   <li>{@link LifecycleEntity#restore()} + {@link LifecycleEntity#clearTrashed()}</li>
	 * </ol>
	 * hash 충돌은 caller 가 S5-2-3 (cascade restore) 와 함께 통합 검증.
	 *
	 * <p>{@code trashed_path=null} 인 자원 (A2 — soft-delete 시 자원 부재였던 경우) 은 단순 lifecycle 복원.</p>
	 */
	public <T extends LifecycleEntity & Markable> void restoreFromTrash(
			T entity, Function<T, Map<String, String>> attributeBuilder) {

		String trashedPathStr = entity.getTrashedPath();
		if (trashedPathStr == null) {
			// MK3-1 — trashed_path=null 분기 분리.
			//   (a) FS 자원이 DB.path 에 살아있음 → 단순 lifecycle 복원 (외부 mv 로 자원만 돌아온 케이스 등)
			//   (b) FS 자원도 부재 → ghost row → 명시적 거절 (휴지통 정리 / reconciliation drift apply 안내)
			if (!Files.exists(entity.getResourcePath())) {
				throw new GhostRowRestoreNotAllowedException(
						entity.getResourceType().name() + "#" + entity.getResourceId());
			}
			entity.restore();
			entity.clearTrashed();
			ensureMarkerPresent(entity, attributeBuilder);   // S6-2-2 — 아래 주석 참조
			log.info(
					"[lifecycle.restore] resource={}#{} outcome=restored",
					entity.getResourceType(), entity.getResourceId()
			);
			return;
		}

		Path trashedPath = Path.of(trashedPathStr);
		Path originalPath = entity.getResourcePath();

		// 원래 경로 부모 접근성 (기존 검사 유지) — 게이트의 FS 비트 판정 전에 둔다.
		Path parent = originalPath.getParent();
		if (parent == null || !Files.isDirectory(parent)) {
			throw new RestoreTargetUnreachableException(parent == null ? "(none)" : parent.toString());
		}

		// ① 멱등 사전게이트 — 원위치(O)·휴지통(T) FS 2비트의 4상태 분류.
		//    self-heal (원O·trashX) 시 DB 만 정합 후 true 반환 → caller 추가 작업 없이 종료.
		if (healOrThrowBeforeRestore(entity, originalPath, trashedPath)) {
			ensureMarkerPresent(entity, attributeBuilder);   // S6-2-2 — 아래 주석 참조
			return;
		}

		// ② FS-first + 즉시 역보상 — (원X·trashO) 정상 복원 경로.
		//    moveBack 으로 자원을 원위치에 올린 뒤 마커 재발급. 마커 재발급이 실패하면
		//    작성된 마커를 지우고 자원을 trash 로 되돌려 @Transactional 롤백과 FS 를 동방향으로 맞춘다.
		trashService.moveBack(trashedPath, originalPath);

		// moveBack(비가역 FS) 직후부터 모든 후속 작업(마커 attributes 합성 · 서명 · write · DB 전이)을
		// 단일 보상 윈도우로 감싼다. attributeBuilder.apply / computeSignature 등 write 이전 단계의
		// RuntimeException(caller lambda NPE, MarkerWriteFailedException 등)도 자원을 trash 로 되돌려야
		// @Transactional 롤백과 FS 가 동방향(둘 다 trash)이 되어 dangling stale row 를 0 으로 만든다.
		// (HF-1 적대적 검증 — try 윈도우가 moveBack 직후 두 statement 를 안 덮어 역방향 비대칭이 새던 결함 차단)
		try {
			MarkerContent content = new MarkerContent(
					entity.getResourceType().name(),
					entity.getResourceId(),
					attributeBuilder.apply(entity),
					Instant.now(),
					entity.getManifestHash(),
					null
			);
			String signature = markerService.computeSignature(content);
			markerService.write(originalPath, entity.getMarkerLayout(), content.withSignature(signature));
			entity.reissueMarker(entity.getManifestHash(), signature);
			entity.restore();
			entity.clearTrashed();
			log.info(
					"[lifecycle.restore] resource={}#{} outcome=restored",
					entity.getResourceType(), entity.getResourceId()
			);
		} catch (RuntimeException e) {
			compensateRestore(entity, originalPath, trashedPath);
			throw e;   // @Transactional 롤백 유발 — FS(역보상) 와 동방향 (둘 다 trash)
		}
	}

	/**
	 * S6-2-2 — self-heal / trashed_path=null 단순 복원 분기의 마커 보강. 두 분기는 파일이 이미
	 * 원위치에 있어 정상 복원의 마커 write 를 지나치는데, soft-delete 가 원위치 마커를 지우므로
	 * 복원 결과가 "마커 없는 활성 자원"이 되어 다음 점검에서 곧장 MISSING(수동 조치 전용)으로
	 * 떨어졌다 — SOFTDEL_ESCAPE_TO_ORIGINAL 자동 해결이 이 경로를 상시화하면서 드러난 결함
	 * (S6-2-2 적대적 검증). 원위치에 마커가 없을 때만 합성·서명·기록한다 — 잔존 마커는 self-heal
	 * 게이트의 manifestHash 검증을 이미 통과한 상태라 재발급이 불필요.
	 */
	private <T extends LifecycleEntity & Markable> void ensureMarkerPresent(
			T entity, Function<T, Map<String, String>> attributeBuilder) {
		Path markerFile = markerService.resolveMarkerFile(entity.getResourcePath(), entity.getMarkerLayout());
		if (markerFile != null && Files.exists(markerFile)) {
			return;
		}
		MarkerContent content = new MarkerContent(
				entity.getResourceType().name(),
				entity.getResourceId(),
				attributeBuilder.apply(entity),
				Instant.now(),
				entity.getManifestHash(),
				null
		);
		String signature = markerService.computeSignature(content);
		markerService.write(entity.getResourcePath(), entity.getMarkerLayout(), content.withSignature(signature));
		entity.reissueMarker(entity.getManifestHash(), signature);
	}

	/**
	 * HF-1 (요구 ②) — restore FS-first 역보상. moveBack 이후 마커 합성·서명·write·재발급·DB 전이 중
	 * 어느 단계든 실패 시 (1) 작성됐을 수 있는 마커 제거 (2) 원위치 자원을 trash 로 되돌림.
	 * 원예외는 caller 가 재던져 @Transactional 롤백을 유발한다.
	 *
	 * <p>마커 삭제는 fail-safe (deleteIfExists 의 IOException 흡수) — 핵심 보상은 자원의 trash 복귀이므로
	 * 마커 정리가 실패해도 역 mv 는 반드시 시도한다. 역 mv 자체가 실패하면 {@code moveBackReverse} 내부의
	 * {@code TrashMoveFailedException} 이 전파되어 운영자 점검을 유도한다.</p>
	 */
	private <T extends LifecycleEntity & Markable> void compensateRestore(
			T entity, Path originalPath, Path trashedPath) {
		Path writtenMarker = markerService.resolveMarkerFile(originalPath, entity.getMarkerLayout());
		try {
			Files.deleteIfExists(writtenMarker);
		} catch (IOException io) {
			log.warn(
					"[trash.compensate.markerDelete.fail] resource={}#{} path={} outcome=continue msg={}",
					entity.getResourceType(), entity.getResourceId(), writtenMarker, io.getMessage()
			);
		}
		trashService.moveBackReverse(originalPath, trashedPath);
	}

	/**
	 * HF-1 (요구 ①) — restore 멱등 사전게이트. {@code trashed_path != null} 분기 진입 직후 호출되어
	 * 원위치(O)·휴지통(T) 두 FS 비트의 4상태를 단일 분기로 분류한다.
	 * <ul>
	 *   <li>(원O·trashX) partial-restore 잔여 → manifestHash 검증 후 DB 만 self-heal, {@code return true}</li>
	 *   <li>(원X·trashX) 진짜 분실 → {@link RestoreTrashLostException} (409)</li>
	 *   <li>(원O·trashO) 경로 점유 → {@link RestorePathOccupiedException} (409)</li>
	 *   <li>(원X·trashO) 정상 복원 → {@code return false} (caller 가 moveBack 진행)</li>
	 * </ul>
	 * 기존 trash-lost / path-occupied 개별 검사를 4상태 단일 게이트로 <b>대체</b>한다 (분기 확장 아님).
	 *
	 * @return true = caller 종료 (self-heal 완료), false = 정상 복원 흐름 진행
	 */
	private <T extends LifecycleEntity & Markable> boolean healOrThrowBeforeRestore(
			T entity, Path originalPath, Path trashedPath) {
		boolean originalExists = Files.exists(originalPath);
		boolean trashedExists = Files.exists(trashedPath);

		if (originalExists && !trashedExists) {
			// (원O·trashX) partial-restore 잔여 — moveBack/write 는 이미 끝났고 DB 만 stale.
			// 동명파일 오인 방어 후 DB 만 정합 (self-heal, A-2 : 조용히 2xx, 토스트 미노출).
			assertSameResourceOrThrow(entity, originalPath);
			entity.restore();
			entity.clearTrashed();
			log.info(
					"[lifecycle.restore.selfHeal] resource={}#{} path={} outcome=self-healed",
					entity.getResourceType(), entity.getResourceId(), originalPath
			);
			return true;
		}
		if (!originalExists && !trashedExists) {
			// (원X·trashX) 자원이 양쪽에서 완전히 사라짐 — 진짜 분실.
			throw new RestoreTrashLostException(trashedPath.toString());
		}
		if (originalExists) {
			// (원O·trashO) 원위치에 동명 파일 점유 + trash 도 살아있음 — 기존 점유 충돌.
			throw new RestorePathOccupiedException(originalPath.toString());
		}
		// (원X·trashO) 정상 복원 — caller 가 moveBack 진행.
		return false;
	}

	/**
	 * HF-1 (요구 ①) — (원O·trashX) self-heal 직전 동명파일 오인 방어. 원위치에 있는 파일이 실제로
	 * 해당 자원인지 {@code manifestHash} 로 선택 검증하여, 외부 무관 파일을 자원으로 오인해 DB 만
	 * 정합시키는 사고를 차단한다.
	 *
	 * <p><b>선택 검증</b> — 원위치에 마커가 함께 남아있을 때만 검증한다. 마커가 없으면(partial-restore 가
	 * 마커 write 전에 끊긴 정상 잔여 포함) 자원 본체 존재만으로 self-heal 을 허용한다.</p>
	 */
	private <T extends LifecycleEntity & Markable> void assertSameResourceOrThrow(T entity, Path originalPath) {
		// 선택 검증 — 원위치에 마커가 함께 남아있는 경우에만 manifestHash 일치를 확인한다.
		// 마커 부재 시(partial-restore 가 마커 write 전에 끊긴 정상 케이스 포함) 자원 본체 존재만으로
		// self-heal 을 허용한다. 마커가 있는데 자원의 manifestHash 와 어긋나면 원위치 파일은
		// 다른 자원이 점유한 것으로 보고 점유 충돌(409)로 거절한다.
		Path markerFile = markerService.resolveMarkerFile(originalPath, entity.getMarkerLayout());
		if (!Files.exists(markerFile)) {
			return;
		}
		MarkerContent marker = markerService.read(originalPath, entity.getMarkerLayout());
		String entityHash = entity.getManifestHash();
		if (entityHash != null && marker.manifestHash() != null
				&& !entityHash.equals(marker.manifestHash())) {
			log.warn(
					"[trash] self-heal 거부 — 원위치 마커 manifestHash 불일치 (동명파일 오인 방어). "
							+ "type={} id={} path={} entityHash={} markerHash={}",
					entity.getResourceType(), entity.getResourceId(), originalPath,
					entityHash, marker.manifestHash()
			);
			throw new RestorePathOccupiedException(originalPath.toString());
		}
	}
}
