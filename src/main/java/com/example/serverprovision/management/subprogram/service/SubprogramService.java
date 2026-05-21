package com.example.serverprovision.management.subprogram.service;

import com.example.serverprovision.global.exception.TypedNameMismatchException;
import com.example.serverprovision.global.lifecycle.LifecycleStage;
import com.example.serverprovision.global.marker.MarkerContent;
import com.example.serverprovision.global.marker.MarkerLayout;
import com.example.serverprovision.global.marker.ResourceType;
import com.example.serverprovision.global.marker.exception.MarkerMissingException;
import com.example.serverprovision.global.marker.service.ProvisionMarkerService;
import com.example.serverprovision.global.security.EntrypointPolicyService;
import com.example.serverprovision.global.security.PathPolicyService;
import com.example.serverprovision.management.bios.service.BundleExtractionService;
import com.example.serverprovision.management.bios.service.BundleManifestService;
import com.example.serverprovision.management.bios.service.BundleManifestService.ManifestSummary;
import com.example.serverprovision.management.bios.vo.IntegrityStatus;
import com.example.serverprovision.management.board.entity.BoardModel;
import com.example.serverprovision.management.board.exception.BoardModelNotFoundException;
import com.example.serverprovision.management.board.repository.BoardModelRepository;
import com.example.serverprovision.management.common.dto.response.IntegrityStatusResponse;
import com.example.serverprovision.management.common.filesystem.service.BundleTreeCleanupService;
import com.example.serverprovision.management.common.filesystem.service.TargetDirectoryPolicyService;
import com.example.serverprovision.management.common.nudge.ContentNudgePayload;
import com.example.serverprovision.management.common.nudge.NudgeRegistry;
import com.example.serverprovision.management.common.nudge.NudgeResourceType;
import com.example.serverprovision.management.common.nudge.NudgeSession;
import com.example.serverprovision.management.common.nudge.dto.NudgeConflictEntry;
import com.example.serverprovision.management.subprogram.dto.request.SubprogramCreateRequest;
import com.example.serverprovision.management.subprogram.dto.request.SubprogramRegisterExistingRequest;
import com.example.serverprovision.management.subprogram.dto.request.SubprogramUpdateRequest;
import com.example.serverprovision.management.subprogram.dto.response.BoardWithSubprogramListResponse;
import com.example.serverprovision.management.subprogram.dto.response.SubprogramResponse;
import com.example.serverprovision.management.subprogram.entity.Subprogram;
import com.example.serverprovision.management.subprogram.enums.SubprogramKind;
import com.example.serverprovision.management.subprogram.enums.SubprogramUploadMode;
import com.example.serverprovision.management.subprogram.exception.*;
import com.example.serverprovision.management.subprogram.repository.SubprogramRepository;
import com.example.serverprovision.management.subprogram.vo.BoardScope;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Path;
import java.time.Instant;
import java.util.*;

