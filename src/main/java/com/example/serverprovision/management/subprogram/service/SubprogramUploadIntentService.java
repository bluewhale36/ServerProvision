package com.example.serverprovision.management.subprogram.service;

import com.example.serverprovision.global.lifecycle.LifecycleStage;
import com.example.serverprovision.global.security.PathPolicyService;
import com.example.serverprovision.management.board.exception.BoardModelNotFoundException;
import com.example.serverprovision.management.board.repository.BoardModelRepository;
import com.example.serverprovision.management.common.filesystem.service.TargetDirectoryPolicyService;
import com.example.serverprovision.management.common.nudge.IntentMetaNudgePayload;
import com.example.serverprovision.management.common.nudge.NudgeRegistry;
import com.example.serverprovision.management.common.nudge.NudgeResourceType;
import com.example.serverprovision.management.common.nudge.NudgeSession;
import com.example.serverprovision.management.common.nudge.dto.NudgeConflictEntry;
import com.example.serverprovision.management.os.exception.InvalidUploadTokenException;
import com.example.serverprovision.management.subprogram.dto.request.SubprogramUploadIntentRequest;
import com.example.serverprovision.management.subprogram.dto.response.SubprogramUploadIntentResponse;
import com.example.serverprovision.management.subprogram.entity.Subprogram;
import com.example.serverprovision.management.subprogram.enums.SubprogramKind;
import com.example.serverprovision.management.subprogram.enums.SubprogramUploadMode;
import com.example.serverprovision.management.subprogram.exception.DuplicateSubprogramVersionException;
import com.example.serverprovision.management.subprogram.exception.SubprogramNudgeRequiredException;
import com.example.serverprovision.management.subprogram.repository.SubprogramRepository;
import com.example.serverprovision.management.subprogram.vo.BoardScope;
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

