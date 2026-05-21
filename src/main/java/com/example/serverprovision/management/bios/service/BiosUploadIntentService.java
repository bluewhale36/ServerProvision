package com.example.serverprovision.management.bios.service;

import com.example.serverprovision.global.lifecycle.LifecycleStage;
import com.example.serverprovision.global.security.PathPolicyService;
import com.example.serverprovision.management.bios.dto.request.BiosUploadIntentRequest;
import com.example.serverprovision.management.bios.dto.response.BiosUploadIntentResponse;
import com.example.serverprovision.management.bios.entity.BoardBIOS;
import com.example.serverprovision.management.bios.enums.BiosUploadMode;
import com.example.serverprovision.management.bios.exception.BiosNudgeRequiredException;
import com.example.serverprovision.management.bios.exception.DuplicateBiosVersionException;
import com.example.serverprovision.management.bios.repository.BiosRepository;
import com.example.serverprovision.management.board.exception.BoardModelNotFoundException;
import com.example.serverprovision.management.board.repository.BoardModelRepository;
import com.example.serverprovision.management.common.filesystem.service.TargetDirectoryPolicyService;
import com.example.serverprovision.management.common.nudge.IntentMetaNudgePayload;
import com.example.serverprovision.management.common.nudge.NudgeRegistry;
import com.example.serverprovision.management.common.nudge.NudgeResourceType;
import com.example.serverprovision.management.common.nudge.NudgeSession;
import com.example.serverprovision.management.common.nudge.dto.NudgeConflictEntry;
import com.example.serverprovision.management.os.exception.InvalidUploadTokenException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.stream.Stream;

