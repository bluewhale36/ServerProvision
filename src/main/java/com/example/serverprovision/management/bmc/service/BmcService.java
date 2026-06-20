package com.example.serverprovision.management.bmc.service;

import com.example.serverprovision.global.exception.TypedNameMismatchException;
import com.example.serverprovision.global.lifecycle.LifecycleStage;
import com.example.serverprovision.global.marker.MarkerContent;
import com.example.serverprovision.global.marker.MarkerLayout;
import com.example.serverprovision.global.marker.ResourceType;
import com.example.serverprovision.global.marker.exception.MarkerMissingException;
import com.example.serverprovision.global.marker.service.ProvisionMarkerService;
import com.example.serverprovision.global.security.PathPolicyService;
import com.example.serverprovision.management.bios.service.BundleEntrypointDetector;
import com.example.serverprovision.management.bios.service.BundleExtractionService;
import com.example.serverprovision.management.bios.service.BundleManifestService;
import com.example.serverprovision.management.bios.service.BundleManifestService.ManifestSummary;
import com.example.serverprovision.management.bios.vo.IntegrityStatus;
import com.example.serverprovision.management.bmc.dto.request.BmcCreateRequest;
import com.example.serverprovision.management.bmc.dto.request.BmcRegisterExistingRequest;
import com.example.serverprovision.management.bmc.dto.request.BmcUpdateRequest;
import com.example.serverprovision.management.bmc.dto.response.BmcResponse;
import com.example.serverprovision.management.bmc.dto.response.BoardWithBmcListResponse;
import com.example.serverprovision.management.bmc.entity.BoardBMC;
import com.example.serverprovision.management.bmc.enums.BmcUploadMode;
import com.example.serverprovision.management.bmc.exception.BmcNotFoundException;
import com.example.serverprovision.management.bmc.exception.BmcNudgeRequiredException;
import com.example.serverprovision.management.bmc.exception.DuplicateBmcVersionException;
import com.example.serverprovision.management.bmc.exception.IllegalBmcStateException;
import com.example.serverprovision.management.bmc.repository.BmcRepository;
import com.example.serverprovision.management.board.entity.BoardModel;
import com.example.serverprovision.management.board.exception.BoardModelNotFoundException;
import com.example.serverprovision.management.board.repository.BoardModelRepository;
import com.example.serverprovision.management.common.dto.response.IntegrityStatusResponse;
import com.example.serverprovision.management.common.filesystem.exception.TargetDirectoryNotEmptyException;
import com.example.serverprovision.management.common.filesystem.service.BundleTreeCleanupService;
import com.example.serverprovision.management.common.filesystem.service.TargetDirectoryPolicyService;
import com.example.serverprovision.management.common.nudge.ContentNudgePayload;
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
 * MA4 BMC 펌웨어 도메인 로직 총괄.
 * BMC 는 BIOS 와 같은 번들 디렉토리 자원으로 저장하고 IN_TREE 마커를 사용한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BmcService {

	private final BmcRepository bmcRepository;
	private final BoardModelRepository boardModelRepository;
	private final BundleExtractionService bundleExtractionService;
	private final BundleEntrypointDetector bundleEntrypointDetector;
	private final BundleManifestService bundleManifestService;
	private final ProvisionMarkerService provisionMarkerService;
	private final TargetDirectoryPolicyService targetDirectoryPolicyService;
	private final BundleTreeCleanupService bundleTreeCleanupService;
	private final PathPolicyService pathPolicyService;
	private final NudgeRegistry nudgeRegistry;
	private final com.example.serverprovision.global.trash.TrashLifecycleService trashLifecycleService;
	private final com.example.serverprovision.global.lifecycle.SoftDeleteIntentService softDeleteIntentService;

	public List<BoardWithBmcListResponse> findAllGrouped(boolean includeDeleted) {
		List<BoardModel> boards = includeDeleted
				? boardModelRepository.findAllByOrderByVendorAscCreatedAtDesc()
				: boardModelRepository.findAllByIsDeletedFalseOrderByVendorAscCreatedAtDesc();
		if (boards.isEmpty()) return List.of();

		List<Long> boardIds = boards.stream().map(BoardModel::getId).toList();
		List<BoardBMC> allBmc = bmcRepository.findAllByBoardModel_IdIn(boardIds);
		Map<Long, List<BoardBMC>> byBoard = allBmc.stream()
				.filter(b -> includeDeleted || !b.isDeleted())
				.collect(Collectors.groupingBy(b -> b.getBoardModel().getId(), HashMap::new, Collectors.toList()));

		return boards.stream()
				.map(board -> new BoardWithBmcListResponse(
						board.getId(),
						board.getVendor(),
						board.getVendor().getDisplayName(),
						board.getModelName(),
						board.isDeleted(),
						byBoard.getOrDefault(board.getId(), List.of()).stream()
								.sorted(Comparator.comparing(BoardBMC::getVersion).reversed())
								.map(BmcService::toResponse)
								.toList()
				))
				.toList();
	}

	public BmcResponse findBmc(Long boardId, Long bmcId) {
		return toResponse(requireLiveBmc(boardId, bmcId));
	}

	public IntegrityStatusResponse findIntegrityStatus(Long boardId, Long bmcId) {
		BoardBMC bmc = requireLiveBmc(boardId, bmcId);
		return IntegrityStatusResponse.of(
				bmc.getId(),
				bmc.getLastIntegrityStatus() != null ? bmc.getLastIntegrityStatus() : IntegrityStatus.NOT_VERIFIED,
				bmc.getLastVerifiedAt()
		);
	}

	@Transactional
	public Long addBmc(
			Long boardId,
			BmcCreateRequest request,
			BmcUploadMode uploadMode,
			MultipartFile[] folderFiles,
			MultipartFile zipFile,
			MultipartFile singleFile
	) {
		BoardModel parent = requireActiveBoard(boardId);

		if (bmcRepository.existsByBoardModel_IdAndVersionAndIsDeletedFalse(boardId, request.version())) {
			throw new DuplicateBmcVersionException(boardId, request.version());
		}

		// S3 — allowlist 검증된 절대경로만 사용
		Path targetDir = pathPolicyService.assertWritablePath(request.targetDirectory());

		// MK2 — 자동 hard-delete 인라인 로직 제거. soft-deleted 동일 (board, version) 충돌은
		// NudgeRegistry 흐름에서 사용자 명시 액션 (PROCEED / REPLACE / CANCEL) 으로 해소된다.

		targetDirectoryPolicyService.prepareForUpload(targetDir, request.allowCreateDirectory());

		bmcRepository.findFirstByBoardModel_IdAndTreeRootPathAndIsDeletedFalse(boardId, targetDir.toString())
				.ifPresent(existing -> {
					throw new TargetDirectoryNotEmptyException(existing.getTreeRootPath());
				});

		try {
			switch (uploadMode) {
				case FOLDER -> bundleExtractionService.extractFolder(folderFiles, targetDir);
				case ZIP -> bundleExtractionService.extractZip(zipFile, targetDir);
				case SINGLE_FILE -> bundleExtractionService.extractSingleFile(singleFile, targetDir);
			}

			// S5-11 v2 — Vendor 별 strategy 위임
			String entrypoint = bundleEntrypointDetector.detect(
					parent.getVendor(), targetDir, request.entrypointRelativePath());
			ManifestSummary manifest = bundleManifestService.compute(targetDir);

			// MK2 단계 B — 해시 충돌 후보 (SoftDeleted / Deprecated) 탐지 시 nudge 세션 발급 + 409.
			// MK3-1 — ghost 후보 사전 필터링.
			List<BoardBMC> hashCandidates = bmcRepository.findHashConflictCandidates(boardId, manifest.manifestHash())
					.stream()
					.filter(c -> !com.example.serverprovision.global.trash.GhostEvaluator.isGhost(c))
					.toList();
			if (!hashCandidates.isEmpty()) {
				NudgeSession session = nudgeRegistry.register(
						NudgeResourceType.BMC,
						boardId,
						hashCandidates.stream().map(BoardBMC::getId).toList(),
						new ContentNudgePayload(
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
						"[addBmc] nudge required : boardId={}, version={}, candidates={}",
						boardId, request.version(), hashCandidates.size()
				);
				throw new BmcNudgeRequiredException(session, entries);
			}

			BoardBMC saved = bmcRepository.save(BoardBMC.builder()
														.boardModel(parent)
														.name(request.name())
														.version(request.version())
														.treeRootPath(targetDir.toString())
														.legacyFilePath(targetDir.toString())
														.boardModelIdMirror(parent.getId())
														.entrypointRelativePath(entrypoint)
														.manifestHash(manifest.manifestHash())
														.markerSignature(null)
														.fileCount(manifest.fileCount())
														.totalBytes(manifest.totalBytes())
														.description(request.description())
														.isEnabled(true)
														.isDeleted(false)
														.build());

			MarkerContent unsigned = new MarkerContent(
					ResourceType.BMC_FIRMWARE.name(),
					saved.getId(),
					Map.of(
							"boardId", String.valueOf(boardId),
							"version", request.version(),
							"entrypointRelativePath", entrypoint
					),
					Instant.now(),
					manifest.manifestHash(),
					null
			);
			String signature = provisionMarkerService.computeSignature(unsigned);
			saved.reissueMarker(manifest.manifestHash(), signature);
			provisionMarkerService.write(targetDir, MarkerLayout.IN_TREE, unsigned.withSignature(signature));

			log.info(
					"[addBmc] 등록 완료. bmcId={}, boardId={}, version={}, fileCount={}, totalBytes={}",
					saved.getId(), boardId, request.version(), manifest.fileCount(), manifest.totalBytes()
			);
			return saved.getId();
		} catch (BmcNudgeRequiredException e) {
			// MK2 — nudge 결정 대기 동안 임시 트리는 보존 (사용자 proceed 시 정식 영속화에 재사용).
			throw e;
		} catch (RuntimeException e) {
			bundleTreeCleanupService.cleanupFailedUpload(targetDir, "purgeExistingTree", "addBMC", e);
			throw e;
		}
	}

	/**
	 * 기존 디렉토리를 BMC 자원으로 등록. 업로드 없이 이미 있는 트리를 claim.
	 * 추출 단계만 생략하고 검증 / nudge / marker 흐름은 {@link #addBmc} 와 일치한다.
	 */
	@Transactional
	public Long registerExisting(Long boardId, BmcRegisterExistingRequest request) {
		BoardModel parent = requireActiveBoard(boardId);

		if (bmcRepository.existsByBoardModel_IdAndVersionAndIsDeletedFalse(boardId, request.version())) {
			throw new DuplicateBmcVersionException(boardId, request.version());
		}

		Path targetDir = pathPolicyService.assertWritablePath(request.targetDirectory());
		targetDirectoryPolicyService.prepareForExistingDirectoryRegistration(targetDir);

		bmcRepository.findFirstByBoardModel_IdAndTreeRootPathAndIsDeletedFalse(boardId, targetDir.toString())
				.ifPresent(existing -> {
					throw new TargetDirectoryNotEmptyException(existing.getTreeRootPath());
				});

		// S5-11 v2 — Vendor 별 strategy 위임
		String entrypoint = bundleEntrypointDetector.detect(
				parent.getVendor(), targetDir, request.entrypointRelativePath());
		ManifestSummary manifest = bundleManifestService.compute(targetDir);

		// MK3-1 — ghost 후보 사전 필터링.
		List<BoardBMC> hashCandidates = bmcRepository.findHashConflictCandidates(boardId, manifest.manifestHash())
				.stream()
				.filter(c -> !com.example.serverprovision.global.trash.GhostEvaluator.isGhost(c))
				.toList();
		if (!hashCandidates.isEmpty()) {
			NudgeSession session = nudgeRegistry.register(
					NudgeResourceType.BMC,
					boardId,
					hashCandidates.stream().map(BoardBMC::getId).toList(),
					new ContentNudgePayload(
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
					"[registerExistingBmc] nudge required : boardId={}, version={}, candidates={}",
					boardId, request.version(), hashCandidates.size()
			);
			throw new BmcNudgeRequiredException(session, entries);
		}

		BoardBMC saved = bmcRepository.save(BoardBMC.builder()
													.boardModel(parent)
													.name(request.name())
													.version(request.version())
													.treeRootPath(targetDir.toString())
													.legacyFilePath(targetDir.toString())
													.boardModelIdMirror(parent.getId())
													.entrypointRelativePath(entrypoint)
													.manifestHash(manifest.manifestHash())
													.markerSignature(null)
													.fileCount(manifest.fileCount())
													.totalBytes(manifest.totalBytes())
													.description(request.description())
													.isEnabled(true)
													.isDeleted(false)
													.build());

		MarkerContent unsigned = new MarkerContent(
				ResourceType.BMC_FIRMWARE.name(),
				saved.getId(),
				Map.of(
						"boardId", String.valueOf(boardId),
						"version", request.version(),
						"entrypointRelativePath", entrypoint
				),
				Instant.now(),
				manifest.manifestHash(),
				null
		);
		String signature = provisionMarkerService.computeSignature(unsigned);
		saved.reissueMarker(manifest.manifestHash(), signature);
		provisionMarkerService.write(targetDir, MarkerLayout.IN_TREE, unsigned.withSignature(signature));

		log.info(
				"[registerExistingBmc] 등록 완료. bmcId={}, boardId={}, version={}, fileCount={}, totalBytes={}",
				saved.getId(), boardId, request.version(), manifest.fileCount(), manifest.totalBytes()
		);
		return saved.getId();
	}

	// ==== MK2 — BmcNudgeService 가 사용할 helper =====================

	/**
	 * MK2 — nudge proceed/replace 후 임시 트리를 ACTIVE 자원으로 영속화.
	 */
	@Transactional
	public Long persistFromNudge(Long boardId, ContentNudgePayload payload) {
		BoardModel parent = requireActiveBoard(boardId);
		if (bmcRepository.existsByBoardModel_IdAndVersionAndIsDeletedFalse(boardId, payload.version())) {
			throw new DuplicateBmcVersionException(boardId, payload.version());
		}
		Path targetDir = pathPolicyService.assertWritablePath(payload.tempFilePath());
		String entrypoint = payload.attributes().getOrDefault("entrypointRelativePath", "");
		int fileCount = Integer.parseInt(payload.attributes().getOrDefault("fileCount", "0"));
		long totalBytes = Long.parseLong(payload.attributes().getOrDefault("totalBytes", "0"));
		String description = payload.attributes().getOrDefault("description", "");

		BoardBMC saved = bmcRepository.save(BoardBMC.builder()
													.boardModel(parent)
													.name(payload.name())
													.version(payload.version())
													.treeRootPath(targetDir.toString())
													.legacyFilePath(targetDir.toString())
													.boardModelIdMirror(parent.getId())
													.entrypointRelativePath(entrypoint)
													.manifestHash(payload.manifestHash())
													.markerSignature(null)
													.fileCount(fileCount)
													.totalBytes(totalBytes)
													.description(description)
													.isEnabled(true)
													.isDeleted(false)
													.build());

		MarkerContent unsigned = new MarkerContent(
				ResourceType.BMC_FIRMWARE.name(),
				saved.getId(),
				Map.of(
						"boardId", String.valueOf(boardId),
						"version", payload.version(),
						"entrypointRelativePath", entrypoint
				),
				Instant.now(),
				payload.manifestHash(),
				null
		);
		String signature = provisionMarkerService.computeSignature(unsigned);
		saved.reissueMarker(payload.manifestHash(), signature);
		provisionMarkerService.write(targetDir, MarkerLayout.IN_TREE, unsigned.withSignature(signature));

		log.info("[persistFromNudge.bmc] 영속화 완료. bmcId={}, boardId={}", saved.getId(), boardId);
		return saved.getId();
	}

	/**
	 * MK2 — nudge cancel 시 임시 트리 cleanup.
	 */
	@Transactional
	public void purgeNudgeTempTree(Path tempPath) {
		bundleTreeCleanupService.purgeExistingTree(tempPath, "purgeNudgeTempTree.bmc");
	}

	@Transactional
	public void update(Long boardId, Long bmcId, BmcUpdateRequest request) {
		BoardBMC bmc = requireLiveBmc(boardId, bmcId);
		if (!bmc.getVersion().equals(request.version())
				&& bmcRepository.existsByBoardModel_IdAndVersionAndIsDeletedFalse(boardId, request.version())) {
			throw new DuplicateBmcVersionException(boardId, request.version());
		}
		bmc.update(request.name(), request.version(), request.description());
	}

	/**
	 * S5-2-3-1 — 자식 BMC 단독 toggle. 부모 가드 : 부모 Board 비활성/Deprecated 시 enable 거절.
	 */
	@Transactional
	public void toggleEnabled(Long boardId, Long bmcId) {
		BoardBMC bmc = requireLiveBmc(boardId, bmcId);
		boolean nextEnabled = !bmc.isEnabled();
		if (nextEnabled) {
			com.example.serverprovision.management.board.entity.BoardModel parent =
					boardModelRepository.findById(boardId).orElseThrow();
			String parentState = parent.childEnableBlockReason();   // R2-2 — SSOT (DELETED comprehensive)
			if (parentState != null) {
				throw new com.example.serverprovision.global.exception.ChildLifecycleBlockedByParentException(
						com.example.serverprovision.global.marker.ResourceType.BOARD_MODEL,
						parent.getId(), parentState,
						com.example.serverprovision.global.marker.ResourceType.BMC_FIRMWARE,
						bmcId, "enable",
						parent.displayName()
				);
			}
		}
		bmc.toggleEnabled();
		log.info("[lifecycle.toggle] resource=BMC_FIRMWARE#{} enabled={} outcome=toggled", bmcId, nextEnabled);
	}

	/**
	 * MK3 — soft-delete BMC. 도메인 가드 후 공통 trash 흐름 위임. MK3-2 사전조건 추가.
	 */
	@Transactional
	public void softDelete(Long boardId, Long bmcId) {
		BoardBMC bmc = requireLiveBmc(boardId, bmcId);
		// MK3-2 (DCM3-2.1) — Files.exists 사전조건. flag false 면 통과.
		softDeleteIntentService.checkPrecondition(bmc);
		trashLifecycleService.softDeleteToTrash(bmc);
	}

	/**
	 * MK3-2 (DCM3-2.3 ~ 2.5) — softDelete reject modal 의 두 번째 호출 진입점.
	 */
	@Transactional
	public void softDeleteWithIntent(
			Long boardId, Long bmcId,
			com.example.serverprovision.global.lifecycle.DeleteAction action
	) {
		switch (action) {
			case CORRECT_PATH_THEN_DELETE -> softDeleteIntentService.reconcileThenDelete(
					com.example.serverprovision.global.marker.ResourceType.BMC_FIRMWARE, bmcId,
					() -> {
						BoardBMC refreshed = requireLiveBmc(boardId, bmcId);
						trashLifecycleService.softDeleteToTrash(refreshed);
					}
			);
			case FORCED_CLEAR -> softDeleteIntentService.forcedClear(
					com.example.serverprovision.global.marker.ResourceType.BMC_FIRMWARE, bmcId);
		}
	}

	/** MK3 — restore BMC. 도메인 가드 + 공통 흐름. attributes 는 BMC 도메인 메타. */
	/**
	 * MK3 — restore BMC. S5-2-3-1 부모 가드 추가 : 부모 Board 가 deleted 시 자식 단독 restore 거절.
	 */
	@Transactional
	public void restore(Long boardId, Long bmcId) {
		com.example.serverprovision.management.board.entity.BoardModel parent =
				boardModelRepository.findById(boardId).orElseThrow();
		if (parent.blocksChildRestore()) {   // R2-2 — SSOT
			throw new com.example.serverprovision.global.exception.ChildLifecycleBlockedByParentException(
					com.example.serverprovision.global.marker.ResourceType.BOARD_MODEL,
					parent.getId(), "DELETED",
					com.example.serverprovision.global.marker.ResourceType.BMC_FIRMWARE,
					bmcId, "restore",
					parent.displayName()
			);
		}
		BoardBMC bmc = bmcRepository.findByIdAndBoardModel_Id(bmcId, boardId)
				.orElseThrow(() -> new BmcNotFoundException(boardId, bmcId));
		if (!bmc.isDeleted()) {
			throw new IllegalBmcStateException("이미 활성 상태인 BMC 펌웨어입니다. bmcId=" + bmcId);
		}
		if (bmcRepository.existsByBoardModel_IdAndVersionAndIsDeletedFalse(boardId, bmc.getVersion())) {
			throw new DuplicateBmcVersionException(boardId, bmc.getVersion());
		}
		trashLifecycleService.restoreFromTrash(
				bmc, bmcEntity -> java.util.Map.of(
						"boardId", String.valueOf(boardId),
						"version", bmcEntity.getVersion(),
						"entrypointRelativePath",
						bmcEntity.getEntrypointRelativePath() == null ? "" : bmcEntity.getEntrypointRelativePath()
				)
		);
	}

	public IntegrityStatus verifyIntegrity(Long boardId, Long bmcId) {
		BoardBMC bmc = requireLiveBmc(boardId, bmcId);
		Path treeRoot = Path.of(bmc.getTreeRootPath());
		MarkerContent marker;
		try {
			marker = provisionMarkerService.read(treeRoot, MarkerLayout.IN_TREE);
		} catch (MarkerMissingException e) {
			return IntegrityStatus.MARKER_MISSING;
		}
		if (!provisionMarkerService.verifySignature(marker)) {
			return IntegrityStatus.SIGNATURE_INVALID;
		}
		String recomputed = bundleManifestService.compute(treeRoot).manifestHash();
		if (!provisionMarkerService.verifyManifestHash(marker, recomputed)) {
			return IntegrityStatus.TAMPERED;
		}
		return IntegrityStatus.ORIGINAL;
	}

	@Transactional
	public IntegrityStatus verifyAndRecordIntegrity(Long boardId, Long bmcId) {
		IntegrityStatus status = verifyIntegrity(boardId, bmcId);
		requireLiveBmc(boardId, bmcId).recordIntegritySnapshot(status, Instant.now());
		return status;
	}

	private BoardModel requireActiveBoard(Long boardId) {
		return boardModelRepository.findByIdAndIsDeletedFalse(boardId)
				.orElseThrow(() -> new BoardModelNotFoundException(boardId));
	}

	private BoardBMC requireLiveBmc(Long boardId, Long bmcId) {
		requireActiveBoard(boardId);
		BoardBMC bmc = bmcRepository.findByIdAndBoardModel_Id(bmcId, boardId)
				.orElseThrow(() -> new BmcNotFoundException(boardId, bmcId));
		if (bmc.isDeleted()) {
			throw new IllegalBmcStateException("삭제된 BMC 펌웨어에는 수행할 수 없는 작업입니다. bmcId=" + bmcId);
		}
		return bmc;
	}

	private static BmcResponse toResponse(BoardBMC entity) {
		return new BmcResponse(
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

	// ==== MK2 lifecycle 액션 ============================================

	/**
	 * Active → Deprecated 전이. {@link com.example.serverprovision.global.lifecycle.exception.IllegalDeprecationStateException}
	 * 가 super 단에서 던져진다 (이미 deprecated 거나 삭제된 경우).
	 */
	@Transactional
	public void deprecate(Long boardId, Long bmcId) {
		requireLiveBmc(boardId, bmcId).deprecate();
		log.info("[lifecycle.deprecate] resource=BMC_FIRMWARE#{} outcome=deprecated", bmcId);
	}

	/**
	 * Deprecated → Active 전이.
	 */
	/**
	 * S5-2-3-1 — 자식 BMC 단독 undeprecate. 부모 가드 : 부모 Board deprecated 시 거절.
	 */
	@Transactional
	public void undeprecate(Long boardId, Long bmcId) {
		com.example.serverprovision.management.board.entity.BoardModel parent =
				boardModelRepository.findById(boardId).orElseThrow();
		if (parent.blocksChildUndeprecate()) {   // R2-2 — SSOT (DEPRECATED 또는 DELETED)
			throw new com.example.serverprovision.global.exception.ChildLifecycleBlockedByParentException(
					com.example.serverprovision.global.marker.ResourceType.BOARD_MODEL,
					parent.getId(), parent.isDeleted() ? "DELETED" : "DEPRECATED",
					com.example.serverprovision.global.marker.ResourceType.BMC_FIRMWARE,
					bmcId, "undeprecate",
					parent.displayName()
			);
		}
		requireLiveBmc(boardId, bmcId).undeprecate();
		log.info("[lifecycle.undeprecate] resource=BMC_FIRMWARE#{} outcome=undeprecated", bmcId);
	}

	/**
	 * Soft-deleted 한정 영구 삭제 (트리 제거 + DB row delete).
	 * Active / Deprecated 자원에는 호출 금지 — 가드 후 hard-delete 하면 가시 자원이 사라지므로.
	 */
	@Transactional
	public void purge(Long boardId, Long bmcId) {
		// soft-deleted BMC purge 는 활성 부모 board 를 요구하지 않는다(ghost catch-22 차단).
		// join 이 board 연관을 검증하고, 아래 isDeleted 가드가 활성 자원 우발 삭제를 막는다.
		BoardBMC bmc = bmcRepository.findByIdAndBoardModel_Id(bmcId, boardId)
				.orElseThrow(() -> new BmcNotFoundException(boardId, bmcId));
		if (!bmc.isDeleted()) {
			throw new IllegalBmcStateException(
					"영구 삭제는 휴지통(soft-deleted) 상태의 BMC 펌웨어만 가능합니다. bmcId=" + bmcId);
		}
		bundleTreeCleanupService.purgeExistingTree(Path.of(bmc.getTreeRootPath()), "purgeBmc");
		bmcRepository.delete(bmc);
		log.info("[lifecycle.purge] resource=BMC_FIRMWARE#{} outcome=purged", bmcId);
	}

	/**
	 * S5-2-2 — BMC typed-name 검증 후 영구 삭제.
	 * 합성식 : {@code bmc.name}.
	 */
	@Transactional
	public void purgeWithTypedNameCheck(Long boardId, Long bmcId, String typedName) {
		// soft-deleted BMC purge 는 활성 부모 board 를 요구하지 않는다(ghost catch-22 차단). join 이 board 연관 검증.
		BoardBMC bmc = bmcRepository.findByIdAndBoardModel_Id(bmcId, boardId)
				.orElseThrow(() -> new BmcNotFoundException(boardId, bmcId));
		if (!bmc.isDeleted()) {
			throw new IllegalBmcStateException(
					"영구 삭제는 휴지통(soft-deleted) 상태의 BMC 펌웨어만 가능합니다. bmcId=" + bmcId);
		}
		String expected = bmc.displayName();
		if (!expected.equals(typedName)) {
			throw new TypedNameMismatchException(expected, typedName);
		}
		purge(boardId, bmcId);
	}
}
