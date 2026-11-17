package com.example.serverprovision.management.subprogram.service;

import com.example.serverprovision.management.os.enums.OSName;

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
	 * 업로드 전 판정 — 등록 본체와 같은 키 (종류, 범위, 이름, 버전)로 본다(HF30-2). 살아 있는 같은 키는 상태별 안내로 막고, 휴지통의
	 * 같은 키만 중복 확인 창으로 묻는다. 종전에는 이 단계가 이름 없이 (종류, 범위, 버전)으로 판정해, 창의 [그래도 등록] 뒤에도 등록 본체가
	 * 휴지통의 같은 키를 거절하며 업로드가 끝난 뒤 409 로 실패했다(docs C-10).
	 */
	public SubprogramUploadIntentResponse issue(SubprogramKind kind, BoardScope scope, SubprogramUploadIntentRequest request) {
		if (!scope.isCommon()) {
			boardModelRepository.findByIdAndIsDeletedFalse(scope.boardId())
					.orElseThrow(() -> new BoardModelNotFoundException(scope.boardId()));
		}

		SubprogramGuards.assertKeyAvailable(subprogramRepository, kind, scope, request.name(), request.version());

		List<Subprogram> metaCandidates = subprogramRepository.findIntentNudgeCandidates(
				kind, scope.isCommon() ? null : scope.boardId(), request.name(), request.version());
		if (!metaCandidates.isEmpty()) {
			throw new SubprogramNudgeRequiredException(
					"종류, 적용 범위, 이름, 버전이 같은 자원이 휴지통에 있습니다. 진행 방법을 선택해야 합니다.",
					registerIntentNudge(kind, scope, request, metaCandidates),
					toConflictEntries(metaCandidates)
			);
		}

		return issueAfterNudge(kind, scope, request);
	}

	/**
	 * MK2 WAVE 2 — nudge proceed/replace 후 호출. 휴지통 후보 판정은 건너뛰고 살아 있는 같은 키만 다시 본다.
	 */
	public SubprogramUploadIntentResponse issueAfterNudge(SubprogramKind kind, BoardScope scope, SubprogramUploadIntentRequest request) {
		if (!scope.isCommon()) {
			boardModelRepository.findByIdAndIsDeletedFalse(scope.boardId())
					.orElseThrow(() -> new BoardModelNotFoundException(scope.boardId()));
		}
		// 창 진행 사이에 다른 사용자가 같은 키를 등록했을 수 있어 다시 본다.
		SubprogramGuards.assertKeyAvailable(subprogramRepository, kind, scope, request.name(), request.version());

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
			warnings.add("전체 크기가 0 바이트로 보고되었습니다. 업로드 전 파일 상태를 확인해야 합니다.");
		}
		if (request.uploadMode() == SubprogramUploadMode.FOLDER && request.fileCount() == 0) {
			warnings.add("파일 수가 0 으로 보고되었습니다. 폴더가 비어 있을 수 있습니다.");
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
						request.osName(),
						Instant.now()
				)
		);

		return new SubprogramUploadIntentResponse(token, warnings, null);
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
		attributes.put("name", request.name());
		attributes.put("version", request.version());
		attributes.put("targetDirectory", request.targetDirectory());
		attributes.put("uploadMode", request.uploadMode().name());
		attributes.put("fileCount", String.valueOf(request.fileCount()));
		attributes.put("totalBytes", String.valueOf(request.totalBytes()));
		attributes.put("allowCreateDirectory", String.valueOf(request.allowCreateDirectory()));
		attributes.put("osName", request.osName() == null ? "" : request.osName().name());
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
				attributes.get("name"),
				attributes.get("targetDirectory"),
				SubprogramUploadMode.valueOf(attributes.get("uploadMode")),
				Integer.parseInt(attributes.get("fileCount")),
				Long.parseLong(attributes.get("totalBytes")),
				attributes.get("version"),
				Boolean.parseBoolean(attributes.getOrDefault("allowCreateDirectory", "false")),
				attributes.getOrDefault("osName", "").isBlank() ? null : OSName.valueOf(attributes.get("osName"))
		);
		return new IntentReissue(kind, scope, request);
	}

	public Intent consume(SubprogramKind kind, BoardScope scope, String token) {
		if (token == null || token.isBlank()) {
			throw new InvalidUploadTokenException("업로드 준비 정보가 없습니다. 페이지를 새로 고친 뒤 다시 시도해야 합니다.");
		}
		Intent intent = intents.remove(token);
		if (intent == null) {
			throw new InvalidUploadTokenException("업로드 준비 정보가 만료되었거나 올바르지 않습니다. 페이지를 새로 고친 뒤 다시 시도해야 합니다.");
		}
		if (intent.kind() != kind) {
			throw new InvalidUploadTokenException("업로드 준비 정보의 자원 종류가 요청과 다릅니다. 페이지를 새로 고친 뒤 다시 시도해야 합니다.");
		}
		if (!equalsScope(intent.scope(), scope)) {
			throw new InvalidUploadTokenException("업로드 준비 정보의 적용 범위가 요청과 다릅니다. 페이지를 새로 고친 뒤 다시 시도해야 합니다.");
		}
		if (Duration.between(intent.issuedAt(), Instant.now()).compareTo(TTL) > 0) {
			throw new InvalidUploadTokenException("업로드 준비 정보가 만료되었습니다. 다시 시도해야 합니다.");
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
			OSName osName,
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