/**
 * Subprogram 업로드 intent 발급 / 검증 / 소비. BIOS / BMC IntentService 와 동일 패턴.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SubprogramUploadIntentService {

	private static final Duration TTL = Duration.ofHours(2);

	private final BoardModelRepository boardModelRepository;
	private final SubprogramRepository subprogramRepository;
	private final TargetDirectoryPolicyService targetDirectoryPolicyService;
	private final PathPolicyService pathPolicyService;
	private final NudgeRegistry nudgeRegistry;

	private final ConcurrentMap<String, Intent> intents = new ConcurrentHashMap<>();

	/**
	 * MK2 WAVE 2 — intent 단계 메타 nudge. Subprogram 폼은 name 이 intent 단계에 없으므로
	 * (kind, scope, version) 단위로 후보 검출. 후보가 있으면 SubprogramNudgeRequiredException throw.
	 */
	public SubprogramUploadIntentResponse issue(SubprogramKind kind, BoardScope scope, SubprogramUploadIntentRequest request) {
		if (!scope.isCommon()) {
			boardModelRepository.findByIdAndIsDeletedFalse(scope.boardId())
					.orElseThrow(() -> new BoardModelNotFoundException(scope.boardId()));
		}

		// intent 단계 메타 충돌 사전 검출 — (kind, scope, version) 단위. name 미포함이라 일부 false-positive 가능하지만
		// 사용자가 modal 에서 후보 확인 후 proceed/cancel 결정 가능.
		List<Subprogram> metaCandidates = collectMetaNudgeCandidates(kind, scope, request.version());
		if (!metaCandidates.isEmpty()) {
			throw new SubprogramNudgeRequiredException(
					"동일한 (kind, scope, version) 의 Subprogram 이 휴지통 또는 Deprecated 상태로 발견됐습니다. 진행 방법을 선택하세요.",
					registerIntentNudge(kind, scope, request, metaCandidates),
					toConflictEntries(metaCandidates)
			);
		}

		return issueAfterNudge(kind, scope, request);
	}

	/**
	 * MK2 WAVE 2 — nudge proceed/replace 후 호출. 메타 검사 skip.
	 */
	public SubprogramUploadIntentResponse issueAfterNudge(SubprogramKind kind, BoardScope scope, SubprogramUploadIntentRequest request) {
		if (!scope.isCommon()) {
			boardModelRepository.findByIdAndIsDeletedFalse(scope.boardId())
					.orElseThrow(() -> new BoardModelNotFoundException(scope.boardId()));
		}

		Path targetDir = pathPolicyService.assertWritablePath(request.targetDirectory());
		targetDirectoryPolicyService.validateForIntent(targetDir, request.allowCreateDirectory());

		subprogramRepository.findFirstByTreeRootPathAndIsDeletedFalse(targetDir.toString())
				.ifPresent(existing -> {
					throw new DuplicateSubprogramVersionException(
							existing.getKind(),
							existing.isCommonScope() ? BoardScope.COMMON : BoardScope.ofBoard(existing.getBoardId()),
							existing.getName(),
							existing.getVersion()
					);
				});

		List<String> warnings = new ArrayList<>();
		if (request.totalBytes() == 0) {
			warnings.add("총 바이트가 0 으로 보고되었습니다. 업로드 전 파일 상태를 확인하세요.");
		}
		if (request.uploadMode() == SubprogramUploadMode.FOLDER && request.fileCount() == 0) {
			warnings.add("파일 수가 0 으로 보고되었습니다. 폴더가 비어있을 수 있습니다.");
		}

		String token = UUID.randomUUID().toString();
		intents.put(
				token, new Intent(
						kind,
						scope,
						request.targetDirectory(),
						request.uploadMode(),
						request.fileCount(),
						request.totalBytes(),
						request.version(),
						Instant.now()
				)
		);

		return new SubprogramUploadIntentResponse(token, warnings, null);
	}

	private List<Subprogram> collectMetaNudgeCandidates(SubprogramKind kind, BoardScope scope, String version) {
		// intent 단계엔 name 이 없으므로 wildcard 검색. (kind, scope, version) 일치하는 모든 후보를 모은다.
		Long boardId = scope.isCommon() ? null : scope.boardId();
		// findIntentNudgeCandidates 는 name 도 받으므로 임시로 list 직접 필터링.
		List<Subprogram> all = scope.isCommon()
				? subprogramRepository.findByKindAndCommonScope(kind)
				: subprogramRepository.findByKindAndBoardId(kind, scope.boardId());
		return all.stream()
				.filter(s -> version != null && version.equals(s.getVersion()))
				.filter(s -> s.isDeleted() || s.isDeprecated())
				.toList();
	}

	private NudgeSession registerIntentNudge(
			SubprogramKind kind, BoardScope scope,
			SubprogramUploadIntentRequest request, List<Subprogram> candidates
	) {
		Map<String, String> attributes = new HashMap<>();
		attributes.put("kind", kind.name());
		attributes.put("scopeCommon", String.valueOf(scope.isCommon()));
		if (!scope.isCommon()) {
			attributes.put("boardId", String.valueOf(scope.boardId()));
		}
		attributes.put("version", request.version());
		attributes.put("targetDirectory", request.targetDirectory());
		attributes.put("uploadMode", request.uploadMode().name());
		attributes.put("fileCount", String.valueOf(request.fileCount()));
		attributes.put("totalBytes", String.valueOf(request.totalBytes()));
		attributes.put("allowCreateDirectory", String.valueOf(request.allowCreateDirectory()));
		return nudgeRegistry.register(
				NudgeResourceType.SUBPROGRAM,
				scope.isCommon() ? null : scope.boardId(),
				candidates.stream().map(Subprogram::getId).toList(),
				new IntentMetaNudgePayload(attributes)
		);
	}

	private List<NudgeConflictEntry> toConflictEntries(List<Subprogram> candidates) {
		return candidates.stream()
				.map(s -> new NudgeConflictEntry(
						s.getId(),
						LifecycleStage.of(s.isDeprecated(), s.isDeleted()),
						s.getManifestHash(),
						s.getName(),
						s.getVersion(),
						Instant.now()
				))
				.toList();
	}

	/**
	 * MK2 WAVE 2 — IntentMetaNudgePayload.attributes 로부터 (SubprogramKind, BoardScope, Request) 재구성.
	 */
	public IntentReissue reconstructFromAttributes(Map<String, String> attributes) {
		SubprogramKind kind = SubprogramKind.valueOf(attributes.get("kind"));
		boolean common = Boolean.parseBoolean(attributes.getOrDefault("scopeCommon", "false"));
		BoardScope scope = common ? BoardScope.COMMON : BoardScope.ofBoard(Long.parseLong(attributes.get("boardId")));
		SubprogramUploadIntentRequest request = new SubprogramUploadIntentRequest(
				attributes.get("targetDirectory"),
				SubprogramUploadMode.valueOf(attributes.get("uploadMode")),
				Integer.parseInt(attributes.get("fileCount")),
				Long.parseLong(attributes.get("totalBytes")),
				attributes.get("version"),
				Boolean.parseBoolean(attributes.getOrDefault("allowCreateDirectory", "false"))
		);
		return new IntentReissue(kind, scope, request);
	}

	public Intent consume(SubprogramKind kind, BoardScope scope, String token) {
		if (token == null || token.isBlank()) {
			throw new InvalidUploadTokenException("업로드 토큰이 없습니다. 페이지를 새로고침 후 다시 시도하세요.");
		}
		Intent intent = intents.remove(token);
		if (intent == null) {
			throw new InvalidUploadTokenException("만료되었거나 유효하지 않은 업로드 토큰입니다.");
		}
		if (intent.kind() != kind) {
			throw new InvalidUploadTokenException("토큰의 kind 와 요청이 일치하지 않습니다.");
		}
		if (!equalsScope(intent.scope(), scope)) {
			throw new InvalidUploadTokenException("토큰의 scope 와 요청이 일치하지 않습니다.");
		}
		if (Duration.between(intent.issuedAt(), Instant.now()).compareTo(TTL) > 0) {
			throw new InvalidUploadTokenException("업로드 토큰이 만료되었습니다. 다시 시도해주세요.");
		}
		return intent;
	}

	@Scheduled(fixedDelayString = "${upload.intent.prune-interval-ms:300000}")
	public void prune() {
		Instant cutoff = Instant.now().minus(TTL);
		intents.entrySet().removeIf(e -> e.getValue().issuedAt().isBefore(cutoff));
	}

	private static boolean equalsScope(BoardScope a, BoardScope b) {
		if (a.isCommon() && b.isCommon()) return true;
		if (a.isCommon() != b.isCommon()) return false;
		return a.boardId().equals(b.boardId());
	}

	public record Intent(
			SubprogramKind kind,
			BoardScope scope,
			String targetDirectory,
			SubprogramUploadMode uploadMode,
			int fileCount,
			long totalBytes,
			String version,
			Instant issuedAt
	) {

	}


	public record IntentReissue(
			SubprogramKind kind,
			BoardScope scope,
			SubprogramUploadIntentRequest request
	) {

	}
}
