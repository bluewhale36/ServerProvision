package com.example.serverprovision.management.os.service.iso;

import com.example.serverprovision.management.common.nudge.IntentMetaNudgePayload;
import com.example.serverprovision.management.common.nudge.NudgeRegistry;
import com.example.serverprovision.management.common.nudge.NudgeResourceType;
import com.example.serverprovision.management.common.nudge.NudgeSession;
import com.example.serverprovision.management.common.nudge.dto.NudgeConflictEntry;
import com.example.serverprovision.management.common.nudge.dto.NudgeRequiredResponse;
import com.example.serverprovision.management.common.util.UploadPathResolver;
import com.example.serverprovision.management.os.dto.request.IsoUploadIntentRequest;
import com.example.serverprovision.management.os.dto.response.IsoUploadIntentResponse;
import com.example.serverprovision.management.os.entity.ISO;
import com.example.serverprovision.management.os.exception.*;
import com.example.serverprovision.management.os.exception.DuplicateISOContentException;
import com.example.serverprovision.management.os.repository.ISORepository;
import com.example.serverprovision.management.os.repository.OSMetadataRepository;
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

	private final OSMetadataRepository osMetadataRepository;
	private final ISORepository isoRepository;
	private final NudgeRegistry nudgeRegistry;

	private final ConcurrentMap<String, Intent> intents = new ConcurrentHashMap<>();

	public IsoUploadIntentResponse issue(Long osMetadataId, IsoUploadIntentRequest request) {
		// 부모 OS 존재 확인
		osMetadataRepository.findByIdAndIsDeletedFalse(osMetadataId)
				.orElseThrow(() -> new OSMetadataNotFoundException(osMetadataId));

		String rawPath = request.isoPath();
		String filename = request.filename();
		long size = request.size();

		String resolvedPath = UploadPathResolver.resolve(
				rawPath,
				filename,
				p -> new IsoUploadIntentConflictException("경로가 '/' 로 끝나면 업로드할 파일이 필요합니다: " + p)
		);

		// 하드 블록 : 같은 OS 의 동일 경로에 활성 ISO 가 이미 등록됨 (DB 차원)
		Optional<ISO> existingSamePath = isoRepository
				.findFirstByOsMetadata_IdAndIsoPathAndIsDeletedFalse(osMetadataId, resolvedPath);
		if (existingSamePath.isPresent()) {
			throw new IsoUploadIntentConflictException(
					"같은 경로에 이미 등록된 ISO 가 있습니다: " + resolvedPath);
		}

		// HF30-2 — 내용 판정을 경로 창보다 먼저 한다. 같은 OS 에 휴지통 · Deprecated ISO 가 하나라도 있으면 지문(SHA-256)을 먼저 받고,
		// 지문이 오면 같은 경로 · 같은 내용의 휴지통 후보를 한 창에 묻는다. 종전에는 경로 창 [그래도 등록] 뒤 재발급이 지문을 버려
		// 내용 판정을 건너뛰었고, 그 공백을 배경 단계의 휴지통 창이 메웠지만 그 창은 화면에 연결되지 않았다(docs C-17).
		if (request.clientHash() == null || request.clientHash().isBlank()) {
			List<ISO> hashCheckCandidates = isoRepository.findIntentHashCheckCandidates(osMetadataId);
			if (!hashCheckCandidates.isEmpty()) {
				log.info(
						"[IsoUploadIntentService] phase1 hash check required. osMetadataId={}, candidates={}",
						osMetadataId, hashCheckCandidates.size()
				);
				return new IsoUploadIntentResponse.HashCheckRequired(
						toConflictEntries(hashCheckCandidates),
						"SHA-256"
				);
			}
		} else {
			assertContentAvailable(osMetadataId, request.clientHash());
			List<ISO> trashCandidates = collectTrashCandidates(osMetadataId, resolvedPath, request.clientHash());
			if (!trashCandidates.isEmpty()) {
				NudgeSession session = registerIntentNudge(osMetadataId, resolvedPath, request, trashCandidates);
				throw new IsoNudgeRequiredException(
						"같은 경로나 같은 내용의 ISO 가 휴지통에 있습니다. 진행 방법을 선택해야 합니다.",
						NudgeRequiredResponse.of(session.nudgeId(), toConflictEntries(trashCandidates), session.expiresAt())
				);
			}
		}

		// 하드 블록 : 파일시스템 상 동일 이름 파일이 실재 — 덮어쓰기 방지
		validateFilesystem(resolvedPath, request.allowCreateDirectory(), size);

		// 소프트 경고 수집
		List<String> warnings = new ArrayList<>();
		if (size == 0) {
			warnings.add("파일 크기가 0입니다. 업로드하기 전에 파일을 확인해야 합니다.");
		}

		String token = UUID.randomUUID().toString();
		intents.put(
				token, new Intent(
						osMetadataId, resolvedPath, filename, size,
						request.clientHash(), Instant.now()
				)
		);
		log.info(
				"[IsoUploadIntentService] intent 발급. token={}, osMetadataId={}, rawPath={}, resolvedPath={}, size={}, clientHash={}",
				token, osMetadataId, rawPath, resolvedPath, size,
				request.clientHash() != null ? request.clientHash().substring(0, Math.min(16, request.clientHash().length())) + "…" : "<null>"
		);
		return new IsoUploadIntentResponse.IntentTokenIssued(token, warnings);
	}

	private void validateFilesystem(String resolvedPath, boolean allowCreateDirectory, long size) {
		try {
			Path target = Path.of(resolvedPath);
			// HF4-3 (F-4b) — 디렉토리 경로는 핸드셰이크 시점에 거절해 무의미한 바이트 전송을 차단한다.
			// (IsoRegistrationService.prepare 의 가드와 동일 판정 — 없으면 전송 완료 후에야 실패.)
			if (Files.isDirectory(target)) {
				throw new IsoPathIsDirectoryException(resolvedPath);
			}
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
			throw new IsoUploadIntentConflictException("ISO 경로 형식이 올바르지 않습니다: " + resolvedPath);
		}
	}

	public Intent consume(Long osMetadataId, String token) {
		if (token == null || token.isBlank()) {
			throw new InvalidUploadTokenException("업로드 토큰이 없습니다. 페이지를 새로 고친 뒤 다시 시도해야 합니다.");
		}
		Intent intent = intents.remove(token);
		if (intent == null) {
			throw new InvalidUploadTokenException("만료되었거나 유효하지 않은 업로드 토큰입니다.");
		}
		if (!intent.osMetadataId().equals(osMetadataId)) {
			throw new InvalidUploadTokenException("업로드 토큰의 OS 버전이 요청과 다릅니다.");
		}
		Duration age = Duration.between(intent.issuedAt(), Instant.now());
		if (age.compareTo(TTL) > 0) {
			throw new InvalidUploadTokenException("업로드 토큰이 만료되었습니다. 다시 시도해야 합니다.");
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
	public IsoUploadIntentResponse issueAfterNudge(Long osMetadataId, IsoUploadIntentRequest request) {
		// 부모 OS 존재 확인
		osMetadataRepository.findByIdAndIsDeletedFalse(osMetadataId)
				.orElseThrow(() -> new OSMetadataNotFoundException(osMetadataId));

		String resolvedPath = UploadPathResolver.resolve(
				request.isoPath(),
				request.filename(),
				p -> new IsoUploadIntentConflictException("경로가 '/' 로 끝나면 업로드할 파일이 필요합니다: " + p)
		);

		// 활성 동일 경로 race 방어 재검사
		if (isoRepository.findFirstByOsMetadata_IdAndIsoPathAndIsDeletedFalse(osMetadataId, resolvedPath).isPresent()) {
			throw new IsoUploadIntentConflictException(
					"같은 경로에 이미 등록된 ISO 가 있습니다: " + resolvedPath);
		}

		List<String> warnings = new ArrayList<>();
		if (request.size() == 0) {
			warnings.add("파일 크기가 0입니다. 업로드하기 전에 파일을 확인해야 합니다.");
		}

		String token = UUID.randomUUID().toString();
		intents.put(
				token, new Intent(
						osMetadataId, resolvedPath, request.filename(), request.size(),
						request.clientHash(), Instant.now()
				)
		);
		return new IsoUploadIntentResponse.IntentTokenIssued(token, warnings);
	}

	/**
	 * 같은 OS 에 내용이 같은 살아 있는 ISO(활성 · Deprecated)가 있으면 상태별 안내로 409(HF30-2). 배경 등록 단계도 같은 판정을
	 * 경합 안전망으로 다시 한다({@link IsoRegistrationService#finalize}).
	 */
	private void assertContentAvailable(Long osMetadataId, String manifestHash) {
		IsoGuards.livingSameContent(isoRepository, osMetadataId, manifestHash)
				.ifPresent(existing -> {
					throw DuplicateISOContentException.againstRegistration(existing);
				});
	}

	/** 같은 OS 의 휴지통 ISO 중 경로가 같거나 내용이 같은 것 — 한 창의 후보(중복 제거, id 순). */
	private List<ISO> collectTrashCandidates(Long osMetadataId, String resolvedPath, String clientHash) {
		Map<Long, ISO> byId = new TreeMap<>();
		isoRepository.findIntentPathNudgeCandidates(osMetadataId, resolvedPath).forEach(iso -> byId.put(iso.getId(), iso));
		isoRepository.findIntentHashNudgeCandidates(osMetadataId, clientHash).forEach(iso -> byId.put(iso.getId(), iso));
		return List.copyOf(byId.values());
	}

	private NudgeSession registerIntentNudge(
			Long osMetadataId, String resolvedPath,
			IsoUploadIntentRequest request, List<ISO> candidates
	) {
		Map<String, String> attributes = new HashMap<>();
		attributes.put("osMetadataId", String.valueOf(osMetadataId));
		attributes.put("isoPath", request.isoPath());
		attributes.put("resolvedPath", resolvedPath);
		attributes.put("filename", request.filename() != null ? request.filename() : "");
		attributes.put("size", String.valueOf(request.size()));
		attributes.put("allowCreateDirectory", String.valueOf(request.allowCreateDirectory()));
		return nudgeRegistry.register(
				NudgeResourceType.OS_ISO,
				osMetadataId,
				candidates.stream().map(ISO::getId).toList(),
				new IntentMetaNudgePayload(attributes)
		);
	}

	private List<NudgeConflictEntry> toConflictEntries(List<ISO> candidates) {
		return candidates.stream()
				.map(c -> NudgeConflictEntry.ofPath(c, c.getOsMetadata().getOsVersion(), c.getIsoPath()))
				.toList();
	}

	/**
	 * MK2 WAVE 2 — IntentMetaNudgePayload.attributes 로부터 (osMetadataId, IsoUploadIntentRequest) 재구성.
	 */
	public IntentReissue reconstructFromAttributes(Map<String, String> attributes) {
		Long osMetadataId = Long.parseLong(attributes.get("osMetadataId"));
		IsoUploadIntentRequest request = new IsoUploadIntentRequest(
				attributes.get("isoPath"),
				attributes.getOrDefault("filename", ""),
				Long.parseLong(attributes.getOrDefault("size", "0")),
				Boolean.parseBoolean(attributes.getOrDefault("allowCreateDirectory", "false")),
				// WAVE 3 — proceed/replace 후 재발급은 hash 검사 skip 의도라 null 동봉.
				null
		);
		return new IntentReissue(osMetadataId, request);
	}

	/**
	 * MK2 WAVE 3 — Intent record 에 {@code clientHash} 추가. 단계 B (IsoVerificationLauncher) 가 server-side
	 * hash 재계산 후 본 값과 비교 → 불일치 시 IsoClientHashMismatchException fail-fast.
	 */
	public record Intent(
			Long osMetadataId,
			String isoPath,
			String filename,
			long expectedSize,
			String clientHash,
			Instant issuedAt
	) {

	}


	public record IntentReissue(
			Long osMetadataId,
			IsoUploadIntentRequest request
	) {

	}
}
