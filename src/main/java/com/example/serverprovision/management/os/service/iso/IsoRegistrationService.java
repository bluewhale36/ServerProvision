package com.example.serverprovision.management.os.service.iso;

import com.example.serverprovision.global.job.service.BackgroundJobService;
import com.example.serverprovision.global.marker.MarkerContent;
import com.example.serverprovision.global.marker.MarkerLayout;
import com.example.serverprovision.global.marker.ResourceType;
import com.example.serverprovision.global.marker.exception.SidecarConflictException;
import com.example.serverprovision.global.marker.service.ProvisionMarkerService;
import com.example.serverprovision.global.security.FileSystemHardener;
import com.example.serverprovision.global.security.PathPolicyService;
import com.example.serverprovision.management.os.dto.request.ISOCreateRequest;
import com.example.serverprovision.management.os.entity.ISO;
import com.example.serverprovision.management.os.entity.OSMetadata;
import com.example.serverprovision.management.os.exception.*;
import com.example.serverprovision.management.os.repository.ISORepository;
import com.example.serverprovision.management.os.repository.OSMetadataRepository;
import com.example.serverprovision.management.os.service.postcreation.PostCreationTaskStrategy;
import com.example.serverprovision.management.common.util.UploadPathResolver;
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
 * (단계 A 준비 / 단계 B 영속화) 을 본 service 로 응집해 OSMetadataService 가 부모 도메인에만 집중하게 한다.
 *
 * <p>책임 3 진입점 :</p>
 * <ul>
 *   <li>{@link #add} — 동기 직접 등록 (단순 진입점, 단위 테스트·legacy 호출 호환).</li>
 *   <li>{@link #prepare} — 단계 A. 경로 검증 + 임시 파일 저장 + sidecar 사전 충돌 검사. 결과는 {@link PreparedIsoRegistration}.</li>
 *   <li>{@link #finalize} — 단계 B. 해시 재계산 + 같은 OS 의 살아 있는 같은 내용 거절(경합 안전망) + 영속화 + 마커 발급 + S5-7 post-creation 자동 트리거.
 *       HF30-2 부터 휴지통의 같은 내용으로 창을 띄우지 않는다 — 업로드 전 판정이 묻는다.</li>
 * </ul>
 *
 * <p>의존 그래프 — 단방향 :</p>
 * <ul>
 *   <li>본 service → {@link OSMetadataRepository} (부모 lookup), {@link ISORepository} 등.</li>
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
	// S5-7 — OS family 별 자동 작업 트리거.
	private final List<PostCreationTaskStrategy> postCreationTaskStrategies;

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
		String resolvedPath = UploadPathResolver.resolve(
				request.isoPath(),
				originalFilename,
				path -> new InvalidIsoPathException("경로가 '/' 로 끝나면 업로드할 파일이 필요합니다: " + path)
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
					throw new IsoUploadIntentConflictException("같은 경로에 이미 등록된 ISO 가 있습니다: " + existing.getIsoPath());
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
			throw new InvalidIsoPathException("해당 경로에 ISO 파일이 없습니다: " + resolvedPath);
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
					throw new IsoUploadIntentConflictException("같은 경로에 이미 등록된 ISO 가 있습니다: " + existing.getIsoPath());
				});

		// 같은 OS 의 살아 있는 같은 내용 → fail-fast. 업로드 전 판정과 같은 기준의 경합 안전망이다(HF30-2). 휴지통의 같은 내용은
		// 업로드 전 중복 확인 창이 이미 물었으므로 여기서 다시 묻지 않는다 — 종전의 배경 단계 휴지통 창은 화면에 연결되지 않았다(docs C-17).
		IsoGuards.livingSameContent(isoRepository, prepared.osMetadataId(), manifestHash).ifPresent(existing -> {
			cleanupUploadedArtifacts(prepared.uploadedFile(), target);
			throw DuplicateISOContentException.againstRegistration(existing);
		});

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

	/**
=======
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

	// ==== private helpers ==============================================

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
			throw new IsoMarkerWriteFailedException("ISO 마커 파일을 기록하지 못했습니다: " + target, e);
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
			throw new ISOFileStorageException("ISO 파일의 해시를 계산하지 못했습니다: " + file, e);
		}
	}

	private void ensureParentDirectory(String targetPath, boolean allowCreateDirectory) {
		Path target;
		try {
			target = Path.of(targetPath);
		} catch (java.nio.file.InvalidPathException e) {
			throw new ISOFileStorageException("ISO 경로 형식이 올바르지 않습니다: " + targetPath, e);
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
			throw new ISOFileStorageException("디렉토리를 만들지 못했습니다: " + parent, e);
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
			throw new ISOFileStorageException("ISO 파일을 저장하지 못했습니다: " + targetPath + " (" + reason + ")", e);
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
