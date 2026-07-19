package com.example.serverprovision.management.os.service.iso;

import com.example.serverprovision.global.job.service.BackgroundJobService;
import com.example.serverprovision.global.marker.MarkerContent;
import com.example.serverprovision.global.marker.MarkerLayout;
import com.example.serverprovision.global.marker.ResourceType;
import com.example.serverprovision.global.marker.exception.SidecarConflictException;
import com.example.serverprovision.global.marker.service.ProvisionMarkerService;
import com.example.serverprovision.global.security.FileSystemHardener;
import com.example.serverprovision.global.security.PathPolicyService;
import com.example.serverprovision.management.common.nudge.NudgeRegistry;
import com.example.serverprovision.management.common.nudge.NudgeResourceType;
import com.example.serverprovision.management.common.nudge.NudgeSession;
import com.example.serverprovision.management.common.nudge.dto.NudgeConflictEntry;
import com.example.serverprovision.management.common.nudge.dto.NudgeRequiredResponse;
import com.example.serverprovision.management.os.dto.request.ISOCreateRequest;
import com.example.serverprovision.management.os.entity.ISO;
import com.example.serverprovision.management.os.entity.OSMetadata;
import com.example.serverprovision.management.os.exception.*;
import com.example.serverprovision.management.os.repository.ISORepository;
import com.example.serverprovision.management.os.repository.OSMetadataRepository;
import com.example.serverprovision.management.os.service.postcreation.PostCreationTaskStrategy;
import com.example.serverprovision.management.os.util.IsoPathResolver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

