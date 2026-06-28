package com.example.serverprovision.management.subprogram.service;

import com.example.serverprovision.global.lifecycle.LifecycleStage;
import com.example.serverprovision.global.marker.IntegrityStatus;
import com.example.serverprovision.global.security.PathPolicyService;
import com.example.serverprovision.global.trash.GhostEvaluator;
import com.example.serverprovision.management.bios.service.BundleExtractionService;
import com.example.serverprovision.management.bios.service.BundleManifestService;
import com.example.serverprovision.management.bios.service.BundleManifestService.ManifestSummary;
import com.example.serverprovision.management.board.entity.BoardModel;
import com.example.serverprovision.management.board.repository.BoardModelRepository;
import com.example.serverprovision.management.common.filesystem.service.BundleTreeCleanupService;
import com.example.serverprovision.management.common.filesystem.service.TargetDirectoryPolicyService;
import com.example.serverprovision.management.common.nudge.ContentNudgePayload;
import com.example.serverprovision.management.common.nudge.NudgeRegistry;
import com.example.serverprovision.management.common.nudge.NudgeResourceType;
import com.example.serverprovision.management.common.nudge.NudgeSession;
import com.example.serverprovision.management.common.nudge.dto.NudgeConflictEntry;
import com.example.serverprovision.management.subprogram.dto.request.SubprogramCreateRequest;
import com.example.serverprovision.management.subprogram.dto.request.SubprogramRegisterExistingRequest;
import com.example.serverprovision.management.subprogram.entity.Subprogram;
import com.example.serverprovision.management.subprogram.enums.SubprogramKind;
import com.example.serverprovision.management.subprogram.enums.SubprogramUploadMode;
import com.example.serverprovision.management.subprogram.exception.DuplicateSubprogramVersionException;
import com.example.serverprovision.management.subprogram.exception.SubprogramNudgeRequiredException;
import com.example.serverprovision.management.subprogram.exception.SubprogramPathConflictException;
import com.example.serverprovision.management.subprogram.repository.SubprogramRepository;
import com.example.serverprovision.management.subprogram.vo.BoardScope;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * R6-3 — Subprogram 등록 흐름 전담 service. R6-3 이전 {@code SubprogramService} 에 잔류하던 등록 책임
 * (업로드 / 기존 트리 claim / nudge confirm 영속화 / nudge cancel cleanup)을 본 service 로 응집한다
 * ({@code BmcRegistrationService} 선례 미러).
 *
 * <p>책임 4 진입점 :</p>
 * <ul>
 *   <li>{@link #addSubprogram} — 업로드 본체. 검증 → 트리 전개 → manifest 계산 → 해시 충돌 nudge → 2-phase save + marker.</li>
 *   <li>{@link #registerExisting} — 업로드 없이 이미 있는 트리를 claim. {@code addSubprogram} 와 검증/nudge/save 흐름 공유(추출만 생략).</li>
 *   <li>{@link #persistFromNudge} — nudge proceed/replace 후 임시 트리를 ACTIVE 자원으로 영속화.</li>
 *   <li>{@link #purgeNudgeTempTree} — nudge cancel 시 임시 트리 정리.</li>
 * </ul>
 *
 * <p>중복 제거(불가침) — addSubprogram/registerExisting 에 복붙돼 있던 활성/soft-deleted 중복키 검사는
 * {@link #assertNoDuplicateKey}, nudge 발급 블록은 {@link #issueHashNudge}, 3 경로에 반복되던 entity save + marker
 * write 골격은 {@link #persistBundle} 단일 helper 로 모은다. marker 4-step 은 {@link SubprogramMarkerWriter}(신규)에
 * 위임한다. 중복키 검사는 공용(common key)·board(board key) 두 축을 모두 보존한다.</p>
 *
 * <p>의존 그래프 — 단방향. lifecycle/scanner/verifier 를 역참조하지 않는다(순환 토대 깨끗).
 * REPLACE 시 purge 는 {@link SubprogramLifecycleService} 에 위임한다.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SubprogramRegistrationService {

	private final SubprogramRepository subprogramRepository;
	private final BoardModelRepository boardModelRepository;
	private final BundleExtractionService bundleExtractionService;
	private final BundleManifestService bundleManifestService;
	private final SubprogramMarkerWriter subprogramMarkerWriter;
	private final TargetDirectoryPolicyService targetDirectoryPolicyService;
	private final BundleTreeCleanupService bundleTreeCleanupService;
	private final PathPolicyService pathPolicyService;
	private final NudgeRegistry nudgeRegistry;

	// ==== 업로드 등록 ==================================================

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
		BoardModel parent = scope.isCommon() ? null : SubprogramGuards.requireActiveBoard(boardModelRepository, scope.boardId());

		// 1) 활성 + soft-deleted 중복키 검사 (공용/board 두 축).
		assertNoDuplicateKey(kind, scope, request.name(), request.version());

		// S3 — allowlist 검증
		Path targetDir = pathPolicyService.assertWritablePath(request.targetDirectory());

		// 2) 디렉토리 정책 (비어있는지, 부모 디렉토리 등)
		targetDirectoryPolicyService.prepareForUpload(targetDir, request.allowCreateDirectory());

		// 3) 트리 루트 활성 점유 검사 (kind 무관, 모든 활성 Subprogram 통틀어서)
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
			issueHashNudge(kind, scope, request.name(), request.version(), request.description(),
					targetDir, manifest, "addSubprogram");

			Subprogram saved = persistBundle(kind, scope, parent, request.name(), request.version(),
					request.description(), targetDir, manifest);

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
		BoardModel parent = scope.isCommon() ? null : SubprogramGuards.requireActiveBoard(boardModelRepository, scope.boardId());

		assertNoDuplicateKey(kind, scope, request.name(), request.version());

		Path targetDir = pathPolicyService.assertWritablePath(request.targetDirectory());
		targetDirectoryPolicyService.prepareForExistingDirectoryRegistration(targetDir);

		subprogramRepository.findFirstByTreeRootPathAndIsDeletedFalse(targetDir.toString())
				.ifPresent(existing -> {
					throw new SubprogramPathConflictException(existing.getTreeRootPath());
				});

		ManifestSummary manifest = bundleManifestService.compute(targetDir);

		issueHashNudge(kind, scope, request.name(), request.version(), request.description(),
				targetDir, manifest, "registerExistingSubprogram");

		Subprogram saved = persistBundle(kind, scope, parent, request.name(), request.version(),
				request.description(), targetDir, manifest);

		log.info(
				"[registerExistingSubprogram] 등록 완료. id={}, kind={}, scope={}, name={}, version={}",
				saved.getId(), kind, scope.pathToken(), request.name(), request.version()
		);
		return saved.getId();
	}

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
			parent = SubprogramGuards.requireActiveBoard(boardModelRepository, boardId);
		}
		Path targetDir = pathPolicyService.assertWritablePath(payload.tempFilePath());
		int fileCount = Integer.parseInt(payload.attributes().getOrDefault("fileCount", "0"));
		long totalBytes = Long.parseLong(payload.attributes().getOrDefault("totalBytes", "0"));
		String description = payload.attributes().getOrDefault("description", "");
		BoardScope scope = common ? BoardScope.COMMON : BoardScope.ofBoard(parent.getId());

		Subprogram saved = persistBundle(kind, scope, parent, payload.name(), payload.version(), description,
				targetDir, payload.manifestHash(), fileCount, totalBytes);

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

	// ==== private helpers (복붙 dedup) =================================

	/**
	 * 활성 + soft-deleted 동일 키 충돌 검사. 공용(common key)·board(board key) 두 축을 모두 보존한다.
	 *
	 * <p>MK2 — soft-deleted 동일 키 자동 hard-delete 폐지. 충돌 후보가 있으면 호출자가 nudge 흐름을 거쳐야 하며,
	 * 본 메서드에 도달하면(= nudge 미경유) 정책 위반이므로 명시적 conflict 로 차단.</p>
	 */
	private void assertNoDuplicateKey(SubprogramKind kind, BoardScope scope, String name, String version) {
		Optional<Subprogram> active = scope.isCommon()
				? subprogramRepository.findActiveByCommonKey(kind, name, version)
				: subprogramRepository.findActiveByBoardKey(kind, scope.boardId(), name, version);
		if (active.isPresent()) {
			throw new DuplicateSubprogramVersionException(kind, scope, name, version);
		}

		Optional<Subprogram> staleHolder = scope.isCommon()
				? subprogramRepository.findSoftDeletedByCommonKey(kind, name, version)
				: subprogramRepository.findSoftDeletedByBoardKey(kind, scope.boardId(), name, version);
		if (staleHolder.isPresent()) {
			Subprogram existing = staleHolder.get();
			BoardScope sc = existing.isCommonScope() ? BoardScope.COMMON : BoardScope.ofBoard(existing.getBoardId());
			throw new DuplicateSubprogramVersionException(existing.getKind(), sc, existing.getName(), existing.getVersion());
		}
	}

	/**
	 * MK2 단계 B — 해시 충돌 후보 (SoftDeleted / Deprecated, 같은 scope) 탐지 시 nudge 세션 발급 +
	 * {@link SubprogramNudgeRequiredException}. 임시 트리는 호출자 targetDir 에 그대로 남겨두고 사용자
	 * 결정(proceed / replace / cancel) 대기. MK3-1 — ghost 후보는 사전 필터링.
	 *
	 * <p>addSubprogram / registerExisting 에 복붙돼 있던 블록을 단일화. {@code logContext} 만 호출처별로 다르다.</p>
	 */
	private void issueHashNudge(
			SubprogramKind kind, BoardScope scope, String name, String version, String description,
			Path targetDir, ManifestSummary manifest, String logContext
	) {
		List<Subprogram> hashCandidates = subprogramRepository.findHashConflictCandidates(
						kind, scope.isCommon() ? null : scope.boardId(), manifest.manifestHash())
				.stream()
				.filter(c -> !GhostEvaluator.isGhost(c))
				.toList();
		if (hashCandidates.isEmpty()) {
			return;
		}
		NudgeSession session = nudgeRegistry.register(
				NudgeResourceType.SUBPROGRAM,
				scope.isCommon() ? null : scope.boardId(),
				hashCandidates.stream().map(Subprogram::getId).toList(),
				new ContentNudgePayload(
						name,
						version,
						manifest.manifestHash(),
						targetDir.toString(),
						Map.of(
								"kind", kind.name(),
								"scopeCommon", String.valueOf(scope.isCommon()),
								"boardId", scope.isCommon() ? "" : String.valueOf(scope.boardId()),
								"fileCount", String.valueOf(manifest.fileCount()),
								"totalBytes", String.valueOf(manifest.totalBytes()),
								"description", description != null ? description : ""
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
				"[{}] nudge required : kind={}, scope={}, version={}, candidates={}",
				logContext, kind, scope.pathToken(), version, hashCandidates.size()
		);
		throw new SubprogramNudgeRequiredException(session, entries);
	}

	/**
	 * 2-phase save — 엔티티 선 저장(signature=null) → subprogramId 획득 → {@link SubprogramMarkerWriter} 가
	 * subprogramId 포함 marker 서명·기록. addSubprogram / registerExisting / persistFromNudge 3 경로의 동일
	 * 골격을 단일화. manifest 가 있는 경로(add/register)와 payload 로 풀어쓴 경로(nudge)를 함께 수용하기 위해
	 * fileCount / totalBytes 를 명시 인자로 받는 오버로드를 둔다.
	 */
	private Subprogram persistBundle(
			SubprogramKind kind, BoardScope scope, BoardModel parent, String name, String version,
			String description, Path targetDir, ManifestSummary manifest
	) {
		return persistBundle(kind, scope, parent, name, version, description, targetDir,
				manifest.manifestHash(), manifest.fileCount(), manifest.totalBytes());
	}

	private Subprogram persistBundle(
			SubprogramKind kind, BoardScope scope, BoardModel parent, String name, String version,
			String description, Path targetDir, String manifestHash, int fileCount, long totalBytes
	) {
		Subprogram saved = subprogramRepository.save(Subprogram.builder()
				.kind(kind)
				.boardModel(parent) // null OK (공용)
				.name(name)
				.version(version)
				.treeRootPath(targetDir.toString())
				.entrypointRelativePath(null)  // MA5-D5 — 등록 시 미입력
				.manifestHash(manifestHash)
				.markerSignature(null)
				.lastIntegrityStatus(IntegrityStatus.NOT_VERIFIED)
				.fileCount(fileCount)
				.totalBytes(totalBytes)
				.description(description)
				.isEnabled(true)
				.isDeleted(false)
				.build());

		subprogramMarkerWriter.writeSignedMarker(
				saved, targetDir, kind, scope, name, version, manifestHash);
		return saved;
	}
}
