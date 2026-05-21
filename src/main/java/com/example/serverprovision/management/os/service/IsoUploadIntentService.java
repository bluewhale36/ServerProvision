package com.example.serverprovision.management.os.service;

import com.example.serverprovision.global.lifecycle.LifecycleStage;
import com.example.serverprovision.management.common.nudge.IntentMetaNudgePayload;
import com.example.serverprovision.management.common.nudge.NudgeRegistry;
import com.example.serverprovision.management.common.nudge.NudgeResourceType;
import com.example.serverprovision.management.common.nudge.NudgeSession;
import com.example.serverprovision.management.common.nudge.dto.NudgeConflictEntry;
import com.example.serverprovision.management.common.nudge.dto.NudgeRequiredResponse;
import com.example.serverprovision.management.os.dto.request.IsoUploadIntentRequest;
import com.example.serverprovision.management.os.dto.response.IsoUploadIntentResponse;
import com.example.serverprovision.management.os.entity.ISO;
import com.example.serverprovision.management.os.exception.*;
import com.example.serverprovision.management.os.repository.ISORepository;
import com.example.serverprovision.management.os.repository.OSImageRepository;
import com.example.serverprovision.management.os.util.IsoPathResolver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.FileStore;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * ISO 업로드 Intent 핸드셰이크 관리자.
 *
 * <p>MK2 — intent 응답에 {@code preExistingMatch} 사전 경고 동봉 (단계 A). 같은 OS 의 동일 isoPath 로
 * 등록된 soft-deleted ISO 가 있으면 클라이언트가 안내 modal 1차 dismiss 후 업로드 진입한다.
 * 단계 B (해시 후) 의 nudge 흐름과는 독립 — 메타만 같고 파일이 달라도 본 사전 경고는 발생할 수 있다.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class IsoUploadIntentService {

	private static final Duration TTL = Duration.ofHours(2);

	private final OSImageRepository osImageRepository;
	private final ISORepository isoRepository;
	private final NudgeRegistry nudgeRegistry;

	private final ConcurrentMap<String, Intent> intents = new ConcurrentHashMap<>();

	public IsoUploadIntentResponse issue(Long osImageId, IsoUploadIntentRequest request) {
		// 부모 OS 존재 확인
		osImageRepository.findByIdAndIsDeletedFalse(osImageId)
				.orElseThrow(() -> new OSImageNotFoundException(osImageId));

		String rawPath = request.isoPath();
		String filename = request.filename();
		long size = request.size();

		String resolvedPath = IsoPathResolver.resolve(
				rawPath,
				filename,
				p -> new IsoUploadIntentConflictException("경로가 '/' 로 끝나면 업로드할 파일이 필요합니다 : " + p)
		);

		// 하드 블록 : 같은 OS 의 동일 경로에 활성 ISO 가 이미 등록됨 (DB 차원)
		Optional<ISO> existingSamePath = isoRepository
				.findFirstByOsImage_IdAndIsoPathAndIsDeletedFalse(osImageId, resolvedPath);
		if (existingSamePath.isPresent()) {
			throw new IsoUploadIntentConflictException(
					"같은 경로에 이미 등록된 ISO 가 있습니다 : " + resolvedPath);
		}

		// MK2 WAVE 2 — 단계 A path nudge : 같은 (osImageId, isoPath) 의 soft-deleted/Deprecated → NUDGE_REQUIRED.
		List<ISO> pathNudgeCandidates = isoRepository.findIntentPathNudgeCandidates(osImageId, resolvedPath);
		if (!pathNudgeCandidates.isEmpty()) {
			NudgeSession session = registerIntentNudge(osImageId, resolvedPath, request, pathNudgeCandidates);
			throw new IsoNudgeRequiredException(
					"동일한 경로의 ISO 가 휴지통 또는 Deprecated 상태로 발견됐습니다. 진행 방법을 선택하세요.",
					NudgeRequiredResponse.of(session.nudgeId(), toConflictEntries(pathNudgeCandidates), session.expiresAt())
			);
		}

		// MK2 WAVE 3 — Phase 2 (client hash 동봉) : hash 매칭 nudge.
		if (request.clientHash() != null && !request.clientHash().isBlank()) {
			List<ISO> hashCandidates = isoRepository.findIntentHashNudgeCandidates(osImageId, request.clientHash());
			if (!hashCandidates.isEmpty()) {
				NudgeSession session = registerIntentNudge(osImageId, resolvedPath, request, hashCandidates);
				throw new IsoNudgeRequiredException(
						"동일한 내용의 ISO 가 휴지통 또는 Deprecated 상태로 발견됐습니다. 진행 방법을 선택하세요.",
						NudgeRequiredResponse.of(session.nudgeId(), toConflictEntries(hashCandidates), session.expiresAt())
				);
			}
			// hash 비매칭 — 정상 진행 (아래 token 발급)
		} else {
			// MK2 WAVE 3 — Phase 1 (hash 미동봉) : 휴지통 / Deprecated ISO 1건 이상이면 client 에 hash 계산 요청.
			List<ISO> hashCheckCandidates = isoRepository.findIntentHashCheckCandidates(osImageId);
			if (!hashCheckCandidates.isEmpty()) {
				log.info(
						"[IsoUploadIntentService] phase1 hash check required. osImageId={}, candidates={}",
						osImageId, hashCheckCandidates.size()
				);
				return new IsoUploadIntentResponse.HashCheckRequired(
						toConflictEntries(hashCheckCandidates),
						"SHA-256"
				);
			}
		}

		// 하드 블록 : 파일시스템 상 동일 이름 파일이 실재 — 덮어쓰기 방지
		validateFilesystem(resolvedPath, request.allowCreateDirectory(), size);

		// 소프트 경고 수집
		List<String> warnings = new ArrayList<>();
		if (size == 0) {
			warnings.add("파일 크기가 0 으로 보고되었습니다. 업로드 전 파일 상태를 확인하세요.");
		}

		String token = UUID.randomUUID().toString();
		intents.put(
				token, new Intent(
						osImageId, resolvedPath, filename, size,
						request.clientHash(), Instant.now()
				)
		);
		log.info(
				"[IsoUploadIntentService] intent 발급. token={}, osImageId={}, rawPath={}, resolvedPath={}, size={}, clientHash={}",
				token, osImageId, rawPath, resolvedPath, size,
				request.clientHash() != null ? request.clientHash().substring(0, Math.min(16, request.clientHash().length())) + "…" : "<null>"
		);
		return new IsoUploadIntentResponse.IntentTokenIssued(token, warnings);
	}

	private void validateFilesystem(String resolvedPath, boolean allowCreateDirectory, long size) {
		try {
			Path target = Path.of(resolvedPath);
			if (Files.exists(target) && !Files.isDirectory(target)) {
				throw new DuplicateFilenameException(resolvedPath);
			}
			Path parent = target.getParent();
			if (parent != null && !Files.exists(parent) && !allowCreateDirectory) {
				throw new DirectoryMissingException(parent.toString());
			}
			if (size > 0 && parent != null && Files.exists(parent)) {
				try {
					FileStore store = Files.getFileStore(parent);
					long usable = store.getUsableSpace();
					long safetyMargin = Math.max(size / 10, 256L * 1024 * 1024);
					if (usable < size + safetyMargin) {
						throw new InsufficientDiskSpaceException(resolvedPath, size + safetyMargin, usable);
					}
				} catch (IOException ioe) {
					log.warn(
							"[IsoUploadIntentService] FileStore 조회 실패 — 디스크 공간 검증 건너뜀. parent={}, msg={}",
							parent, ioe.getMessage()
					);
				}
			}
		} catch (java.nio.file.InvalidPathException e) {
			throw new IsoUploadIntentConflictException("ISO 경로 형식이 올바르지 않습니다 : " + resolvedPath);
		}
	}

	public Intent consume(Long osImageId, String token) {
		if (token == null || token.isBlank()) {
			throw new InvalidUploadTokenException("업로드 토큰이 없습니다. 페이지를 새로고침 후 다시 시도하세요.");
		}
		Intent intent = intents.remove(token);
		if (intent == null) {
			throw new InvalidUploadTokenException("만료되었거나 유효하지 않은 업로드 토큰입니다.");
		}
		if (!intent.osImageId().equals(osImageId)) {
			throw new InvalidUploadTokenException("토큰과 요청 OS 가 일치하지 않습니다.");
		}
		Duration age = Duration.between(intent.issuedAt(), Instant.now());
		if (age.compareTo(TTL) > 0) {
			throw new InvalidUploadTokenException("업로드 토큰이 만료되었습니다. 다시 시도해주세요.");
		}
		return intent;
	}

	@Scheduled(fixedDelayString = "${upload.intent.prune-interval-ms:300000}")
	public void prune() {
		Instant cutoff = Instant.now().minus(TTL);
		intents.entrySet().removeIf(e -> e.getValue().issuedAt().isBefore(cutoff));
	}

	public int size() {
		return intents.size();
	}

	/**
	 * MK2 WAVE 2 — nudge proceed/replace 후 호출. 메타 path 검사 skip + intent 발급.
	 * 호출자가 IntentMetaNudgePayload.attributes 로부터 IsoUploadIntentRequest 를 reconstruct 해서 전달.
	 */
	public IsoUploadIntentResponse issueAfterNudge(Long osImageId, IsoUploadIntentRequest request) {
		// 부모 OS 존재 확인
		osImageRepository.findByIdAndIsDeletedFalse(osImageId)
				.orElseThrow(() -> new OSImageNotFoundException(osImageId));

		String resolvedPath = IsoPathResolver.resolve(
				request.isoPath(),
				request.filename(),
				p -> new IsoUploadIntentConflictException("경로가 '/' 로 끝나면 업로드할 파일이 필요합니다 : " + p)
		);

		// 활성 동일 경로 race 방어 재검사
		if (isoRepository.findFirstByOsImage_IdAndIsoPathAndIsDeletedFalse(osImageId, resolvedPath).isPresent()) {
			throw new IsoUploadIntentConflictException(
					"같은 경로에 이미 등록된 ISO 가 있습니다 : " + resolvedPath);
		}

		List<String> warnings = new ArrayList<>();
		if (request.size() == 0) {
			warnings.add("파일 크기가 0 으로 보고되었습니다. 업로드 전 파일 상태를 확인하세요.");
		}

		String token = UUID.randomUUID().toString();
		intents.put(
				token, new Intent(
						osImageId, resolvedPath, request.filename(), request.size(),
						request.clientHash(), Instant.now()
				)
		);
		return new IsoUploadIntentResponse.IntentTokenIssued(token, warnings);
	}

	private NudgeSession registerIntentNudge(
			Long osImageId, String resolvedPath,
			IsoUploadIntentRequest request, List<ISO> candidates
	) {
		Map<String, String> attributes = new HashMap<>();
		attributes.put("osImageId", String.valueOf(osImageId));
		attributes.put("isoPath", request.isoPath());
		attributes.put("resolvedPath", resolvedPath);
		attributes.put("filename", request.filename() != null ? request.filename() : "");
		attributes.put("size", String.valueOf(request.size()));
		attributes.put("allowCreateDirectory", String.valueOf(request.allowCreateDirectory()));
		return nudgeRegistry.register(
				NudgeResourceType.OS_ISO,
				osImageId,
				candidates.stream().map(ISO::getId).toList(),
				new IntentMetaNudgePayload(attributes)
		);
	}

	private List<NudgeConflictEntry> toConflictEntries(List<ISO> candidates) {
		return candidates.stream()
				.map(c -> new NudgeConflictEntry(
						c.getId(),
						LifecycleStage.of(c.isDeprecated(), c.isDeleted()),
						c.getManifestHash(),
						c.getOsImage().getOsVersion(),
						c.getIsoPath(),
						Instant.now()
				))
				.toList();
	}

	/**
	 * MK2 WAVE 2 — IntentMetaNudgePayload.attributes 로부터 (osImageId, IsoUploadIntentRequest) 재구성.
	 */
	public IntentReissue reconstructFromAttributes(Map<String, String> attributes) {
		Long osImageId = Long.parseLong(attributes.get("osImageId"));
		IsoUploadIntentRequest request = new IsoUploadIntentRequest(
				attributes.get("isoPath"),
				attributes.getOrDefault("filename", ""),
				Long.parseLong(attributes.getOrDefault("size", "0")),
				Boolean.parseBoolean(attributes.getOrDefault("allowCreateDirectory", "false")),
				// WAVE 3 — proceed/replace 후 재발급은 hash 검사 skip 의도라 null 동봉.
				null
		);
		return new IntentReissue(osImageId, request);
	}

	/**
	 * MK2 WAVE 3 — Intent record 에 {@code clientHash} 추가. 단계 B (IsoVerificationLauncher) 가 server-side
	 * hash 재계산 후 본 값과 비교 → 불일치 시 IsoClientHashMismatchException fail-fast.
	 */
	public record Intent(
			Long osImageId,
			String isoPath,
			String filename,
			long expectedSize,
			String clientHash,
			Instant issuedAt
	) {

	}


	public record IntentReissue(
			Long osImageId,
			IsoUploadIntentRequest request
	) {

	}
}