/**
 * MA5 Subprogram 도메인 로직 총괄. Driver / Utility 통합.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SubprogramService {

	private final SubprogramRepository subprogramRepository;
	private final BoardModelRepository boardModelRepository;
	private final BundleExtractionService bundleExtractionService;
	private final BundleManifestService bundleManifestService;
	private final ProvisionMarkerService provisionMarkerService;
	private final TargetDirectoryPolicyService targetDirectoryPolicyService;
	private final BundleTreeCleanupService bundleTreeCleanupService;
	private final PathPolicyService pathPolicyService;
	private final EntrypointPolicyService entrypointPolicyService;
	private final NudgeRegistry nudgeRegistry;
	private final com.example.serverprovision.global.trash.TrashLifecycleService trashLifecycleService;
	private final com.example.serverprovision.global.lifecycle.SoftDeleteIntentService softDeleteIntentService;

	/* ─────────────────────────── 조회 ─────────────────────────── */

	public List<BoardWithSubprogramListResponse> findAllGrouped(SubprogramKind kind, boolean includeDeleted) {
		// 보드별 활성 보드 + 공용 노드(첫 행 고정).
		List<BoardModel> boards = includeDeleted
				? boardModelRepository.findAllByOrderByVendorAscCreatedAtDesc()
				: boardModelRepository.findAllByIsDeletedFalseOrderByVendorAscCreatedAtDesc();

		List<Subprogram> all = includeDeleted
				? subprogramRepository.findAllByKind(kind)
				: subprogramRepository.findAllByKindAndIsDeletedFalse(kind);

		// boardId(또는 null) → 자원 목록.
		Map<Long, List<Subprogram>> byBoardId = new HashMap<>();
		List<Subprogram> commonItems = new ArrayList<>();
		for (Subprogram sp : all) {
			if (sp.isCommonScope()) {
				commonItems.add(sp);
			} else {
				byBoardId.computeIfAbsent(sp.getBoardId(), k -> new ArrayList<>()).add(sp);
			}
		}

		List<BoardWithSubprogramListResponse> rows = new ArrayList<>();
		rows.add(BoardWithSubprogramListResponse.common(toResponses(commonItems)));

		for (BoardModel board : boards) {
			List<Subprogram> items = byBoardId.getOrDefault(board.getId(), List.of());
			rows.add(new BoardWithSubprogramListResponse(
					board.getId(),
					board.getVendor(),
					board.getVendor().getDisplayName(),
					board.getModelName(),
					board.isDeleted(),
					toResponses(items)
			));
		}
		return rows;
	}

	public BoardWithSubprogramListResponse findByScope(SubprogramKind kind, BoardScope scope, boolean includeDeleted) {
		List<Subprogram> items;
		if (scope.isCommon()) {
			items = subprogramRepository.findByKindAndCommonScope(kind);
		} else {
			// 보드 활성 검증
			BoardModel board = boardModelRepository.findById(scope.boardId())
					.orElseThrow(() -> new BoardModelNotFoundException(scope.boardId()));
			items = subprogramRepository.findByKindAndBoardId(kind, scope.boardId());

			List<Subprogram> filtered = items.stream()
					.filter(s -> includeDeleted || !s.isDeleted())
					.toList();
			return new BoardWithSubprogramListResponse(
					board.getId(),
					board.getVendor(),
					board.getVendor().getDisplayName(),
					board.getModelName(),
					board.isDeleted(),
					toResponses(filtered)
			);
		}
		List<Subprogram> filtered = items.stream()
				.filter(s -> includeDeleted || !s.isDeleted())
				.toList();
		return BoardWithSubprogramListResponse.common(toResponses(filtered));
	}

	public SubprogramResponse findSubprogram(Long subprogramId) {
		return toResponse(requireLive(subprogramId));
	}

	public IntegrityStatusResponse findIntegrityStatus(Long subprogramId) {
		Subprogram sp = requireLive(subprogramId);
		return IntegrityStatusResponse.of(
				sp.getId(),
				sp.getLastIntegrityStatus() != null ? sp.getLastIntegrityStatus() : IntegrityStatus.NOT_VERIFIED,
				sp.getLastVerifiedAt()
		);
	}

	/* ─────────────────────────── 등록 ─────────────────────────── */

	@Transactional
	public Long addSubprogram(
			SubprogramKind kind,
			BoardScope scope,
			SubprogramCreateRequest request,
			SubprogramUploadMode uploadMode,
			MultipartFile[] folderFiles,
			MultipartFile zipFile,
			MultipartFile singleFile
	) {
		BoardModel parent = scope.isCommon() ? null : requireActiveBoard(scope.boardId());

		// 1) 활성 (kind, scope, name, version) 중복 검사
		Optional<Subprogram> active = scope.isCommon()
				? subprogramRepository.findActiveByCommonKey(kind, request.name(), request.version())
				: subprogramRepository.findActiveByBoardKey(kind, scope.boardId(), request.name(), request.version());
		if (active.isPresent()) {
			throw new DuplicateSubprogramVersionException(kind, scope, request.name(), request.version());
		}

		// S3 — allowlist 검증
		Path targetDir = pathPolicyService.assertWritablePath(request.targetDirectory());

		// 2) MK2 — soft-deleted 동일 키 자동 hard-delete 제거. 충돌 후보가 있으면 nudge 흐름으로 사용자에게 위임.
		//    본 메서드는 nudge 의 PROCEED 결정 직후 호출 경로용으로 남고, 호출자가 충돌 후보 부재를 사전에 보장한다.
		Optional<Subprogram> staleHolder = scope.isCommon()
				? subprogramRepository.findSoftDeletedByCommonKey(kind, request.name(), request.version())
				: subprogramRepository.findSoftDeletedByBoardKey(kind, scope.boardId(), request.name(), request.version());
		if (staleHolder.isPresent()) {
			// CP1 v3 결정 — 단계 B 충돌은 nudge 로 처리. 호출자가 nudge 흐름을 거치지 않은 채로 본 메서드에 도달하면
			// 정책 위반이므로 명시적 conflict 로 차단 (MK2 이전 자동 삭제 정책 폐지).
			Subprogram existing = staleHolder.get();
			BoardScope sc = existing.isCommonScope() ? BoardScope.COMMON : BoardScope.ofBoard(existing.getBoardId());
			throw new DuplicateSubprogramVersionException(existing.getKind(), sc, existing.getName(), existing.getVersion());
		}

		// 3) 디렉토리 정책 (비어있는지, 부모 디렉토리 등)
		targetDirectoryPolicyService.prepareForUpload(targetDir, request.allowCreateDirectory());

		// 4) 트리 루트 활성 점유 검사 (kind 무관, 모든 활성 Subprogram 통틀어서)
		subprogramRepository.findFirstByTreeRootPathAndIsDeletedFalse(targetDir.toString())
				.ifPresent(existing -> {
					throw new SubprogramPathConflictException(existing.getTreeRootPath());
				});

		try {
			switch (uploadMode) {
				case FOLDER -> bundleExtractionService.extractFolder(folderFiles, targetDir);
				case ZIP -> bundleExtractionService.extractZip(zipFile, targetDir);
				case SINGLE_FILE -> bundleExtractionService.extractSingleFile(singleFile, targetDir);
			}

			ManifestSummary manifest = bundleManifestService.compute(targetDir);

			// MK2 단계 B — 해시 충돌 후보 (SoftDeleted / Deprecated, 같은 scope) 탐지 시 nudge throw.
			// MK3-1 — ghost 후보 사전 필터링.
			List<Subprogram> hashCandidates = subprogramRepository.findHashConflictCandidates(
							kind, scope.isCommon() ? null : scope.boardId(), manifest.manifestHash())
					.stream()
					.filter(c -> !com.example.serverprovision.global.trash.GhostEvaluator.isGhost(c))
					.toList();
			if (!hashCandidates.isEmpty()) {
				NudgeSession session = nudgeRegistry.register(
						NudgeResourceType.SUBPROGRAM,
						scope.isCommon() ? null : scope.boardId(),
						hashCandidates.stream().map(Subprogram::getId).toList(),
						new ContentNudgePayload(
								request.name(),
								request.version(),
								manifest.manifestHash(),
								targetDir.toString(),
								Map.of(
										"kind", kind.name(),
										"scopeCommon", String.valueOf(scope.isCommon()),
										"boardId", scope.isCommon() ? "" : String.valueOf(scope.boardId()),
										"fileCount", String.valueOf(manifest.fileCount()),
										"totalBytes", String.valueOf(manifest.totalBytes()),
										"description", request.description() != null ? request.description() : ""
								)
						)
				);
				List<NudgeConflictEntry> entries = hashCandidates.stream()
						.map(s -> new NudgeConflictEntry(
								s.getId(),
								LifecycleStage.of(s.isDeprecated(), s.isDeleted()),
								s.getManifestHash(),
								s.getName(),
								s.getVersion(),
								Instant.now()
						))
						.toList();
				log.info(
						"[addSubprogram] nudge required : kind={}, scope={}, version={}, candidates={}",
						kind, scope.pathToken(), request.version(), hashCandidates.size()
				);
				throw new SubprogramNudgeRequiredException(session, entries);
			}

			Subprogram saved = subprogramRepository.save(Subprogram.builder()
																 .kind(kind)
																 .boardModel(parent) // null OK (공용)
																 .name(request.name())
																 .version(request.version())
																 .treeRootPath(targetDir.toString())
																 .entrypointRelativePath(null)  // MA5-D5 — 등록 시 미입력
																 .manifestHash(manifest.manifestHash())
																 .markerSignature(null)
																 .lastIntegrityStatus(IntegrityStatus.NOT_VERIFIED)
																 .fileCount(manifest.fileCount())
																 .totalBytes(manifest.totalBytes())
																 .description(request.description())
																 .isEnabled(true)
																 .isDeleted(false)
																 .build());

			MarkerContent unsigned = new MarkerContent(
					ResourceType.SUBPROGRAM.name(),
					saved.getId(),
					buildMarkerAttributes(kind, scope, request.name(), request.version()),
					Instant.now(),
					manifest.manifestHash(),
					null
			);
			String signature = provisionMarkerService.computeSignature(unsigned);
			saved.reissueMarker(manifest.manifestHash(), signature);
			provisionMarkerService.write(targetDir, MarkerLayout.IN_TREE, unsigned.withSignature(signature));

			log.info(
					"[addSubprogram] 등록 완료. id={}, kind={}, scope={}, name={}, version={}",
					saved.getId(), kind, scope.pathToken(), request.name(), request.version()
			);
			return saved.getId();
		} catch (SubprogramNudgeRequiredException e) {
			// MK2 — nudge 결정 대기 동안 임시 트리 보존 (사용자 proceed 시 정식 영속화에 재사용).
			throw e;
		} catch (RuntimeException e) {
			bundleTreeCleanupService.cleanupFailedUpload(targetDir, "purgeExistingTree", "addSubprogram", e);
			throw e;
		}
	}

	/**
	 * 기존 디렉토리를 Subprogram 자원으로 등록. 업로드 없이 이미 있는 트리를 claim.
	 * 추출 단계만 생략하고 검증 / nudge / marker 흐름은 {@link #addSubprogram} 와 일치한다.
	 */
	@Transactional
	public Long registerExisting(
			SubprogramKind kind,
			BoardScope scope,
			SubprogramRegisterExistingRequest request
	) {
		BoardModel parent = scope.isCommon() ? null : requireActiveBoard(scope.boardId());

		Optional<Subprogram> active = scope.isCommon()
				? subprogramRepository.findActiveByCommonKey(kind, request.name(), request.version())
				: subprogramRepository.findActiveByBoardKey(kind, scope.boardId(), request.name(), request.version());
		if (active.isPresent()) {
			throw new DuplicateSubprogramVersionException(kind, scope, request.name(), request.version());
		}

		Path targetDir = pathPolicyService.assertWritablePath(request.targetDirectory());

		Optional<Subprogram> staleHolder = scope.isCommon()
				? subprogramRepository.findSoftDeletedByCommonKey(kind, request.name(), request.version())
				: subprogramRepository.findSoftDeletedByBoardKey(kind, scope.boardId(), request.name(), request.version());
		if (staleHolder.isPresent()) {
			Subprogram existing = staleHolder.get();
			BoardScope sc = existing.isCommonScope() ? BoardScope.COMMON : BoardScope.ofBoard(existing.getBoardId());
			throw new DuplicateSubprogramVersionException(existing.getKind(), sc, existing.getName(), existing.getVersion());
		}

		targetDirectoryPolicyService.prepareForExistingDirectoryRegistration(targetDir);

		subprogramRepository.findFirstByTreeRootPathAndIsDeletedFalse(targetDir.toString())
				.ifPresent(existing -> {
					throw new SubprogramPathConflictException(existing.getTreeRootPath());
				});

		ManifestSummary manifest = bundleManifestService.compute(targetDir);

		// MK3-1 — ghost 후보 사전 필터링.
		List<Subprogram> hashCandidates = subprogramRepository.findHashConflictCandidates(
						kind, scope.isCommon() ? null : scope.boardId(), manifest.manifestHash())
				.stream()
				.filter(c -> !com.example.serverprovision.global.trash.GhostEvaluator.isGhost(c))
				.toList();
		if (!hashCandidates.isEmpty()) {
			NudgeSession session = nudgeRegistry.register(
					NudgeResourceType.SUBPROGRAM,
					scope.isCommon() ? null : scope.boardId(),
					hashCandidates.stream().map(Subprogram::getId).toList(),
					new ContentNudgePayload(
							request.name(),
							request.version(),
							manifest.manifestHash(),
							targetDir.toString(),
							Map.of(
									"kind", kind.name(),
									"scopeCommon", String.valueOf(scope.isCommon()),
									"boardId", scope.isCommon() ? "" : String.valueOf(scope.boardId()),
									"fileCount", String.valueOf(manifest.fileCount()),
									"totalBytes", String.valueOf(manifest.totalBytes()),
									"description", request.description() != null ? request.description() : ""
							)
					)
			);
			List<NudgeConflictEntry> entries = hashCandidates.stream()
					.map(s -> new NudgeConflictEntry(
							s.getId(),
							LifecycleStage.of(s.isDeprecated(), s.isDeleted()),
							s.getManifestHash(),
							s.getName(),
							s.getVersion(),
							Instant.now()
					))
					.toList();
			log.info(
					"[registerExistingSubprogram] nudge required : kind={}, scope={}, version={}, candidates={}",
					kind, scope.pathToken(), request.version(), hashCandidates.size()
			);
			throw new SubprogramNudgeRequiredException(session, entries);
		}

		Subprogram saved = subprogramRepository.save(Subprogram.builder()
															 .kind(kind)
															 .boardModel(parent)
															 .name(request.name())
															 .version(request.version())
															 .treeRootPath(targetDir.toString())
															 .entrypointRelativePath(null)
															 .manifestHash(manifest.manifestHash())
															 .markerSignature(null)
															 .lastIntegrityStatus(IntegrityStatus.NOT_VERIFIED)
															 .fileCount(manifest.fileCount())
															 .totalBytes(manifest.totalBytes())
															 .description(request.description())
															 .isEnabled(true)
															 .isDeleted(false)
															 .build());

		MarkerContent unsigned = new MarkerContent(
				ResourceType.SUBPROGRAM.name(),
				saved.getId(),
				buildMarkerAttributes(kind, scope, request.name(), request.version()),
				Instant.now(),
				manifest.manifestHash(),
				null
		);
		String signature = provisionMarkerService.computeSignature(unsigned);
		saved.reissueMarker(manifest.manifestHash(), signature);
		provisionMarkerService.write(targetDir, MarkerLayout.IN_TREE, unsigned.withSignature(signature));

		log.info(
				"[registerExistingSubprogram] 등록 완료. id={}, kind={}, scope={}, name={}, version={}",
				saved.getId(), kind, scope.pathToken(), request.name(), request.version()
		);
		return saved.getId();
	}

	// ==== MK2 — SubprogramNudgeService 가 사용할 helper =================

	/**
	 * MK2 — nudge proceed/replace 후 임시 트리를 ACTIVE 자원으로 영속화.
	 */
	@Transactional
	public Long persistFromNudge(ContentNudgePayload payload) {
		SubprogramKind kind = SubprogramKind.valueOf(payload.attributes().get("kind"));
		boolean common = Boolean.parseBoolean(payload.attributes().getOrDefault("scopeCommon", "false"));
		BoardModel parent = null;
		if (!common) {
			Long boardId = Long.parseLong(payload.attributes().get("boardId"));
			parent = requireActiveBoard(boardId);
		}
		Path targetDir = pathPolicyService.assertWritablePath(payload.tempFilePath());
		int fileCount = Integer.parseInt(payload.attributes().getOrDefault("fileCount", "0"));
		long totalBytes = Long.parseLong(payload.attributes().getOrDefault("totalBytes", "0"));
		String description = payload.attributes().getOrDefault("description", "");

		Subprogram saved = subprogramRepository.save(Subprogram.builder()
															 .kind(kind)
															 .boardModel(parent)
															 .name(payload.name())
															 .version(payload.version())
															 .treeRootPath(targetDir.toString())
															 .entrypointRelativePath(null)
															 .manifestHash(payload.manifestHash())
															 .markerSignature(null)
															 .lastIntegrityStatus(IntegrityStatus.NOT_VERIFIED)
															 .fileCount(fileCount)
															 .totalBytes(totalBytes)
															 .description(description)
															 .isEnabled(true)
															 .isDeleted(false)
															 .build());

		BoardScope scope = common ? BoardScope.COMMON : BoardScope.ofBoard(parent.getId());
		MarkerContent unsigned = new MarkerContent(
				ResourceType.SUBPROGRAM.name(),
				saved.getId(),
				buildMarkerAttributes(kind, scope, payload.name(), payload.version()),
				Instant.now(),
				payload.manifestHash(),
				null
		);
		String signature = provisionMarkerService.computeSignature(unsigned);
		saved.reissueMarker(payload.manifestHash(), signature);
		provisionMarkerService.write(targetDir, MarkerLayout.IN_TREE, unsigned.withSignature(signature));

		log.info("[persistFromNudge.subprogram] id={}, kind={}, scope={}", saved.getId(), kind, scope.pathToken());
		return saved.getId();
	}

	/**
	 * MK2 — nudge cancel 시 임시 트리 cleanup.
	 */
	@Transactional
	public void purgeNudgeTempTree(Path tempPath) {
		bundleTreeCleanupService.purgeExistingTree(tempPath, "purgeNudgeTempTree.subprogram");
	}

	private Map<String, String> buildMarkerAttributes(SubprogramKind kind, BoardScope scope, String name, String version) {
		Map<String, String> attrs = new LinkedHashMap<>();
		attrs.put("kind", kind.pathToken());
		attrs.put("name", name);
		attrs.put("version", version);
		if (scope.isCommon()) {
			attrs.put("scope", "common");
		} else {
			attrs.put("scope", "board");
			attrs.put("boardId", String.valueOf(scope.boardId()));
		}
		return attrs;
	}

	/* ─────────────────────────── 편집 / 토글 / 삭제 / 복구 ─────────────────────────── */

	@Transactional
	public void update(Long subprogramId, SubprogramUpdateRequest request) {
		Subprogram sp = requireLive(subprogramId);
		// version 변경 시 (kind, scope, name, version) 중복 재검사
		if (!sp.getVersion().equals(request.version()) || !sp.getName().equals(request.name())) {
			Optional<Subprogram> conflict = sp.isCommonScope()
					? subprogramRepository.findActiveByCommonKey(sp.getKind(), request.name(), request.version())
					: subprogramRepository.findActiveByBoardKey(sp.getKind(), sp.getBoardId(), request.name(), request.version());
			if (conflict.isPresent() && !conflict.get().getId().equals(subprogramId)) {
				BoardScope scope = sp.isCommonScope() ? BoardScope.COMMON : BoardScope.ofBoard(sp.getBoardId());
				throw new DuplicateSubprogramVersionException(sp.getKind(), scope, request.name(), request.version());
			}
		}
		// S3 — entrypoint 입력 검증 (절대경로 / .. / 트리 밖 차단).
		// C2 — 빈 입력(null/blank) 은 "값 유지" 로 해석. 명시적 제거는 별도 액션이 없으므로 wipe 방지.
		// 의미 있는 입력이 들어왔을 때만 정책 검증 후 교체.
		String requestedEntrypoint = request.entrypointRelativePath();
		boolean entrypointProvided = requestedEntrypoint != null && !requestedEntrypoint.isBlank();
		String nextEntrypoint = entrypointProvided
				? entrypointPolicyService.validateAndNormalize(
				Path.of(sp.getTreeRootPath()), requestedEntrypoint)
				: sp.getEntrypointRelativePath();
		sp.update(request.name(), request.version(), request.description(), nextEntrypoint);
	}

	@Transactional
	public void toggleEnabled(Long subprogramId) {
		requireLive(subprogramId).toggleEnabled();
	}

	/**
	 * MK3 — soft-delete Subprogram. 도메인 가드 후 공통 trash 흐름 위임. MK3-2 사전조건 추가.
	 */
	@Transactional
	public void softDelete(Long subprogramId) {
		Subprogram sp = requireLive(subprogramId);
		// MK3-2 (DCM3-2.1) — Files.exists 사전조건. flag false 면 통과.
		softDeleteIntentService.checkPrecondition(sp);
		trashLifecycleService.softDeleteToTrash(sp);
	}

	/**
	 * MK3-2 (DCM3-2.3 ~ 2.5) — softDelete reject modal 의 두 번째 호출 진입점.
	 */
	@Transactional
	public void softDeleteWithIntent(
			Long subprogramId,
			com.example.serverprovision.global.lifecycle.DeleteAction action
	) {
		switch (action) {
			case CORRECT_PATH_THEN_DELETE -> softDeleteIntentService.reconcileThenDelete(
					com.example.serverprovision.global.marker.ResourceType.SUBPROGRAM, subprogramId,
					() -> {
						Subprogram refreshed = requireLive(subprogramId);
						trashLifecycleService.softDeleteToTrash(refreshed);
					}
			);
			case FORCED_CLEAR -> softDeleteIntentService.forcedClear(
					com.example.serverprovision.global.marker.ResourceType.SUBPROGRAM, subprogramId);
		}
	}

	/**
	 * MK3 — restore Subprogram. 도메인 가드 (활성 자원 충돌) + 공통 흐름. attributes 는 Subprogram 도메인 메타.
	 */
	@Transactional
	public void restore(Long subprogramId) {
		Subprogram sp = subprogramRepository.findById(subprogramId)
				.orElseThrow(() -> new SubprogramNotFoundException(subprogramId));
		Optional<Subprogram> active = sp.isCommonScope()
				? subprogramRepository.findActiveByCommonKey(sp.getKind(), sp.getName(), sp.getVersion())
				: subprogramRepository.findActiveByBoardKey(sp.getKind(), sp.getBoardId(), sp.getName(), sp.getVersion());
		if (active.isPresent()) {
			BoardScope scope = sp.isCommonScope() ? BoardScope.COMMON : BoardScope.ofBoard(sp.getBoardId());
			throw new DuplicateSubprogramVersionException(sp.getKind(), scope, sp.getName(), sp.getVersion());
		}
		trashLifecycleService.restoreFromTrash(
				sp, spEntity -> {
					java.util.Map<String, String> attrs = new java.util.HashMap<>();
					attrs.put("kind", spEntity.getKind().name());
					attrs.put("name", spEntity.getName());
					attrs.put("version", spEntity.getVersion());
					if (spEntity.getBoardId() != null) {
						attrs.put("boardId", String.valueOf(spEntity.getBoardId()));
					}
					return attrs;
				}
		);
	}

	/* ─────────────────────────── MK2 — Deprecate / Undeprecate / Purge ─────────────────────────── */

	/**
	 * MK2 — Active → Deprecated 전이. SoftDeleted 자원에는 호출 불가 (LifecycleEntity 가드).
	 */
	@Transactional
	public void deprecate(Long subprogramId) {
		Subprogram sp = subprogramRepository.findById(subprogramId)
				.orElseThrow(() -> new SubprogramNotFoundException(subprogramId));
		sp.deprecate();
	}

	/**
	 * MK2 — Deprecated → Active 전이.
	 */
	@Transactional
	public void undeprecate(Long subprogramId) {
		Subprogram sp = subprogramRepository.findById(subprogramId)
				.orElseThrow(() -> new SubprogramNotFoundException(subprogramId));
		sp.undeprecate();
	}

	/**
	 * MK2 — 영구 삭제 (hard-delete). SoftDeleted 자원에 한해 허용. 디스크 트리 + DB row 모두 제거.
	 *
	 * <p>nudge REPLACE 결정의 사후 호출 경로이기도 하다 (별도 트랜잭션). 트리 cleanup 은 Service 책임이며
	 * cleanup 실패 시 트랜잭션 rollback 으로 row 가 보존되어 재시도 가능.</p>
	 */
	@Transactional
	public void purge(Long subprogramId) {
		Subprogram sp = subprogramRepository.findById(subprogramId)
				.orElseThrow(() -> new SubprogramNotFoundException(subprogramId));
		if (!sp.isDeleted()) {
			throw new IllegalSubprogramStateException(
					"활성 상태에서는 영구 삭제할 수 없습니다. 먼저 삭제(soft-delete)를 수행하세요. id=" + subprogramId);
		}
		bundleTreeCleanupService.purgeExistingTree(Path.of(sp.getTreeRootPath()), "purgeSubprogram");
		subprogramRepository.delete(sp);
		log.info(
				"[purge] Subprogram 영구 삭제. id={}, kind={}, name={}, version={}",
				sp.getId(), sp.getKind(), sp.getName(), sp.getVersion()
		);
	}

	/**
	 * S5-2-2 — Subprogram typed-name 검증 후 영구 삭제.
	 * 합성식 : {@code sp.name}.
	 */
	@Transactional
	public void purgeWithTypedNameCheck(Long subprogramId, String typedName) {
		Subprogram sp = subprogramRepository.findById(subprogramId)
				.orElseThrow(() -> new SubprogramNotFoundException(subprogramId));
		if (!sp.isDeleted()) {
			throw new IllegalSubprogramStateException(
					"활성 상태에서는 영구 삭제할 수 없습니다. 먼저 삭제(soft-delete)를 수행하세요. id=" + subprogramId);
		}
		String expected = sp.displayName();
		if (!expected.equals(typedName)) {
			throw new TypedNameMismatchException(expected, typedName);
		}
		purge(subprogramId);
	}

	/* ─────────────────────────── 무결성 검증 ─────────────────────────── */

	public IntegrityStatus verifyIntegrity(Long subprogramId) {
		Subprogram sp = requireLive(subprogramId);
		Path treeRoot = Path.of(sp.getTreeRootPath());
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
	public IntegrityStatus verifyAndRecordIntegrity(Long subprogramId) {
		IntegrityStatus status = verifyIntegrity(subprogramId);
		requireLive(subprogramId).recordIntegritySnapshot(status, Instant.now());
		return status;
	}

	/* ─────────────────────────── helpers ─────────────────────────── */

	private BoardModel requireActiveBoard(Long boardId) {
		return boardModelRepository.findByIdAndIsDeletedFalse(boardId)
				.orElseThrow(() -> new BoardModelNotFoundException(boardId));
	}

	private Subprogram requireLive(Long subprogramId) {
		Subprogram sp = subprogramRepository.findById(subprogramId)
				.orElseThrow(() -> new SubprogramNotFoundException(subprogramId));
		if (sp.isDeleted()) {
			throw new IllegalSubprogramStateException("삭제된 자원에는 수행할 수 없는 작업입니다. id=" + subprogramId);
		}
		return sp;
	}

	private List<SubprogramResponse> toResponses(List<Subprogram> entities) {
		return entities.stream()
				.sorted(Comparator.comparing(Subprogram::getName).thenComparing(Comparator.comparing(Subprogram::getVersion).reversed()))
				.map(SubprogramService::toResponse)
				.toList();
	}

	private static SubprogramResponse toResponse(Subprogram entity) {
		return new SubprogramResponse(
				entity.getId(),
				entity.getKind(),
				entity.getKind().getDisplayName(),
				entity.getBoardId(),
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
				entity.currentStage()
		);
	}
}
