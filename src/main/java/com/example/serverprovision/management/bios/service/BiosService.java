package com.example.serverprovision.management.bios.service;

import com.example.serverprovision.global.exception.TypedNameMismatchException;
import com.example.serverprovision.global.lifecycle.LifecycleStage;
import com.example.serverprovision.global.marker.MarkerContent;
import com.example.serverprovision.global.marker.MarkerLayout;
import com.example.serverprovision.global.marker.exception.MarkerMissingException;
import com.example.serverprovision.global.marker.service.ProvisionMarkerService;
import com.example.serverprovision.global.security.PathPolicyService;
import com.example.serverprovision.management.bios.dto.request.BiosCreateRequest;
import com.example.serverprovision.management.bios.dto.request.BiosRegisterExistingRequest;
import com.example.serverprovision.management.bios.dto.request.BiosUpdateRequest;
import com.example.serverprovision.management.bios.dto.response.BiosResponse;
import com.example.serverprovision.management.bios.dto.response.BoardWithBiosListResponse;
import com.example.serverprovision.management.bios.entity.BoardBIOS;
import com.example.serverprovision.management.bios.enums.BiosUploadMode;
import com.example.serverprovision.management.bios.exception.BiosNotFoundException;
import com.example.serverprovision.management.bios.exception.BiosNudgeRequiredException;
import com.example.serverprovision.management.bios.exception.DuplicateBiosVersionException;
import com.example.serverprovision.management.bios.exception.IllegalBiosStateException;
import com.example.serverprovision.management.bios.repository.BiosRepository;
import com.example.serverprovision.management.bios.service.BundleManifestService.ManifestSummary;
import com.example.serverprovision.global.marker.IntegrityStatus;
import com.example.serverprovision.management.board.entity.BoardModel;
import com.example.serverprovision.management.board.exception.BoardModelNotFoundException;
import com.example.serverprovision.management.board.repository.BoardModelRepository;
import com.example.serverprovision.management.common.dto.response.IntegrityStatusResponse;
import com.example.serverprovision.management.common.filesystem.service.BundleTreeCleanupService;
import com.example.serverprovision.management.common.filesystem.service.TargetDirectoryPolicyService;
import com.example.serverprovision.management.common.nudge.NudgeRegistry;
import com.example.serverprovision.management.common.nudge.NudgeResourceType;
import com.example.serverprovision.management.common.nudge.NudgeSession;
import com.example.serverprovision.management.common.nudge.dto.NudgeConflictEntry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Path;
import java.time.Instant;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * A3 v3 BIOS 번들 도메인 로직 총괄. 번들 저장 · marker 관리 · 무결성 검증을 조합한다.
 *
 * <p>전반적 흐름 :</p>
 * <ul>
 *   <li>등록 : 검증 → 기존 soft-deleted 동일 (board, version) 정리 → 트리 전개 → manifest 계산 →
 *       엔티티 선 저장 → marker signature 계산(biosId 포함) → entity.reissueMarker → marker 파일 기록</li>
 *   <li>조회 : Miller 전체 뷰 (N+1 방지 배치 조회) + 마지막 검증 스냅샷(lastIntegrityStatus) 을 내려감</li>
 *   <li>검증 : {@code .provision.json} 읽어 서명 + manifestHash 재계산 비교</li>
 *   <li>재발급 : 현재 트리 내용으로 manifest 재계산 → 엔티티 · marker 갱신 (관리자 명시 액션 전제)</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BiosService {

	private final BiosRepository biosRepository;
	private final BoardModelRepository boardModelRepository;
	private final BundleExtractionService bundleExtractionService;
	private final BundleEntrypointDetector bundleEntrypointDetector;
	private final BundleManifestService bundleManifestService;
	private final ProvisionMarkerService provisionMarkerService;
	private final BiosMarkerWriter biosMarkerWriter;
	private final TargetDirectoryPolicyService targetDirectoryPolicyService;
	private final BundleTreeCleanupService bundleTreeCleanupService;
	private final PathPolicyService pathPolicyService;
	private final NudgeRegistry nudgeRegistry;
	private final com.example.serverprovision.global.trash.TrashLifecycleService trashLifecycleService;
	private final com.example.serverprovision.global.lifecycle.SoftDeleteIntentService softDeleteIntentService;

	// ==== 조회 ========================================================

	public List<BoardWithBiosListResponse> findAllGrouped(boolean includeDeleted) {
		List<BoardModel> boards = includeDeleted
				? boardModelRepository.findAllByOrderByVendorAscCreatedAtDesc()
				: boardModelRepository.findAllByIsDeletedFalseOrderByVendorAscCreatedAtDesc();
		if (boards.isEmpty()) return List.of();

		List<Long> boardIds = boards.stream().map(BoardModel::getId).toList();
		List<BoardBIOS> allBios = biosRepository.findAllByBoardModel_IdIn(boardIds);
		Map<Long, List<BoardBIOS>> byBoard = allBios.stream()
				.filter(b -> includeDeleted || !b.isDeleted())
				.collect(Collectors.groupingBy(b -> b.getBoardModel().getId(), HashMap::new, Collectors.toList()));

		return boards.stream()
				.map(board -> new BoardWithBiosListResponse(
						board.getId(),
						board.getVendor(),
						board.getVendor().getDisplayName(),
						board.getModelName(),
						board.isDeleted(),
						byBoard.getOrDefault(board.getId(), List.of()).stream()
								.sorted(Comparator.comparing(BoardBIOS::getVersion).reversed())
								.map(BiosService::toResponse)
								.toList()
				))
				.toList();
	}

	public BiosResponse findBios(Long boardId, Long biosId) {
		return toResponse(requireLiveBios(boardId, biosId));
	}

	public IntegrityStatusResponse findIntegrityStatus(Long boardId, Long biosId) {
		BoardBIOS bios = requireLiveBios(boardId, biosId);
		return IntegrityStatusResponse.of(
				bios.getId(),
				bios.getLastIntegrityStatus() != null ? bios.getLastIntegrityStatus() : IntegrityStatus.NOT_VERIFIED,
				bios.getLastVerifiedAt()
		);
	}

	// ==== 쓰기 연산 ====================================================

	@Transactional
	public Long addBios(
			Long boardId,
			BiosCreateRequest request,
			BiosUploadMode uploadMode,
			MultipartFile[] folderFiles,
			MultipartFile zipFile,
			MultipartFile singleFile
	) {
		BoardModel parent = requireActiveBoard(boardId);

		// 1) 활성 (board, version) 중복 검사
		if (biosRepository.existsByBoardModel_IdAndVersionAndIsDeletedFalse(boardId, request.version())) {
			throw new DuplicateBiosVersionException(boardId, request.version());
		}

		// S3 — allowlist 검증된 절대경로만 사용
		Path targetDir = pathPolicyService.assertWritablePath(request.targetDirectory());

		// MK2 — 자동 hard-delete 인라인 로직 제거. 동일 (board, version) SoftDeleted 자원이 있어도
		//       즉시 purge 하지 않는다. 대신 단계 B (해시 비교) 에서 nudge 세션을 발급해 사용자 결정에 위임.

		// 2) targetDirectory 상태 검증 — 상위 dir 존재 or allowCreateDirectory, 그리고 자기 자신이 비어있거나 부재
		targetDirectoryPolicyService.prepareForUpload(targetDir, request.allowCreateDirectory());

		try {
			// 3) 업로드 페이로드 전개 (extractionService 가 targetDirectory 생성 · 비어있음 검증을 내부 수행)
			switch (uploadMode) {
				case FOLDER -> bundleExtractionService.extractFolder(folderFiles, targetDir);
				case ZIP -> bundleExtractionService.extractZip(zipFile, targetDir);
				case SINGLE_FILE -> bundleExtractionService.extractSingleFile(singleFile, targetDir);
			}

			// 4) 진입점 탐지 (override 우선) — S5-11 v2 : Vendor 별 strategy 위임
			String entrypoint = bundleEntrypointDetector.detect(
					parent.getVendor(), targetDir, request.entrypointRelativePath());

			// 5) manifest 집계
			ManifestSummary manifest = bundleManifestService.compute(targetDir);

			// 6) MK2 단계 B — 해시 충돌 후보 (SoftDeleted / Deprecated) 탐지 시 nudge 세션 발급 + 409.
			//    임시 트리는 targetDir 에 그대로 남겨두고 사용자 결정 (proceed / replace / cancel) 대기.
			//    BiosNudgeService 가 confirm 시점에 정식 영속화 또는 cleanup.
			// MK3-1 — ghost (DB-only soft-deleted, FS 부재) 후보 사전 필터링.
			List<BoardBIOS> hashCandidates = biosRepository.findHashConflictCandidates(boardId, manifest.manifestHash())
					.stream()
					.filter(c -> !com.example.serverprovision.global.trash.GhostEvaluator.isGhost(c))
					.toList();
			if (!hashCandidates.isEmpty()) {
				NudgeSession session = nudgeRegistry.register(
						NudgeResourceType.BIOS,
						boardId,
						hashCandidates.stream().map(BoardBIOS::getId).toList(),
						new com.example.serverprovision.management.common.nudge.ContentNudgePayload(
								request.name(),
								request.version(),
								manifest.manifestHash(),
								targetDir.toString(),
								Map.of(
										"entrypointRelativePath", entrypoint,
										"fileCount", String.valueOf(manifest.fileCount()),
										"totalBytes", String.valueOf(manifest.totalBytes()),
										"description", request.description() != null ? request.description() : ""
								)
						)
				);
				List<NudgeConflictEntry> entries = hashCandidates.stream()
						.map(b -> new NudgeConflictEntry(
								b.getId(),
								LifecycleStage.of(b.isDeprecated(), b.isDeleted()),
								b.getManifestHash(),
								b.getName(),
								b.getVersion(),
								java.time.Instant.now()
						))
						.toList();
				log.info(
						"[addBios] nudge required : boardId={}, version={}, candidates={}",
						boardId, request.version(), hashCandidates.size()
				);
				throw new BiosNudgeRequiredException(session, entries);
			}

			// 7) 2-phase save : 엔티티 선 저장 (signature=null) → biosId 획득
			BoardBIOS saved = biosRepository.save(BoardBIOS.builder()
														  .boardModel(parent)
														  .name(request.name())
														  .version(request.version())
														  .treeRootPath(targetDir.toString())
														  .entrypointRelativePath(entrypoint)
														  .manifestHash(manifest.manifestHash())
														  .markerSignature(null)
														  .fileCount(manifest.fileCount())
														  .totalBytes(manifest.totalBytes())
														  .description(request.description())
														  .isEnabled(true)
														  .isDeleted(false)
														  .build());

			// 8) biosId 를 포함한 marker 생성 + 서명 + entity 갱신 + 파일 기록 (R4-2 — BiosMarkerWriter 위임)
			biosMarkerWriter.writeSignedMarker(
					saved, targetDir, boardId, request.version(), entrypoint, manifest.manifestHash());

			log.info(
					"[addBios] 등록 완료. biosId={}, boardId={}, version={}, fileCount={}, totalBytes={}",
					saved.getId(), boardId, request.version(), manifest.fileCount(), manifest.totalBytes()
			);
			return saved.getId();
		} catch (BiosNudgeRequiredException nudge) {
			// MK2 — nudge 분기는 임시 트리를 보존해야 confirm 시 ACTIVE 영속화가 가능하다.
			// cleanup 대상에서 제외하고 그대로 위로 throw — 컨트롤러는 advice 가 처리.
			throw nudge;
		} catch (RuntimeException e) {
			bundleTreeCleanupService.cleanupFailedUpload(targetDir, "purgeExistingTree", "addBios", e);
			throw e;
		}
	}

	/**
	 * 기존 디렉토리를 BIOS 번들 자원으로 등록. 업로드 없이 이미 있는 트리를 claim.
	 * <p>업로드 경로의 {@link #addBios} 와 동일한 검증/마커 발급/nudge 흐름을 공유하되,
	 * 추출 단계만 생략한다. 자동 hard-delete 없이 해시 충돌 시 nudge 위임 정책도 동일.</p>
	 */
	@Transactional
	public Long registerExisting(Long boardId, BiosRegisterExistingRequest request) {
		BoardModel parent = requireActiveBoard(boardId);

		if (biosRepository.existsByBoardModel_IdAndVersionAndIsDeletedFalse(boardId, request.version())) {
			throw new DuplicateBiosVersionException(boardId, request.version());
		}

		Path targetDir = pathPolicyService.assertWritablePath(request.targetDirectory());
		// 업로드와 달리 자동 hard-delete 없이 nudge 흐름에 위임 — 정책 일관성 유지.
		targetDirectoryPolicyService.prepareForExistingDirectoryRegistration(targetDir);

		// S5-11 v2 — Vendor 별 strategy 위임
		String entrypoint = bundleEntrypointDetector.detect(
				parent.getVendor(), targetDir, request.entrypointRelativePath());
		ManifestSummary manifest = bundleManifestService.compute(targetDir);

		// MK3-1 — ghost 후보 사전 필터링.
		List<BoardBIOS> hashCandidates = biosRepository.findHashConflictCandidates(boardId, manifest.manifestHash())
				.stream()
				.filter(c -> !com.example.serverprovision.global.trash.GhostEvaluator.isGhost(c))
				.toList();
		if (!hashCandidates.isEmpty()) {
			NudgeSession session = nudgeRegistry.register(
					NudgeResourceType.BIOS,
					boardId,
					hashCandidates.stream().map(BoardBIOS::getId).toList(),
					new com.example.serverprovision.management.common.nudge.ContentNudgePayload(
							request.name(),
							request.version(),
							manifest.manifestHash(),
							targetDir.toString(),
							Map.of(
									"entrypointRelativePath", entrypoint,
									"fileCount", String.valueOf(manifest.fileCount()),
									"totalBytes", String.valueOf(manifest.totalBytes()),
									"description", request.description() != null ? request.description() : ""
							)
					)
			);
			List<NudgeConflictEntry> entries = hashCandidates.stream()
					.map(b -> new NudgeConflictEntry(
							b.getId(),
							LifecycleStage.of(b.isDeprecated(), b.isDeleted()),
							b.getManifestHash(),
							b.getName(),
							b.getVersion(),
							Instant.now()
					))
					.toList();
			log.info(
					"[registerExistingBios] nudge required : boardId={}, version={}, candidates={}",
					boardId, request.version(), hashCandidates.size()
			);
			throw new BiosNudgeRequiredException(session, entries);
		}

		BoardBIOS saved = biosRepository.save(BoardBIOS.builder()
													  .boardModel(parent)
													  .name(request.name())
													  .version(request.version())
													  .treeRootPath(targetDir.toString())
													  .entrypointRelativePath(entrypoint)
													  .manifestHash(manifest.manifestHash())
													  .markerSignature(null)
													  .fileCount(manifest.fileCount())
													  .totalBytes(manifest.totalBytes())
													  .description(request.description())
													  .isEnabled(true)
													  .isDeleted(false)
													  .build());

		biosMarkerWriter.writeSignedMarker(
				saved, targetDir, boardId, request.version(), entrypoint, manifest.manifestHash());

		log.info(
				"[registerExistingBios] 등록 완료. biosId={}, boardId={}, version={}, fileCount={}, totalBytes={}",
				saved.getId(), boardId, request.version(), manifest.fileCount(), manifest.totalBytes()
		);
		return saved.getId();
	}

	@Transactional
	public void update(Long boardId, Long biosId, BiosUpdateRequest request) {
		BoardBIOS bios = requireLiveBios(boardId, biosId);
		if (!bios.getVersion().equals(request.version())
				&& biosRepository.existsByBoardModel_IdAndVersionAndIsDeletedFalse(boardId, request.version())) {
			throw new DuplicateBiosVersionException(boardId, request.version());
		}
		bios.update(request.name(), request.version(), request.description());
	}

	/**
	 * S5-2-3-1 — 자식 BIOS 단독 toggle.
	 * 부모 가드 : 부모 Board 가 비활성/Deprecated 면 enable 거절.
	 */
	@Transactional
	public void toggleEnabled(Long boardId, Long biosId) {
		BoardBIOS bios = requireLiveBios(boardId, biosId);
		boolean nextEnabled = !bios.isEnabled();
		if (nextEnabled) {
			com.example.serverprovision.management.board.entity.BoardModel parent =
					boardModelRepository.findById(boardId).orElseThrow();
			String parentState = parent.childEnableBlockReason();   // R2-2 — SSOT (DELETED comprehensive)
			if (parentState != null) {
				throw new com.example.serverprovision.global.exception.ChildLifecycleBlockedByParentException(
						com.example.serverprovision.global.marker.ResourceType.BOARD_MODEL,
						parent.getId(), parentState,
						com.example.serverprovision.global.marker.ResourceType.BIOS_BUNDLE,
						biosId, "enable",
						parent.displayName()
				);
			}
		}
		bios.toggleEnabled();
		log.info("[lifecycle.toggle] resource=BIOS_BUNDLE#{} enabled={} outcome=toggled", biosId, nextEnabled);
	}

	/**
	 * MK3 — soft-delete BIOS. 도메인 가드 후 공통 trash 흐름 위임. MK3-2 사전조건 추가.
	 */
	@Transactional
	public void softDelete(Long boardId, Long biosId) {
		BoardBIOS bios = requireLiveBios(boardId, biosId);
		// MK3-2 (DCM3-2.1) — Files.exists 사전조건. flag false 면 통과.
		softDeleteIntentService.checkPrecondition(bios);
		trashLifecycleService.softDeleteToTrash(bios);
	}

	/**
	 * MK3-2 (DCM3-2.3 ~ 2.5) — softDelete reject modal 의 두 번째 호출 진입점.
	 */
	@Transactional
	public void softDeleteWithIntent(
			Long boardId, Long biosId,
			com.example.serverprovision.global.lifecycle.DeleteAction action
	) {
		switch (action) {
			case CORRECT_PATH_THEN_DELETE -> softDeleteIntentService.reconcileThenDelete(
					com.example.serverprovision.global.marker.ResourceType.BIOS_BUNDLE, biosId,
					() -> {
						BoardBIOS refreshed = requireLiveBios(boardId, biosId);
						trashLifecycleService.softDeleteToTrash(refreshed);
					}
			);
			case FORCED_CLEAR -> softDeleteIntentService.forcedClear(
					com.example.serverprovision.global.marker.ResourceType.BIOS_BUNDLE, biosId);
		}
	}

	/**
	 * MK3 — restore BIOS. S5-2-3-1 부모 가드 추가 : 부모 Board 가 deleted 상태이면 자식 단독 restore 거절.
	 */
	@Transactional
	public void restore(Long boardId, Long biosId) {
		com.example.serverprovision.management.board.entity.BoardModel parent =
				boardModelRepository.findById(boardId).orElseThrow();
		if (parent.blocksChildRestore()) {   // R2-2 — SSOT
			throw new com.example.serverprovision.global.exception.ChildLifecycleBlockedByParentException(
					com.example.serverprovision.global.marker.ResourceType.BOARD_MODEL,
					parent.getId(), "DELETED",
					com.example.serverprovision.global.marker.ResourceType.BIOS_BUNDLE,
					biosId, "restore",
					parent.displayName()
			);
		}
		BoardBIOS bios = requireExistingBios(boardId, biosId);
		if (biosRepository.existsByBoardModel_IdAndVersionAndIsDeletedFalse(boardId, bios.getVersion())) {
			throw new DuplicateBiosVersionException(boardId, bios.getVersion());
		}
		trashLifecycleService.restoreFromTrash(
				bios, biosEntity -> java.util.Map.of(
						"boardId", String.valueOf(boardId),
						"version", biosEntity.getVersion(),
						"entrypointRelativePath",
						biosEntity.getEntrypointRelativePath() == null ? "" : biosEntity.getEntrypointRelativePath()
				)
		);
	}

	/**
	 * MK2 — Active → Deprecated 전이. 엔티티 가드가 SoftDeleted / 이미 Deprecated 케이스를 거절.
	 * 현 시점에 SoftDeleted 자원에는 호출할 수 없으므로 {@code requireLiveBios} 사용 (삭제됨 → 409).
	 */
	@Transactional
	public void deprecate(Long boardId, Long biosId) {
		requireLiveBios(boardId, biosId).deprecate();
		log.info("[lifecycle.deprecate] resource=BIOS_BUNDLE#{} outcome=deprecated", biosId);
	}

	/**
	 * MK2 — Deprecated → Active 전이. 엔티티 가드가 부적합 상태를 거절.
	 * S5-2-3-1 부모 가드 : 부모 Board 가 deprecated 상태면 거절.
	 */
	@Transactional
	public void undeprecate(Long boardId, Long biosId) {
		com.example.serverprovision.management.board.entity.BoardModel parent =
				boardModelRepository.findById(boardId).orElseThrow();
		if (parent.blocksChildUndeprecate()) {   // R2-2 — SSOT (DEPRECATED 또는 DELETED)
			throw new com.example.serverprovision.global.exception.ChildLifecycleBlockedByParentException(
					com.example.serverprovision.global.marker.ResourceType.BOARD_MODEL,
					parent.getId(), parent.isDeleted() ? "DELETED" : "DEPRECATED",
					com.example.serverprovision.global.marker.ResourceType.BIOS_BUNDLE,
					biosId, "undeprecate",
					parent.displayName()
			);
		}
		requireLiveBios(boardId, biosId).undeprecate();
		log.info("[lifecycle.undeprecate] resource=BIOS_BUNDLE#{} outcome=undeprecated", biosId);
	}

	/**
	 * MK2 — SoftDeleted 자원의 영구 삭제. 트리·marker 물리 삭제 후 DB row 제거.
	 * SoftDeleted 가 아닌 자원에 호출되면 명시적 충돌로 거절 (활성 자원의 우발 영구 삭제 방어).
	 */
	@Transactional
	public void purge(Long boardId, Long biosId) {
		BoardBIOS bios = requireExistingBios(boardId, biosId);
		if (!bios.isDeleted()) {
			throw new IllegalBiosStateException(
					"활성/Deprecated 자원은 영구 삭제할 수 없습니다. 먼저 휴지통으로 이동하세요. biosId=" + biosId);
		}
		bundleTreeCleanupService.purgeExistingTree(Path.of(bios.getTreeRootPath()), "purgeBios");
		biosRepository.delete(bios);
		log.info("[lifecycle.purge] resource=BIOS_BUNDLE#{} outcome=purged", biosId);
	}

	/**
	 * S5-2-2 — BIOS typed-name 검증 후 영구 삭제.
	 * 합성식 : {@code bios.name}.
	 */
	@Transactional
	public void purgeWithTypedNameCheck(Long boardId, Long biosId, String typedName) {
		BoardBIOS bios = requireExistingBios(boardId, biosId);
		if (!bios.isDeleted()) {
			throw new IllegalBiosStateException(
					"활성/Deprecated 자원은 영구 삭제할 수 없습니다. 먼저 휴지통으로 이동하세요. biosId=" + biosId);
		}
		String expected = bios.displayName();
		if (!expected.equals(typedName)) {
			throw new TypedNameMismatchException(expected, typedName);
		}
		purge(boardId, biosId);
	}

	// ==== 무결성 / marker 재발급 =======================================

	public IntegrityStatus verifyIntegrity(Long boardId, Long biosId) {
		BoardBIOS bios = requireLiveBios(boardId, biosId);
		Path treeRoot = Path.of(bios.getTreeRootPath());
		MarkerContent marker;
		try {
			marker = provisionMarkerService.read(treeRoot, MarkerLayout.IN_TREE);
		} catch (MarkerMissingException e) {
			return IntegrityStatus.MARKER_MISSING;
		}
		if (!provisionMarkerService.verifySignature(marker)) {
			return IntegrityStatus.SIGNATURE_INVALID;
		}
		ManifestSummary recomputed = bundleManifestService.compute(treeRoot);
		if (!provisionMarkerService.verifyManifestHash(marker, recomputed.manifestHash())) {
			return IntegrityStatus.TAMPERED;
		}
		return IntegrityStatus.ORIGINAL;
	}

	@Transactional
	public IntegrityStatus verifyAndRecordIntegrity(Long boardId, Long biosId) {
		IntegrityStatus status = verifyIntegrity(boardId, biosId);
		requireLiveBios(boardId, biosId).recordIntegritySnapshot(status, Instant.now());
		return status;
	}

	// 단건 BIOS marker 재발급 메서드는 위험도가 높아 외부 endpoint 와 함께 제거됨.
	// 일괄 재발급(secret 회전 시)은 PathReconciliationService.performReissue 가 담당.
	// 이전 hash → 새 hash audit 로그도 그곳의 일괄 audit 으로 통합.

	// ==== 내부 헬퍼 =====================================================

	private BoardModel requireActiveBoard(Long boardId) {
		return boardModelRepository.findByIdAndIsDeletedFalse(boardId)
				.orElseThrow(() -> new BoardModelNotFoundException(boardId));
	}

	private BoardBIOS requireLiveBios(Long boardId, Long biosId) {
		requireActiveBoard(boardId);
		BoardBIOS bios = biosRepository.findByIdAndBoardModel_Id(biosId, boardId)
				.orElseThrow(() -> new BiosNotFoundException(boardId, biosId));
		if (bios.isDeleted()) {
			throw new IllegalBiosStateException("삭제된 BIOS 에는 수행할 수 없는 작업입니다. biosId=" + biosId);
		}
		return bios;
	}

	/**
	 * MK2 — 상태 무관 단건 조회. SoftDeleted 자원에 대한 restore / purge 가 사용한다.
	 *
	 * <p>보드 <b>활성</b> 여부는 요구하지 않는다 — join({@code findByIdAndBoardModel_Id})이 board 연관을 이미
	 * 검증하고, soft-deleted 부모 아래의 ghost / soft-deleted 자식 정리(purge)도 가능해야 하기 때문(ghost catch-22
	 * 차단 : soft-delete 는 자식을 hard-delete 하지 않으므로 "보드 삭제 cascade 가 처리" 가정이 성립하지 않는다).
	 * restore 는 호출 전 {@code blocksChildRestore} 부모 가드가 선행하므로 본 메서드의 활성 board 검증은 불필요.</p>
	 */
	private BoardBIOS requireExistingBios(Long boardId, Long biosId) {
		return biosRepository.findByIdAndBoardModel_Id(biosId, boardId)
				.orElseThrow(() -> new BiosNotFoundException(boardId, biosId));
	}

	private static BiosResponse toResponse(BoardBIOS entity) {
		return new BiosResponse(
				entity.getId(),
				entity.getBoardModel().getId(),
				entity.getName(),
				entity.getVersion(),
				entity.getTreeRootPath(),
				entity.getEntrypointRelativePath(),
				entity.getManifestHash(),
				entity.getFileCount(),
				entity.getTotalBytes(),
				entity.getDescription(),
				entity.getLastIntegrityStatus() != null ? entity.getLastIntegrityStatus() : IntegrityStatus.NOT_VERIFIED,
				entity.isEnabled(),
				entity.isDeleted(),
				entity.isDeprecated(),
				// R2-2 — 부모 BoardModel lifecycle 가드 (엔티티 그래프로 도달, repo 조회 0).
				entity.getBoardModel().blocksChildEnable(),
				entity.getBoardModel().blocksChildRestore(),
				entity.getBoardModel().blocksChildUndeprecate()
		);
	}

	// ==== MK2 — BiosNudgeService 가 사용할 helper (package-private 접근) =================

	/**
	 * MK2 — nudge proceed/replace 후 임시 트리를 ACTIVE 자원으로 영속화한다. 단계 B 의 {@link #addBios}
	 * 흐름 중 entity save + marker write 부분만 재사용. PendingPayload 는 BiosNudgeService 가 nudge
	 * 세션에서 가져와 전달한다.
	 */
	@Transactional
	public Long persistFromNudge(Long boardId, com.example.serverprovision.management.common.nudge.ContentNudgePayload payload) {
		BoardModel parent = requireActiveBoard(boardId);
		// 활성 (board, version) 재검증 — replace 트랜잭션이 외부에서 별도 commit 됐을 수 있으므로.
		if (biosRepository.existsByBoardModel_IdAndVersionAndIsDeletedFalse(boardId, payload.version())) {
			throw new DuplicateBiosVersionException(boardId, payload.version());
		}
		Path targetDir = pathPolicyService.assertWritablePath(payload.tempFilePath());
		String entrypoint = payload.attributes().getOrDefault("entrypointRelativePath", "");
		int fileCount = Integer.parseInt(payload.attributes().getOrDefault("fileCount", "0"));
		long totalBytes = Long.parseLong(payload.attributes().getOrDefault("totalBytes", "0"));
		String description = payload.attributes().getOrDefault("description", "");

		BoardBIOS saved = biosRepository.save(BoardBIOS.builder()
													  .boardModel(parent)
													  .name(payload.name())
													  .version(payload.version())
													  .treeRootPath(targetDir.toString())
													  .entrypointRelativePath(entrypoint)
													  .manifestHash(payload.manifestHash())
													  .markerSignature(null)
													  .fileCount(fileCount)
													  .totalBytes(totalBytes)
													  .description(description.isEmpty() ? null : description)
													  .isEnabled(true)
													  .isDeleted(false)
													  .build());

		biosMarkerWriter.writeSignedMarker(
				saved, targetDir, boardId, payload.version(), entrypoint, payload.manifestHash());
		log.info("[persistFromNudge] biosId={}, boardId={}, version={}", saved.getId(), boardId, payload.version());
		return saved.getId();
	}

	/**
	 * MK2 — nudge cancel 시 임시 트리 정리. allowed-roots 가드는 cleanup 내부에서 수행.
	 */
	public void purgeNudgeTempTree(Path tempPath) {
		bundleTreeCleanupService.purgeExistingTree(tempPath, "nudgeCancel");
	}
}
