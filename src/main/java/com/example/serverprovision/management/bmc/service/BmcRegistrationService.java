package com.example.serverprovision.management.bmc.service;

import com.example.serverprovision.global.lifecycle.LifecycleStage;
import com.example.serverprovision.global.security.PathPolicyService;
import com.example.serverprovision.global.trash.GhostEvaluator;
import com.example.serverprovision.management.bios.service.BundleEntrypointDetector;
import com.example.serverprovision.management.bios.service.BundleExtractionService;
import com.example.serverprovision.management.bios.service.BundleManifestService;
import com.example.serverprovision.management.bios.service.BundleManifestService.ManifestSummary;
import com.example.serverprovision.management.bmc.dto.request.BmcCreateRequest;
import com.example.serverprovision.management.bmc.dto.request.BmcRegisterExistingRequest;
import com.example.serverprovision.management.bmc.entity.BoardBMC;
import com.example.serverprovision.management.bmc.enums.BmcUploadMode;
import com.example.serverprovision.management.bmc.exception.BmcNudgeRequiredException;
import com.example.serverprovision.management.bmc.exception.DuplicateBmcVersionException;
import com.example.serverprovision.management.bmc.repository.BmcRepository;
import com.example.serverprovision.management.board.entity.BoardModel;
import com.example.serverprovision.management.board.repository.BoardModelRepository;
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
import java.util.List;
import java.util.Map;

