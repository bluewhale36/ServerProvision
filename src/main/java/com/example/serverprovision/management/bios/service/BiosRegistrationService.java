package com.example.serverprovision.management.bios.service;

import com.example.serverprovision.global.lifecycle.LifecycleStage;
import com.example.serverprovision.global.marker.service.ProvisionMarkerService;
import com.example.serverprovision.global.security.PathPolicyService;
import com.example.serverprovision.global.trash.GhostEvaluator;
import com.example.serverprovision.management.bios.dto.request.BiosCreateRequest;
import com.example.serverprovision.management.bios.entity.BoardBIOS;
import com.example.serverprovision.management.bios.exception.BiosNudgeRequiredException;
import com.example.serverprovision.management.bios.exception.DuplicateBiosVersionException;
import com.example.serverprovision.management.common.firmware.exception.InvalidFirmwareFileException;
import com.example.serverprovision.management.bios.repository.BiosRepository;
import com.example.serverprovision.management.board.entity.BoardModel;
import com.example.serverprovision.management.board.repository.BoardModelRepository;
import com.example.serverprovision.management.bios.service.BundleManifestService.ManifestSummary;
import com.example.serverprovision.management.common.filesystem.exception.BundleExtractionException;
import com.example.serverprovision.management.common.filesystem.exception.MarkerConflictException;
import com.example.serverprovision.management.common.filesystem.policy.BundleFilePolicy;
import com.example.serverprovision.management.common.filesystem.service.BundleTreeCleanupService;
import com.example.serverprovision.management.common.filesystem.service.TargetDirectoryPolicyService;
import com.example.serverprovision.management.common.nudge.ContentNudgePayload;
import com.example.serverprovision.management.common.nudge.NudgeRegistry;
import com.example.serverprovision.management.common.nudge.NudgeResourceType;
import com.example.serverprovision.management.common.nudge.NudgeSession;
import com.example.serverprovision.management.common.nudge.dto.NudgeConflictEntry;
import com.example.serverprovision.management.common.util.UploadPathResolver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Stream;