/**
 * R1-4-2 — ISO 등록 흐름 전담 service. R1-4-2 이전 OSMetadataService 에 잔류하던 ISO 등록 책임
 * (단계 A 준비 / 단계 B 영속화 / nudge 후속 처리) 을 본 service 로 응집해 OSMetadataService 가 부모 도메인에만 집중하게 한다.
 *
 * <p>책임 4 진입점 :</p>
 * <ul>
 *   <li>{@link #add} — 동기 직접 등록 (단순 진입점, 단위 테스트·legacy 호출 호환).</li>
 *   <li>{@link #prepare} — 단계 A. 경로 검증 + 임시 파일 저장 + sidecar 사전 충돌 검사. 결과는 {@link PreparedIsoRegistration}.</li>
 *   <li>{@link #finalize} — 단계 B. 해시 재계산 + 활성/잠재 충돌 분기 (활성 → Duplicate, 잠재 → Nudge 발화) + 영속화 + 마커 발급 + S5-7 post-creation 자동 트리거.</li>
 *   <li>{@link #completePendingFromNudge} — nudge confirm (PROCEED / REPLACE) 의 두 번째 단계. 임시 파일을 ACTIVE 로 영속화.</li>
 *   <li>{@link #purgeForNudge} — nudge REPLACE 의 첫 번째 단계. 충돌 후보 ISO 영구 삭제 (sidecar + body + row).</li>
 * </ul>
 *
 * <p>의존 그래프 — 단방향 :</p>
 * <ul>
 *   <li>본 service → {@link IsoLifecycleService} (cleanupArtifacts 위임), {@link OSMetadataRepository} (부모 lookup), {@link ISORepository} 등.</li>
 *   <li>본 service ⇸ OSMetadataService (의존 없음). 부모 lookup 은 Repository 를 통해 직접.</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class IsoRegistrationService {

	private final OSMetadataRepository osMetadataRepository;
	private final ISORepository isoRepository;
	private final ProvisionMarkerService markerService;
	private final BackgroundJobService backgroundJobService;
	private final PathPolicyService pathPolicyService;
	private final FileSystemHardener fileSystemHardener;
	private final NudgeRegistry nudgeRegistry;
	// S5-7 — OS family 별 자동 작업 트리거.
	private final List<PostCreationTaskStrategy> postCreationTaskStrategies;
	// R1-4-1 — ISO 디스크 부산물 (본체 + sidecar) 정리 helper 위임. 단방향, cycle 없음.
	private final IsoLifecycleService isoLifecycleService;

	// ==== public 진입점 ================================================

	@Transactional
	public Long add(Long osMetadataId, ISOCreateRequest request, MultipartFile uploadedFile) {
		PreparedIsoRegistration prepared = prepare(osMetadataId, request, uploadedFile, null);
		return finalize(null, prepared);
	}

	@Transactional
	public PreparedIsoRegistration prepare(
			Long osMetadataId,
			ISOCreateRequest request,
			MultipartFile uploadedFile
	) {
		return prepare(osMetadataId, request, uploadedFile, null);
	}

	/**
	 * MK2 WAVE 3 — clientHash 동봉 오버로드. controller 가 intent.consume() 결과의 clientHash 를 전달.
	 */
	@Transactional
	public PreparedIsoRegistration prepare(
			Long osMetadataId,
			ISOCreateRequest request,
			MultipartFile uploadedFile,
			String clientHash
	) {
		requireActiveImage(osMetadataId);

		boolean hasFile = uploadedFile != null && !uploadedFile.isEmpty();
		String originalFilename = hasFile ? uploadedFile.getOriginalFilename() : null;
		String resolvedPath = IsoPathResolver.resolve(
				request.isoPath(),
				originalFilename,
				path -> new InvalidIsoPathException("경로가 '/' 로 끝나면 업로드할 파일이 필요합니다 : " + path)
		);

		log.info(
				"[prepare] osMetadataId={}, raw={}, resolved={}, allowCreateDirectory={}, hasFile={}",
				osMetadataId, request.isoPath(), resolvedPath, request.allowCreateDirectory(), hasFile
		);

		ensureParentDirectory(resolvedPath, request.allowCreateDirectory());
		// S3 — allowlist 검증
		Path target = pathPolicyService.assertWritablePath(resolvedPath);

		// HF4-3 (F-4) — 대상이 기존 디렉토리면 저장 자체가 불가능한 입력. 방치하면 storeUploadedFile 의
		// Files.copy IOException → ISOFileStorageException(500) 으로 새므로 필드 직결 400 으로 선판정한다.
		if (Files.isDirectory(target)) {
			throw new IsoPathIsDirectoryException(resolvedPath);
		}

		isoRepository.findFirstByOsMetadata_IdAndIsoPathAndIsDeletedFalse(osMetadataId, resolvedPath)
				.ifPresent(existing -> {
					throw new IsoUploadIntentConflictException("같은 경로에 이미 등록된 ISO 가 있습니다 : " + existing.getIsoPath());
				});

		Path sidecar = markerService.resolveMarkerFile(target, MarkerLayout.SIDECAR);
		if (Files.exists(sidecar)) {
			throw new SidecarConflictException(sidecar.toString());
		}

		if (hasFile) {
			if (Files.exists(target) && !Files.isDirectory(target)) {
				throw new DuplicateFilenameException(resolvedPath);
			}
			storeUploadedFile(uploadedFile, resolvedPath);
			// S3.1 (B1) — 저장 직후 POSIX 권한 0644 강제 (의도치 않은 실행 권한 차단).
			fileSystemHardener.applyDefaultPermissionsForFile(target);
		} else if (!Files.isRegularFile(target)) {
			throw new InvalidIsoPathException("ISO 파일이 해당 경로에 존재하지 않습니다 : " + resolvedPath);
		}

		String originalName = hasFile
				? originalFilename
				: target.getFileName().toString();
		return new PreparedIsoRegistration(
				osMetadataId,
				resolvedPath,
				request.description(),
				originalName != null ? originalName : "",
				hasFile,
				clientHash
		);
	}

	@Transactional
	public Long finalize(String jobId, PreparedIsoRegistration prepared) {
		// S3 — finalize 직전 다시 한 번 allowlist 검증 (이중 가드)
		pathPolicyService.assertWritablePath(prepared.resolvedPath());
		OSMetadata parent = requireActiveImage(prepared.osMetadataId());
		Path target = Path.of(prepared.resolvedPath());

		String manifestHash = computeFileSha256(target);

		// MK2 WAVE 3 — client 가 intent 시 보낸 hash 와 server 가 재계산한 hash 비교 (변조 / corruption 방어).
		if (prepared.clientHash() != null && !prepared.clientHash().isBlank()
				&& !prepared.clientHash().equalsIgnoreCase(manifestHash)) {
			log.warn(
					"[finalize] client hash mismatch! osMetadataId={}, client={}, server={}",
					prepared.osMetadataId(), prepared.clientHash(), manifestHash
			);
			cleanupUploadedArtifacts(prepared.uploadedFile(), target);
			throw new IsoClientHashMismatchException(prepared.clientHash(), manifestHash);
		}

		startJobStage(jobId, IsoRegistrationStage.CHECK_DUPLICATE);

		isoRepository.findFirstByOsMetadata_IdAndIsoPathAndIsDeletedFalse(prepared.osMetadataId(), prepared.resolvedPath())
				.ifPresent(existing -> {
					throw new IsoUploadIntentConflictException("같은 경로에 이미 등록된 ISO 가 있습니다 : " + existing.getIsoPath());
				});

		// 활성 ISO 와의 해시 충돌 → fail-fast.
		isoRepository.findFirstByChecksumAndIsDeletedFalse(manifestHash).ifPresent(existing -> {
			cleanupUploadedArtifacts(prepared.uploadedFile(), target);
			throw new DuplicateISOContentException(existing.getIsoPath());
		});

		// MK2 — soft-deleted / deprecated 자원과의 해시 충돌 → nudge 흐름.
		// 자동 purge 로 기존 row 를 silently 정리하지 않는다 — 사용자가 modal 에서 결정해야 한다.
		List<ISO> dormantConflicts =
				isoRepository.findByManifestHashAndIsDeletedTrueOrIsDeprecatedTrue(manifestHash);
		// MK3-1 — ghost (DB-only soft-deleted, FS 부재) 후보 사전 필터링. 사용자 흐름 차단 방지.
		dormantConflicts = dormantConflicts.stream()
				.filter(c -> !com.example.serverprovision.global.trash.GhostEvaluator.isGhost(c))
				.toList();
		if (!dormantConflicts.isEmpty()) {
			NudgeRequiredResponse payload = registerIsoNudgeSession(prepared, manifestHash, dormantConflicts);
			// 임시 파일은 그대로 두고 (confirm 시 정식 등록), 예외로 단계 B 결과를 호출자에게 전달.
			// job runner 가 본 예외를 catch 해 fail 메시지에 nudgeId 를 동봉, UI 가 폴링해 modal 표시.
			throw new IsoNudgeRequiredException(payload);
		}

		startJobStage(jobId, IsoRegistrationStage.PERSIST_METADATA);

		ISO saved = isoRepository.save(ISO.builder()
											   .osMetadata(parent)
											   .isoPath(prepared.resolvedPath())
											   .checksum(manifestHash)
											   .manifestHash(manifestHash)
											   .markerSignature(null)
											   .description(prepared.description())
											   .isEnabled(true)
											   .isDeleted(false)
											   .build());

		finalizeMarker(saved, prepared, manifestHash, target);

		log.info(
				"[finalize] 등록 완료. isoId={}, osMetadataId={}, uploadedFile={}, hash={}",
				saved.getId(), prepared.osMetadataId(), prepared.uploadedFile(), manifestHash
		);

		// S5-7 — ISO 등록 후 자동 작업 트리거 (OS family 별 strategy 다형성).
		// 실패는 ISO 등록 자체 영향 없도록 fail-safe — D3 정책 B (등록 성공 + Job 실패 알림).
		triggerPostCreationTask(parent, saved);

		return saved.getId();
	}

	/**
	 * MK2 — nudge confirm (PROCEED / REPLACE) 에서 호출. 임시 파일이 가리키는 자원을 ACTIVE 로 영속화.
	 */
	@Transactional
	public Long completePendingFromNudge(NudgeSession session) {
		if (session.resourceType() != NudgeResourceType.OS_ISO) {
			throw new IllegalOSMetadataStateException("OS_ISO 세션이 아닙니다.");
		}
		Long osMetadataId = session.boardId();
		OSMetadata parent = requireActiveImage(osMetadataId);
		if (!(session.payload() instanceof com.example.serverprovision.management.common.nudge.ContentNudgePayload p)) {
			throw new IllegalOSMetadataStateException(
					"OS_ISO nudge 세션은 ContentNudgePayload 만 허용합니다. nudgeId=" + session.nudgeId());
		}
		Path target = Path.of(p.tempFilePath());
		String manifestHash = p.manifestHash();
		String description = p.attributes().getOrDefault("description", "");
		String originalFilename = p.attributes().getOrDefault("originalFilename", "");
		boolean uploadedFile = Boolean.parseBoolean(p.attributes().getOrDefault("uploadedFile", "false"));

		// confirm 시점에 활성 ISO 와 해시가 또 충돌하면 fail-fast (다른 사용자가 그 사이 같은 자원을 등록).
		isoRepository.findFirstByChecksumAndIsDeletedFalse(manifestHash).ifPresent(existing -> {
			cleanupUploadedArtifacts(uploadedFile, target);
			throw new DuplicateISOContentException(existing.getIsoPath());
		});

		ISO saved = isoRepository.save(ISO.builder()
											   .osMetadata(parent)
											   .isoPath(p.tempFilePath())
											   .checksum(manifestHash)
											   .manifestHash(manifestHash)
											   .markerSignature(null)
											   .description(description)
											   .isEnabled(true)
											   .isDeleted(false)
											   .build());

		PreparedIsoRegistration prepared = new PreparedIsoRegistration(
				osMetadataId, p.tempFilePath(), description, originalFilename, uploadedFile);
		finalizeMarker(saved, prepared, manifestHash, target);
		log.info(
				"[completePendingFromNudge] 신규 ISO 영속화 완료. isoId={}, osMetadataId={}, hash={}",
				saved.getId(), osMetadataId, manifestHash
		);
		return saved.getId();
	}

	/**
	 * MK2 — REPLACE confirm 에서 충돌 후보 ISO 를 영구 삭제할 때 nudge 서비스가 호출한다.
	 * 외부 endpoint 와 직접 묶이지 않은 내부 진입점. 호출자 (OSNudgeService) 가 LifecycleStage 검증을 마쳤다고 가정.
	 */
	@Transactional
	public void purgeForNudge(ISO target) {
		isoLifecycleService.cleanupArtifacts(target);
		isoRepository.delete(target);
		log.info("[purgeForNudge] 충돌 후보 ISO 영구 삭제. isoId={}", target.getId());
	}

	// ==== private helpers ==============================================

	/**
	 * S5-7 — OS family 에 매칭되는 PostCreationTaskStrategy 를 찾아 자동 작업 트리거.
	 * strategy 실행 중 RuntimeException 발생 시 ISO 등록 트랜잭션은 보존 (D3 정책 B). 실패는
	 * BackgroundJob 의 FAILED 상태로 알림 패널에 노출되므로 사용자가 인지 가능.
	 */
	private void triggerPostCreationTask(OSMetadata osMetadata, ISO iso) {
		// 단위 테스트 @InjectMocks 환경에서 List 자동 주입이 null 인 경우 방어.
		if (postCreationTaskStrategies == null || postCreationTaskStrategies.isEmpty()) return;
		try {
			postCreationTaskStrategies.stream()
					.filter(s -> s.supports(osMetadata.getOsName().getFamily()))
					.findFirst()
					.ifPresent(s -> s.trigger(osMetadata, iso));
		} catch (RuntimeException e) {
			log.error(
					"[S5-7] PostCreationTask 트리거 실패 (ISO 등록은 영속됨). osMetadataId={}, isoId={}, family={}",
					osMetadata.getId(), iso.getId(), osMetadata.getOsName().getFamily(), e
			);
		}
	}

	private NudgeRequiredResponse registerIsoNudgeSession(
			PreparedIsoRegistration prepared,
			String manifestHash,
			List<ISO> dormantConflicts
	) {
		List<NudgeConflictEntry> conflicts = dormantConflicts.stream()
				.map(c -> new NudgeConflictEntry(
						c.getId(),
						c.currentStage(),
						c.getManifestHash(),
						c.getOsMetadata().getOsVersion(),
						c.getIsoPath(),
						// ISO 엔티티가 보유한 createdAt 은 LocalDateTime — Instant 로 환산.
						c.getCreatedAt() != null
								? c.getCreatedAt().atZone(java.time.ZoneId.systemDefault()).toInstant()
								: Instant.EPOCH
				))
				.toList();
		List<Long> conflictIds = dormantConflicts.stream().map(ISO::getId).toList();

		com.example.serverprovision.management.common.nudge.ContentNudgePayload payload =
				new com.example.serverprovision.management.common.nudge.ContentNudgePayload(
						// OS_ISO 의 name 슬롯에는 isoPath 를 담는다 (도메인별 의미 매핑).
						prepared.resolvedPath(),
						"",
						manifestHash,
						prepared.resolvedPath(),
						Map.of(
								"osMetadataId", String.valueOf(prepared.osMetadataId()),
								"originalFilename", prepared.originalFilename(),
								"description", prepared.description() != null ? prepared.description() : "",
								"uploadedFile", String.valueOf(prepared.uploadedFile())
						)
				);
		NudgeSession session = nudgeRegistry.register(
				NudgeResourceType.OS_ISO,
				prepared.osMetadataId(),
				conflictIds,
				payload
		);
		return NudgeRequiredResponse.of(session.nudgeId(), conflicts, session.expiresAt());
	}

	private void finalizeMarker(
			ISO saved,
			PreparedIsoRegistration prepared,
			String manifestHash,
			Path target
	) {
		MarkerContent unsigned = new MarkerContent(
				ResourceType.OS_ISO.name(),
				saved.getId(),
				Map.of(
						"osMetadataId", String.valueOf(prepared.osMetadataId()),
						"originalFilename", prepared.originalFilename()
				),
				Instant.now(),
				manifestHash,
				null
		);
		String signature = markerService.computeSignature(unsigned);
		saved.reissueMarker(manifestHash, signature);
		// 마커 기록 IO 실패를 도메인 예외로 wrap — runner 가 INFRA/TRANSIENT 로 분류해 파일을 격리(삭제 X)하도록.
		// (이 시점 파일은 디스크에 있고 @Transactional 이 DB row 를 롤백하므로 결과는 오펀 파일이다.)
		try {
			markerService.write(target, MarkerLayout.SIDECAR, unsigned.withSignature(signature));
		} catch (RuntimeException e) {
			throw new IsoMarkerWriteFailedException("ISO 마커(sidecar) 기록 실패. path=" + target, e);
		}
		// S3.1 (B1) + S3.2 (K12) — sidecar 경로는 ProvisionMarkerService 의 resolveMarkerFile 로 계산.
		fileSystemHardener.applyDefaultPermissionsForFile(
				markerService.resolveMarkerFile(target, MarkerLayout.SIDECAR));
	}

	/**
	 * 디스크에 이미 있는 파일의 SHA-256.
	 */
	private String computeFileSha256(Path file) {
		try {
			MessageDigest md = MessageDigest.getInstance("SHA-256");
			try (InputStream in = Files.newInputStream(file);
			     DigestInputStream dis = new DigestInputStream(in, md)) {
				byte[] buf = new byte[8192];
				while (dis.read(buf) >= 0) { /* drain */ }
			}
			return HexFormat.of().formatHex(md.digest());
		} catch (IOException | NoSuchAlgorithmException e) {
			throw new ISOFileStorageException("ISO 파일 hash 계산 실패. path=" + file, e);
		}
	}

	private void ensureParentDirectory(String targetPath, boolean allowCreateDirectory) {
		Path target;
		try {
			target = Path.of(targetPath);
		} catch (java.nio.file.InvalidPathException e) {
			throw new ISOFileStorageException("ISO 경로 형식이 올바르지 않습니다 : " + targetPath, e);
		}
		Path parent = target.getParent();
		if (parent == null || Files.exists(parent)) {
			return;
		}
		if (!allowCreateDirectory) {
			log.info("[prepare] 상위 디렉토리 없음 + 생성 미허용 → 거절. parent={}", parent);
			throw new DirectoryMissingException(parent.toString());
		}
		try {
			Files.createDirectories(parent);
			log.info("[prepare] 상위 디렉토리 자동 생성. parent={}", parent);
		} catch (IOException e) {
			throw new ISOFileStorageException("디렉토리 생성에 실패했습니다. path=" + parent, e);
		}
	}

	private void storeUploadedFile(MultipartFile file, String targetPath) {
		try {
			Path target = Path.of(targetPath);
			try (InputStream in = file.getInputStream()) {
				Files.copy(in, target);
			}
		} catch (IOException e) {
			String reason = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
			throw new ISOFileStorageException("ISO 파일 저장에 실패했습니다. path=" + targetPath + " (" + reason + ")", e);
		}
	}

	private void startJobStage(String jobId, IsoRegistrationStage stage) {
		if (jobId != null && !jobId.isBlank()) {
			backgroundJobService.startStage(jobId, stage);
		}
	}

	private void deleteQuietly(Path p) {
		try {
			Files.deleteIfExists(p);
		} catch (IOException ignore) {
			// 중복 판정 직후의 파일 제거 실패는 핵심 흐름을 막지 않는다 — 운영자가 별도로 정리할 대상.
		}
	}

	/**
	 * 콘텐츠/영구 실패(해시 불일치·중복) 시 우리가 업로드한 파일 + sidecar 마커를 정리한다.
	 * "우리가 소유한 업로드인가" 의 단일 판정({@code uploadedFile}) — in-place 등록 파일(운영자 자산)은 건드리지 않는다.
	 * (3곳에 복붙돼 있던 보상 로직을 단일화 — CLAUDE.md §중복 지양.)
	 */
	private void cleanupUploadedArtifacts(boolean uploadedFile, Path target) {
		if (!uploadedFile) {
			return;
		}
		deleteQuietly(target);
		deleteQuietly(markerService.resolveMarkerFile(target, MarkerLayout.SIDECAR));
	}

	/**
	 * 부모 OSMetadata 활성 lookup. 본 service 가 OSMetadataService 의존 없이 단방향 정렬을 위해 직접 Repository 호출.
	 */
	private OSMetadata requireActiveImage(Long id) {
		return osMetadataRepository.findByIdAndIsDeletedFalse(id)
				.orElseThrow(() -> new OSMetadataNotFoundException(id));
	}

	// ==== record =======================================================

	public record PreparedIsoRegistration(
			Long osMetadataId,
			String resolvedPath,
			String description,
			String originalFilename,
			boolean uploadedFile,
			/* MK2 WAVE 3 — intent 시 client 가 보낸 SHA-256. nullable. server-side 재계산 후 비교. */
			String clientHash
	) {

		/**
		 * WAVE 3 이전 호출자 호환 — clientHash 없이 생성.
		 */
		public PreparedIsoRegistration(
				Long osMetadataId, String resolvedPath, String description,
				String originalFilename, boolean uploadedFile
		) {
			this(osMetadataId, resolvedPath, description, originalFilename, uploadedFile, null);
		}
	}
}