/**
 * R5-3 — BMC 등록 흐름 전담 service. R5-3 이전 {@code BmcService} 에 잔류하던 등록 책임
 * (업로드 / 기존 트리 claim / nudge confirm 영속화 / nudge cancel cleanup)을 본 service 로 응집한다.
 * ({@code BiosRegistrationService} 선례 미러.)
 *
 * <p>책임 4 진입점 :</p>
 * <ul>
 *   <li>{@link #addBmc} — 업로드 본체. 검증 → 트리 전개 → manifest 계산 → 해시 충돌 nudge → 2-phase save + marker.</li>
 *   <li>{@link #registerExisting} — 업로드 없이 이미 있는 트리를 claim. {@code addBmc} 와 검증/nudge/save 흐름 공유(추출만 생략).</li>
 *   <li>{@link #persistFromNudge} — nudge proceed/replace 후 임시 트리를 ACTIVE 자원으로 영속화.</li>
 *   <li>{@link #purgeNudgeTempTree} — nudge cancel 시 임시 트리 정리.</li>
 * </ul>
 *
 * <p>중복 제거(불가침) — addBmc/registerExisting 에 복붙돼 있던 nudge 발급 블록은 {@link #issueHashNudge},
 * 3 경로에 반복되던 entity save + marker write 골격은 {@link #persistBundle} 단일 helper 로 모은다. marker 4-step
 * 은 {@link BmcMarkerWriter}(신규)에 위임한다.</p>
 *
 * <p>의존 그래프 — 단방향. lifecycle/scanner/verifier 를 역참조하지 않는다(순환 토대 깨끗).
 * REPLACE 시 purge 는 {@link BmcLifecycleService} 에 위임한다.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BmcRegistrationService {

	private final BmcRepository bmcRepository;
	private final BoardModelRepository boardModelRepository;
	private final BundleExtractionService bundleExtractionService;
	private final BundleEntrypointDetector bundleEntrypointDetector;
	private final BundleManifestService bundleManifestService;
	private final BmcMarkerWriter bmcMarkerWriter;
	private final TargetDirectoryPolicyService targetDirectoryPolicyService;
	private final BundleTreeCleanupService bundleTreeCleanupService;
	private final PathPolicyService pathPolicyService;
	private final NudgeRegistry nudgeRegistry;

	// ==== 업로드 등록 ==================================================

	@Transactional
	public Long addBmc(
			Long boardId,
			BmcCreateRequest request,
			BmcUploadMode uploadMode,
			MultipartFile[] folderFiles,
			MultipartFile zipFile,
			MultipartFile singleFile
	) {
		BoardModel parent = BmcGuards.requireActiveBoard(boardModelRepository, boardId);

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
			issueHashNudge(boardId, request.name(), request.version(), request.description(),
					targetDir, entrypoint, manifest, "addBmc");

			BoardBMC saved = persistBundle(parent, request.name(), request.version(), request.description(),
					targetDir, entrypoint, manifest.manifestHash(), manifest.fileCount(), manifest.totalBytes());

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
		BoardModel parent = BmcGuards.requireActiveBoard(boardModelRepository, boardId);

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

		issueHashNudge(boardId, request.name(), request.version(), request.description(),
				targetDir, entrypoint, manifest, "registerExistingBmc");

		BoardBMC saved = persistBundle(parent, request.name(), request.version(), request.description(),
				targetDir, entrypoint, manifest.manifestHash(), manifest.fileCount(), manifest.totalBytes());

		log.info(
				"[registerExistingBmc] 등록 완료. bmcId={}, boardId={}, version={}, fileCount={}, totalBytes={}",
				saved.getId(), boardId, request.version(), manifest.fileCount(), manifest.totalBytes()
		);
		return saved.getId();
	}

	/**
	 * MK2 — nudge proceed/replace 후 임시 트리를 ACTIVE 자원으로 영속화. 단계 B 의 {@link #addBmc} 흐름 중
	 * entity save + marker write 부분만 재사용. payload 는 {@link BmcNudgeService} 가 nudge 세션에서 가져와 전달한다.
	 */
	@Transactional
	public Long persistFromNudge(Long boardId, ContentNudgePayload payload) {
		BoardModel parent = BmcGuards.requireActiveBoard(boardModelRepository, boardId);
		if (bmcRepository.existsByBoardModel_IdAndVersionAndIsDeletedFalse(boardId, payload.version())) {
			throw new DuplicateBmcVersionException(boardId, payload.version());
		}
		Path targetDir = pathPolicyService.assertWritablePath(payload.tempFilePath());
		String entrypoint = payload.attributes().getOrDefault("entrypointRelativePath", "");
		int fileCount = Integer.parseInt(payload.attributes().getOrDefault("fileCount", "0"));
		long totalBytes = Long.parseLong(payload.attributes().getOrDefault("totalBytes", "0"));
		String description = payload.attributes().getOrDefault("description", "");

		BoardBMC saved = persistBundle(parent, payload.name(), payload.version(), description,
				targetDir, entrypoint, payload.manifestHash(), fileCount, totalBytes);

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

	// ==== private helpers (복붙 dedup) =================================

	/**
	 * MK2 단계 B — 해시 충돌 후보 (SoftDeleted / Deprecated) 탐지 시 nudge 세션 발급 + {@link BmcNudgeRequiredException}.
	 * 임시 트리는 호출자 targetDir 에 그대로 남겨두고 사용자 결정(proceed / replace / cancel) 대기.
	 * MK3-1 — ghost (DB-only soft-deleted, FS 부재) 후보는 사전 필터링.
	 *
	 * <p>addBmc / registerExisting 에 복붙돼 있던 블록을 단일화. {@code logContext} 만 호출처별로 다르다.</p>
	 */
	private void issueHashNudge(
			Long boardId, String name, String version, String description,
			Path targetDir, String entrypoint, ManifestSummary manifest, String logContext
	) {
		List<BoardBMC> hashCandidates = bmcRepository.findHashConflictCandidates(boardId, manifest.manifestHash())
				.stream()
				.filter(c -> !GhostEvaluator.isGhost(c))
				.toList();
		if (hashCandidates.isEmpty()) {
			return;
		}
		NudgeSession session = nudgeRegistry.register(
				NudgeResourceType.BMC,
				boardId,
				hashCandidates.stream().map(BoardBMC::getId).toList(),
				new ContentNudgePayload(
						name,
						version,
						manifest.manifestHash(),
						targetDir.toString(),
						Map.of(
								"entrypointRelativePath", entrypoint,
								"fileCount", String.valueOf(manifest.fileCount()),
								"totalBytes", String.valueOf(manifest.totalBytes()),
								"description", description != null ? description : ""
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
				"[{}] nudge required : boardId={}, version={}, candidates={}",
				logContext, boardId, version, hashCandidates.size()
		);
		throw new BmcNudgeRequiredException(session, entries);
	}

	/**
	 * 2-phase save — 엔티티 선 저장(signature=null) → bmcId 획득 → {@link BmcMarkerWriter} 가 bmcId 포함 marker 서명·기록.
	 * addBmc / registerExisting / persistFromNudge 3 경로의 동일 골격을 단일화.
	 */
	private BoardBMC persistBundle(
			BoardModel parent, String name, String version, String description,
			Path targetDir, String entrypoint, String manifestHash, int fileCount, long totalBytes
	) {
		BoardBMC saved = bmcRepository.save(BoardBMC.builder()
													.boardModel(parent)
													.name(name)
													.version(version)
													.treeRootPath(targetDir.toString())
													.legacyFilePath(targetDir.toString())
													.boardModelIdMirror(parent.getId())
													.entrypointRelativePath(entrypoint)
													.manifestHash(manifestHash)
													.markerSignature(null)
													.fileCount(fileCount)
													.totalBytes(totalBytes)
													.description(description)
													.isEnabled(true)
													.isDeleted(false)
													.build());

		bmcMarkerWriter.writeSignedMarker(
				saved, targetDir, parent.getId(), version, entrypoint, manifestHash);
		return saved;
	}
}