/**
 * R4-3 — BIOS 등록 흐름 전담 service.
 *
 * <p>R12-1 — 번들(폴더 · zip) 업로드 방식을 폐지하고 ISO 등록과 동일한 단일 흐름으로 통합했다.
 * {@code firmwarePath} 를 해석해(디렉토리면 업로드 파일명 append) 업로드 파일이 있으면 그 경로에 저장하고,
 * 없으면 그 경로에 이미 존재하는 파일을 자원으로 등록(claim)한다. 진입점 자동 탐지
 * 는 더 이상 하지 않는다 — 파일 자체가 진입점이므로 파일명으로 확정한다(탐지 계열은 R12-2 에서 제거됐다).
 * 저장 모델은 종전과 같은 "파일이 하나뿐인 트리"(부모 디렉토리 = treeRootPath + IN_TREE 마커)다.</p>
 *
 * <p>책임 3 진입점 :</p>
 * <ul>
 *   <li>{@link #addBios} — 등록 본체. 경로 해석 → 금지 파일명 검사 → 업로드 저장 또는 기존 파일 claim →
 *       manifest 계산 → 해시 충돌 nudge → 2-phase save + marker.</li>
 *   <li>{@link #persistFromNudge} — nudge proceed/replace 후 임시 트리를 ACTIVE 자원으로 영속화.</li>
 *   <li>{@link #cleanupNudgeCancelled} — nudge cancel 시 업로드 임시 트리 정리 (claim 은 보존).</li>
 * </ul>
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
	private final BundleManifestService bundleManifestService;
	private final BiosFirmwareFilePolicy biosFirmwareFilePolicy;
	private final BiosMarkerWriter biosMarkerWriter;
	private final TargetDirectoryPolicyService targetDirectoryPolicyService;
	private final BundleTreeCleanupService bundleTreeCleanupService;
	private final PathPolicyService pathPolicyService;
	private final NudgeRegistry nudgeRegistry;

	// ==== 등록 본체 ====================================================

	/**
	 * BIOS 펌웨어 파일 등록. {@code firmwareFile} 이 있으면 해석된 경로에 저장하고,
	 * 없으면 그 경로에 이미 존재하는 파일을 자원으로 등록(claim)한다.
	 */
	@Transactional
	public Long addBios(Long boardId, BiosCreateRequest request, MultipartFile firmwareFile) {
		BoardModel parent = BiosGuards.requireActiveBoard(boardModelRepository, boardId);

		// 1) 활성 (board, version) 중복 검사
		if (biosRepository.existsByBoardModel_IdAndVersionAndIsDeletedFalse(boardId, request.version())) {
			throw new DuplicateBiosVersionException(boardId, request.version());
		}

		// 2) 경로 해석 — 업로드는 디렉토리 추론(끝 슬래시 또는 점 없는 마지막 세그먼트) 후 파일명 append,
		//    업로드 없는 등록(claim)은 경로 자체가 대상 파일이므로 추론하지 않는다.
		boolean hasFile = firmwareFile != null && !firmwareFile.isEmpty();
		String originalFilename = hasFile ? firmwareFile.getOriginalFilename() : null;
		Function<String, RuntimeException> onMissingFilename = path -> new InvalidFirmwareFileException(
				"경로가 '/' 로 끝나면 업로드할 파일이 필요합니다 : " + path, "firmwarePath");
		String resolvedPathString = hasFile
				? UploadPathResolver.resolveForUpload(request.firmwarePath(), originalFilename,
						biosFirmwareFilePolicy.allowedExtensions(parent.getVendor()), onMissingFilename)
				: UploadPathResolver.resolve(request.firmwarePath(), null, onMissingFilename);

		// 3) 파일명 정책 검사 — intent 를 우회한 direct POST 안전망. 업로드 원본명과 최종 저장명 모두 검사.
		//    정책은 vendor 별 strategy (제조사 패키지 관례 기원의 제약을 다른 제조사에 강제하지 않는다).
		biosFirmwareFilePolicy.assertAllowed(parent.getVendor(), originalFilename, "firmwareFile");

		// S3 — allowlist 검증된 절대경로만 사용
		Path resolved = pathPolicyService.assertWritablePath(resolvedPathString);
		String firmwareFileName = resolved.getFileName().toString();
		biosFirmwareFilePolicy.assertPathFileNameAllowed(parent.getVendor(), firmwareFileName);

		Path treeRoot = resolved.getParent();
		if (treeRoot == null) {
			throw new InvalidFirmwareFileException(
					"경로에서 상위 디렉토리를 확인할 수 없습니다 : " + resolvedPathString, "firmwarePath");
		}

		if (hasFile) {
			return registerUploadedFile(parent, request, firmwareFile, treeRoot, firmwareFileName);
		}
		return registerExistingFile(parent, request, resolved, treeRoot, firmwareFileName);
	}

	/**
	 * 업로드 저장 경로 — 대상 디렉토리 검증 후 저장하고 공통 등록 골격으로 잇는다.
	 * 실패 시 전개된 파일을 cleanup 한다 (nudge 분기는 임시 트리를 보존해야 하므로 제외).
	 */
	private Long registerUploadedFile(
			BoardModel parent, BiosCreateRequest request, MultipartFile firmwareFile,
			Path treeRoot, String firmwareFileName
	) {
		// 상위 dir 존재 or allowCreateDirectory, 그리고 자기 자신이 비어있거나 부재
		targetDirectoryPolicyService.prepareForUpload(treeRoot, request.allowCreateDirectory());
		try {
			bundleExtractionService.storeSingleFileAs(firmwareFile, treeRoot, firmwareFileName);
			return completeRegistration(parent, request, treeRoot, firmwareFileName, "addBios", false);
		} catch (BiosNudgeRequiredException nudge) {
			// MK2 — nudge 분기는 임시 트리를 보존해야 confirm 시 ACTIVE 영속화가 가능하다.
			throw nudge;
		} catch (RuntimeException e) {
			bundleTreeCleanupService.cleanupFailedUpload(treeRoot, "purgeExistingTree", "addBios", e);
			throw e;
		}
	}

	/**
	 * 기존 파일 claim 경로 — 파일 실재 · 마커 부재 · 부모 디렉토리 배타를 검증하고 공통 등록 골격으로 잇는다.
	 * 사용자 소유의 기존 파일이므로 실패해도 cleanup 하지 않는다.
	 */
	private Long registerExistingFile(
			BoardModel parent, BiosCreateRequest request,
			Path resolved, Path treeRoot, String firmwareFileName
	) {
		assertClaimableFirmwareFile(resolved, treeRoot, firmwareFileName);
		return completeRegistration(parent, request, treeRoot, firmwareFileName, "claimExistingBios", true);
	}

	/**
	 * 업로드 · claim 공통 등록 골격 — manifest 집계 → 해시 충돌 nudge → 2-phase save + marker.
	 * 진입점은 자동 탐지 없이 펌웨어 파일명으로 확정한다.
	 */
	private Long completeRegistration(
			BoardModel parent, BiosCreateRequest request,
			Path treeRoot, String firmwareFileName, String logContext, boolean claimExisting
	) {
		ManifestSummary manifest = bundleManifestService.compute(treeRoot);

		// MK2 단계 B — 해시 충돌 후보 (SoftDeleted / Deprecated) 탐지 시 nudge 세션 발급 + 409.
		issueHashNudge(parent.getId(), request.name(), request.version(), request.description(),
				treeRoot, firmwareFileName, manifest, logContext, claimExisting);

		BoardBIOS saved = persistBundle(parent, request.name(), request.version(), request.description(),
				treeRoot, firmwareFileName, manifest.manifestHash(), manifest.fileCount(), manifest.totalBytes());

		log.info(
				"[{}] 등록 완료. biosId={}, boardId={}, version={}, file={}",
				logContext, saved.getId(), parent.getId(), request.version(), firmwareFileName
		);
		return saved.getId();
	}

	/**
	 * R12-1 — 업로드 없는 기존 파일 claim 의 검증 3종.
	 * <ul>
	 *   <li>파일 실재 — 정규 파일이어야 한다.</li>
	 *   <li>마커 부재 — {@code .provision.json} 이 이미 있으면 다른 등록 소유 (재발급은 reconciliation 흐름).</li>
	 *   <li>부모 디렉토리 배타 — IN_TREE 마커 하나가 디렉토리 전체를 claim 하므로, 마커 · 무시 가능 파일을
	 *       제외하고 지정 파일 외 다른 항목이 있으면 거절한다.</li>
	 * </ul>
	 */
	private void assertClaimableFirmwareFile(Path resolved, Path treeRoot, String firmwareFileName) {
		if (!Files.isRegularFile(resolved)) {
			throw new InvalidFirmwareFileException(
					"펌웨어 파일이 해당 경로에 존재하지 않습니다 : " + resolved, "firmwarePath");
		}
		if (Files.exists(treeRoot.resolve(ProvisionMarkerService.MARKER_FILENAME))) {
			throw new MarkerConflictException(treeRoot.toString());
		}
		try (Stream<Path> children = Files.list(treeRoot)) {
			boolean hasOthers = children
					.filter(path -> !BundleFilePolicy.isIgnorable(path))
					.anyMatch(path -> !path.getFileName().toString().equals(firmwareFileName));
			if (hasOthers) {
				throw new InvalidFirmwareFileException(
						"펌웨어 파일이 있는 디렉토리에 다른 파일이 함께 있습니다. 파일 하나만 있는 디렉토리를 지정하십시오 : " + treeRoot,
						"firmwarePath");
			}
		} catch (IOException e) {
			throw new BundleExtractionException("기존 파일 검증 실패 : " + treeRoot, e);
		}
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
	 *
	 * <p>R12-1 — claim 경로(업로드 없는 기존 파일 등록)에서 발급된 nudge 는 트리가 업로드 임시물이
	 * 아니라 <b>사용자의 기존 파일</b>이므로 삭제하지 않는다. 구분은 {@link #issueHashNudge} 가
	 * payload attributes 에 기록한 {@code claimExisting} 플래그가 SSOT.</p>
	 */
	public void cleanupNudgeCancelled(ContentNudgePayload payload) {
		if (Boolean.parseBoolean(payload.attributes().getOrDefault("claimExisting", "false"))) {
			log.info("[nudgeCancel] claim 경로 — 사용자 기존 파일 보존. path={}", payload.tempFilePath());
			return;
		}
		bundleTreeCleanupService.purgeExistingTree(Path.of(payload.tempFilePath()), "nudgeCancel");
	}

	// ==== private helpers (복붙 dedup) =================================

	/**
	 * MK2 단계 B — 해시 충돌 후보 (SoftDeleted / Deprecated) 탐지 시 nudge 세션 발급 + {@link BiosNudgeRequiredException}.
	 * 임시 트리는 호출자 treeRoot 에 그대로 남겨두고 사용자 결정(proceed / replace / cancel) 대기.
	 * MK3-1 — ghost (DB-only soft-deleted, FS 부재) 후보는 사전 필터링.
	 */
	private void issueHashNudge(
			Long boardId, String name, String version, String description,
			Path treeRoot, String firmwareFileName, ManifestSummary manifest, String logContext,
			boolean claimExisting
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
						treeRoot.toString(),
						Map.of(
								"entrypointRelativePath", firmwareFileName,
								"fileCount", String.valueOf(manifest.fileCount()),
								"totalBytes", String.valueOf(manifest.totalBytes()),
								"description", description != null ? description : "",
								// R12-1 — cancel 시 트리 보존 여부의 SSOT (claim = 사용자 기존 파일).
								"claimExisting", String.valueOf(claimExisting)
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
	 * 업로드 / claim / persistFromNudge 3 경로의 동일 골격을 단일화.
	 */
	private BoardBIOS persistBundle(
			BoardModel parent, String name, String version, String description,
			Path treeRoot, String entrypoint, String manifestHash, int fileCount, long totalBytes
	) {
		// 신규 = 최신 기본(E2-1-a) — 전 행 +1 로 1위 자리를 비우고 새 행이 1위로 들어간다.
		// 소급 등록(옛 버전)은 등록 후 목록 드래그로 내린다.
		biosRepository.shiftAllVersionRanks(parent.getId());
		BoardBIOS saved = biosRepository.save(BoardBIOS.builder()
													  .versionRank(1)
													  .boardModel(parent)
													  .name(name)
													  .version(version)
													  .treeRootPath(treeRoot.toString())
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
				saved, treeRoot, parent.getId(), version, entrypoint, manifestHash);
		return saved;
	}
}
