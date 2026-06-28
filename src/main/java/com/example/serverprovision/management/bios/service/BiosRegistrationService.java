package com.example.serverprovision.management.bios.service;

import com.example.serverprovision.global.lifecycle.LifecycleStage;
import com.example.serverprovision.global.security.PathPolicyService;
import com.example.serverprovision.global.trash.GhostEvaluator;
import com.example.serverprovision.management.bios.dto.request.BiosCreateRequest;
import com.example.serverprovision.management.bios.dto.request.BiosRegisterExistingRequest;
import com.example.serverprovision.management.bios.entity.BoardBIOS;
import com.example.serverprovision.management.bios.enums.BiosUploadMode;
import com.example.serverprovision.management.bios.exception.BiosNudgeRequiredException;
import com.example.serverprovision.management.bios.exception.DuplicateBiosVersionException;
import com.example.serverprovision.management.bios.repository.BiosRepository;
import com.example.serverprovision.management.board.entity.BoardModel;
import com.example.serverprovision.management.board.repository.BoardModelRepository;
import com.example.serverprovision.management.bios.service.BundleManifestService.ManifestSummary;
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
 * R4-3 — BIOS 등록 흐름 전담 service. R4-3 이전 {@code BiosService} 에 잔류하던 등록 책임
 * (업로드 / 기존 트리 claim / nudge confirm 영속화 / nudge cancel cleanup)을 본 service 로 응집한다.
 *
 * <p>책임 4 진입점 :</p>
 * <ul>
 *   <li>{@link #addBios} — 업로드 본체. 검증 → 트리 전개 → manifest 계산 → 해시 충돌 nudge → 2-phase save + marker.</li>
 *   <li>{@link #registerExisting} — 업로드 없이 이미 있는 트리를 claim. {@code addBios} 와 검증/nudge/save 흐름 공유(추출만 생략).</li>
 *   <li>{@link #persistFromNudge} — nudge proceed/replace 후 임시 트리를 ACTIVE 자원으로 영속화.</li>
 *   <li>{@link #purgeNudgeTempTree} — nudge cancel 시 임시 트리 정리.</li>
 * </ul>
 *
 * <p>중복 제거(불가침) — addBios/registerExisting 에 복붙돼 있던 nudge 발급 블록은 {@link #issueHashNudge},
 * 3 경로에 반복되던 entity save + marker write 골격은 {@link #persistBundle} 단일 helper 로 모은다.</p>
 *
 * <p>의존 그래프 — 단방향. marker 발급은 {@link BiosMarkerWriter}(기존)에 위임한다. lifecycle/scanner/verifier 를
 * 역참조하지 않는다(순환 토대 깨끗).</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BiosRegistrationService {

	private final BiosRepository biosRepository;
	private final BoardModelRepository boardModelRepository;
	private final BundleExtractionService bundleExtractionService;
	private final BundleEntrypointDetector bundleEntrypointDetector;
	private final BundleManifestService bundleManifestService;
	private final BiosMarkerWriter biosMarkerWriter;
	private final TargetDirectoryPolicyService targetDirectoryPolicyService;
	private final BundleTreeCleanupService bundleTreeCleanupService;
	private final PathPolicyService pathPolicyService;
	private final NudgeRegistry nudgeRegistry;

	// ==== 업로드 등록 ==================================================

	@Transactional
	public Long addBios(
			Long boardId,
			BiosCreateRequest request,
			BiosUploadMode uploadMode,
			MultipartFile[] folderFiles,
			MultipartFile zipFile,
			MultipartFile singleFile
	) {
		BoardModel parent = BiosGuards.requireActiveBoard(boardModelRepository, boardId);

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
			issueHashNudge(boardId, request.name(), request.version(), request.description(),
					targetDir, entrypoint, manifest, "addBios");

			// 7) 2-phase save + marker 발급
			BoardBIOS saved = persistBundle(parent, request.name(), request.version(), request.description(),
					targetDir, entrypoint, manifest.manifestHash(), manifest.fileCount(), manifest.totalBytes());

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
		BoardModel parent = BiosGuards.requireActiveBoard(boardModelRepository, boardId);

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

		issueHashNudge(boardId, request.name(), request.version(), request.description(),
				targetDir, entrypoint, manifest, "registerExistingBios");

		BoardBIOS saved = persistBundle(parent, request.name(), request.version(), request.description(),
				targetDir, entrypoint, manifest.manifestHash(), manifest.fileCount(), manifest.totalBytes());

		log.info(
				"[registerExistingBios] 등록 완료. biosId={}, boardId={}, version={}, fileCount={}, totalBytes={}",
				saved.getId(), boardId, request.version(), manifest.fileCount(), manifest.totalBytes()
		);
		return saved.getId();
	}

	/**
	 * MK2 — nudge proceed/replace 후 임시 트리를 ACTIVE 자원으로 영속화한다. 단계 B 의 {@link #addBios} 흐름 중
	 * entity save + marker write 부분만 재사용. PendingPayload 는 {@link BiosNudgeService} 가 nudge 세션에서 가져와 전달한다.
	 */
	@Transactional
	public Long persistFromNudge(Long boardId, ContentNudgePayload payload) {
		BoardModel parent = BiosGuards.requireActiveBoard(boardModelRepository, boardId);
		// 활성 (board, version) 재검증 — replace 트랜잭션이 외부에서 별도 commit 됐을 수 있으므로.
		if (biosRepository.existsByBoardModel_IdAndVersionAndIsDeletedFalse(boardId, payload.version())) {
			throw new DuplicateBiosVersionException(boardId, payload.version());
		}
		Path targetDir = pathPolicyService.assertWritablePath(payload.tempFilePath());
		String entrypoint = payload.attributes().getOrDefault("entrypointRelativePath", "");
		int fileCount = Integer.parseInt(payload.attributes().getOrDefault("fileCount", "0"));
		long totalBytes = Long.parseLong(payload.attributes().getOrDefault("totalBytes", "0"));
		String rawDescription = payload.attributes().getOrDefault("description", "");
		String description = rawDescription.isEmpty() ? null : rawDescription;

		BoardBIOS saved = persistBundle(parent, payload.name(), payload.version(), description,
				targetDir, entrypoint, payload.manifestHash(), fileCount, totalBytes);

		log.info("[persistFromNudge] biosId={}, boardId={}, version={}", saved.getId(), boardId, payload.version());
		return saved.getId();
	}

	/**
	 * MK2 — nudge cancel 시 임시 트리 정리. allowed-roots 가드는 cleanup 내부에서 수행.
	 */
	public void purgeNudgeTempTree(Path tempPath) {
		bundleTreeCleanupService.purgeExistingTree(tempPath, "nudgeCancel");
	}

	// ==== private helpers (복붙 dedup) =================================

	/**
	 * MK2 단계 B — 해시 충돌 후보 (SoftDeleted / Deprecated) 탐지 시 nudge 세션 발급 + {@link BiosNudgeRequiredException}.
	 * 임시 트리는 호출자 targetDir 에 그대로 남겨두고 사용자 결정(proceed / replace / cancel) 대기.
	 * MK3-1 — ghost (DB-only soft-deleted, FS 부재) 후보는 사전 필터링.
	 *
	 * <p>addBios / registerExisting 에 복붙돼 있던 블록을 단일화. {@code logContext} 만 호출처별로 다르다.</p>
	 */
	private void issueHashNudge(
			Long boardId, String name, String version, String description,
			Path targetDir, String entrypoint, ManifestSummary manifest, String logContext
	) {
		List<BoardBIOS> hashCandidates = biosRepository.findHashConflictCandidates(boardId, manifest.manifestHash())
				.stream()
				.filter(c -> !GhostEvaluator.isGhost(c))
				.toList();
		if (hashCandidates.isEmpty()) {
			return;
		}
		NudgeSession session = nudgeRegistry.register(
				NudgeResourceType.BIOS,
				boardId,
				hashCandidates.stream().map(BoardBIOS::getId).toList(),
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
		throw new BiosNudgeRequiredException(session, entries);
	}

	/**
	 * 2-phase save — 엔티티 선 저장(signature=null) → biosId 획득 → {@link BiosMarkerWriter} 가 biosId 포함 marker 서명·기록.
	 * addBios / registerExisting / persistFromNudge 3 경로의 동일 골격을 단일화.
	 */
	private BoardBIOS persistBundle(
			BoardModel parent, String name, String version, String description,
			Path targetDir, String entrypoint, String manifestHash, int fileCount, long totalBytes
	) {
		BoardBIOS saved = biosRepository.save(BoardBIOS.builder()
													  .boardModel(parent)
													  .name(name)
													  .version(version)
													  .treeRootPath(targetDir.toString())
													  .entrypointRelativePath(entrypoint)
													  .manifestHash(manifestHash)
													  .markerSignature(null)
													  .fileCount(fileCount)
													  .totalBytes(totalBytes)
													  .description(description)
													  .isEnabled(true)
													  .isDeleted(false)
													  .build());

		biosMarkerWriter.writeSignedMarker(
				saved, targetDir, parent.getId(), version, entrypoint, manifestHash);
		return saved;
	}
}