/**
 * BIOS 번들 업로드 Intent 핸드셰이크. 번들 바이트 전송 이전에 하드 조건을 검증하고 1회용 토큰을 발급.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BiosUploadIntentService {

	private static final Duration TTL = Duration.ofHours(2);

	private final BoardModelRepository boardModelRepository;
	private final BiosRepository biosRepository;
	private final TargetDirectoryPolicyService targetDirectoryPolicyService;
	private final PathPolicyService pathPolicyService;
	private final NudgeRegistry nudgeRegistry;

	private final ConcurrentMap<String, Intent> intents = new ConcurrentHashMap<>();

	/**
	 * MK2 WAVE 2 — intent 시점에 (board, version) 메타 키로 SoftDeleted/Deprecated 자원 사전 검출.
	 *
	 * <p>충돌 발견 시 {@link BiosNudgeRequiredException} (단계 A) 으로 throw 해 클라이언트가 임시 파일
	 * 업로드 자체를 시작하지 않도록 한다. proceed/replace 시 {@link #issueAfterNudge}
	 * 가 메타 검사를 건너뛰고 token 을 재발급한다.</p>
	 */
	public BiosUploadIntentResponse issue(Long boardId, BiosUploadIntentRequest request) {
		boardModelRepository.findByIdAndIsDeletedFalse(boardId)
				.orElseThrow(() -> new BoardModelNotFoundException(boardId));

		// 활성 동일 (board, version) 중복 — 하드 거절
		if (biosRepository.existsByBoardModel_IdAndVersionAndIsDeletedFalse(boardId, request.version())) {
			throw new DuplicateBiosVersionException(boardId, request.version());
		}

		// MK2 WAVE 2 (단계 A) — 메타 충돌 사전 검출. SoftDeleted / Deprecated 모두 conflicts 에 포함.
		List<BoardBIOS> metaCandidates = collectMetaNudgeCandidates(boardId, request.version());
		if (!metaCandidates.isEmpty()) {
			throw new BiosNudgeRequiredException(
					"동일한 (보드, 버전) 의 자원이 휴지통 또는 Deprecated 상태로 발견됐습니다. 진행 방법을 선택하세요.",
					registerIntentNudge(boardId, request, metaCandidates),
					toConflictEntries(metaCandidates)
			);
		}

		return issueAfterNudge(boardId, request);
	}

	/**
	 * MK2 WAVE 2 — nudge proceed/replace 후 호출. 메타 검사를 건너뛰고 정상 intent 발급 흐름을 수행.
	 * 활성 (board, version) 중복 검사는 race 방어용으로 한 번 더 수행 (replace 가 별도 트랜잭션이라
	 * 그 사이 다른 사용자가 활성 자원을 만들 수도 있음).
	 */
	public BiosUploadIntentResponse issueAfterNudge(Long boardId, BiosUploadIntentRequest request) {
		boardModelRepository.findByIdAndIsDeletedFalse(boardId)
				.orElseThrow(() -> new BoardModelNotFoundException(boardId));

		if (biosRepository.existsByBoardModel_IdAndVersionAndIsDeletedFalse(boardId, request.version())) {
			throw new DuplicateBiosVersionException(boardId, request.version());
		}

		// S3 — allowlist 검증
		Path targetDir = pathPolicyService.assertWritablePath(request.targetDirectory());
		targetDirectoryPolicyService.validateForIntent(targetDir, request.allowCreateDirectory());

		// 소프트 경고 수집
		List<String> warnings = new ArrayList<>();
		if (request.totalBytes() == 0) {
			warnings.add("총 바이트가 0 으로 보고되었습니다. 업로드 전 파일 상태를 확인하세요.");
		}
		if (request.uploadMode() == BiosUploadMode.FOLDER && request.fileCount() == 0) {
			warnings.add("파일 수가 0 으로 보고되었습니다. 폴더가 비어있을 수 있습니다.");
		}

		String token = UUID.randomUUID().toString();
		intents.put(
				token, new Intent(
						boardId,
						request.targetDirectory(),
						request.uploadMode(),
						request.fileCount(),
						request.totalBytes(),
						request.version(),
						request.entrypointRelativePath(),
						Instant.now()
				)
		);
		log.info(
				"[BiosUploadIntentService] issued token={}, boardId={}, mode={}, target={}",
				token, boardId, request.uploadMode(), request.targetDirectory()
		);
		// preExistingMatch 는 deprecated — meta 충돌은 이제 NUDGE_REQUIRED 로 분기되므로 항상 null.
		return new BiosUploadIntentResponse(token, warnings, null);
	}

	private List<BoardBIOS> collectMetaNudgeCandidates(Long boardId, String version) {
		return Stream.concat(
						biosRepository.findAllByBoardModel_IdAndVersionAndIsDeletedTrue(boardId, version).stream(),
						biosRepository.findAllByBoardModel_IdAndVersionAndIsDeprecatedTrueAndIsDeletedFalse(boardId, version).stream()
				)
				.toList();
	}

	private NudgeSession registerIntentNudge(Long boardId, BiosUploadIntentRequest request, List<BoardBIOS> candidates) {
		Map<String, String> attributes = new HashMap<>();
		attributes.put("boardId", String.valueOf(boardId));
		attributes.put("version", request.version());
		attributes.put("targetDirectory", request.targetDirectory());
		attributes.put("uploadMode", request.uploadMode().name());
		attributes.put("fileCount", String.valueOf(request.fileCount()));
		attributes.put("totalBytes", String.valueOf(request.totalBytes()));
		attributes.put("allowCreateDirectory", String.valueOf(request.allowCreateDirectory()));
		if (request.entrypointRelativePath() != null) {
			attributes.put("entrypointRelativePath", request.entrypointRelativePath());
		}
		return nudgeRegistry.register(
				NudgeResourceType.BIOS,
				boardId,
				candidates.stream().map(BoardBIOS::getId).toList(),
				new IntentMetaNudgePayload(attributes)
		);
	}

	private List<NudgeConflictEntry> toConflictEntries(List<BoardBIOS> candidates) {
		return candidates.stream()
				.map(b -> new NudgeConflictEntry(
						b.getId(),
						LifecycleStage.of(b.isDeprecated(), b.isDeleted()),
						b.getManifestHash(),
						b.getName(),
						b.getVersion(),
						Instant.now()
				))
				.toList();
	}

	/**
	 * MK2 WAVE 2 — IntentMetaNudgePayload 의 attributes 로부터 BiosUploadIntentRequest 재구성.
	 * BiosNudgeService 의 proceedIntent / replaceIntent 가 호출.
	 */
	public BiosUploadIntentRequest reconstructRequestFromAttributes(Map<String, String> attributes) {
		return new BiosUploadIntentRequest(
				attributes.get("targetDirectory"),
				BiosUploadMode.valueOf(attributes.get("uploadMode")),
				Integer.parseInt(attributes.get("fileCount")),
				Long.parseLong(attributes.get("totalBytes")),
				attributes.get("version"),
				Boolean.parseBoolean(attributes.getOrDefault("allowCreateDirectory", "false")),
				attributes.getOrDefault("entrypointRelativePath", "")
		);
	}

	public Intent consume(Long boardId, String token) {
		if (token == null || token.isBlank()) {
			throw new InvalidUploadTokenException("업로드 토큰이 없습니다. 페이지를 새로고침 후 다시 시도하세요.");
		}
		Intent intent = intents.remove(token);
		if (intent == null) {
			throw new InvalidUploadTokenException("만료되었거나 유효하지 않은 업로드 토큰입니다.");
		}
		if (!intent.boardId().equals(boardId)) {
			throw new InvalidUploadTokenException("토큰과 요청 메인보드가 일치하지 않습니다.");
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

	public record Intent(
			Long boardId,
			String targetDirectory,
			BiosUploadMode uploadMode,
			int fileCount,
			long totalBytes,
			String version,
			String entrypointOverride,
			Instant issuedAt
	) {

	}
}
