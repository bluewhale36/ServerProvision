package com.example.serverprovision.management.bmc.service;

import com.example.serverprovision.global.lifecycle.LifecycleStage;
import com.example.serverprovision.global.security.PathPolicyService;
import com.example.serverprovision.management.bmc.dto.request.BmcUploadIntentRequest;
import com.example.serverprovision.management.bmc.dto.response.BmcUploadIntentResponse;
import com.example.serverprovision.management.bmc.entity.BoardBMC;
import com.example.serverprovision.management.bmc.enums.BmcUploadMode;
import com.example.serverprovision.management.bmc.exception.BmcNudgeRequiredException;
import com.example.serverprovision.management.bmc.exception.DuplicateBmcVersionException;
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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.stream.Stream;

@Slf4j
@Service
@RequiredArgsConstructor
public class BmcUploadIntentService {

    private static final Duration TTL = Duration.ofHours(2);

    private final BoardModelRepository boardModelRepository;
    private final com.example.serverprovision.management.bmc.repository.BmcRepository bmcRepository;
    private final TargetDirectoryPolicyService targetDirectoryPolicyService;
    private final PathPolicyService pathPolicyService;
    private final NudgeRegistry nudgeRegistry;

    private final ConcurrentMap<String, Intent> intents = new ConcurrentHashMap<>();

    /**
     * MK2 WAVE 2 — intent 시점 메타 (board, version) 사전 검출. 충돌 시 BmcNudgeRequiredException throw.
     */
    public BmcUploadIntentResponse issue(Long boardId, BmcUploadIntentRequest request) {
        boardModelRepository.findByIdAndIsDeletedFalse(boardId)
                .orElseThrow(() -> new BoardModelNotFoundException(boardId));

        if (bmcRepository.existsByBoardModel_IdAndVersionAndIsDeletedFalse(boardId, request.version())) {
            throw new DuplicateBmcVersionException(boardId, request.version());
        }

        List<BoardBMC> metaCandidates = collectMetaNudgeCandidates(boardId, request.version());
        if (!metaCandidates.isEmpty()) {
            throw new BmcNudgeRequiredException(
                    "동일한 (보드, 버전) 의 BMC 자원이 휴지통 또는 Deprecated 상태로 발견됐습니다. 진행 방법을 선택하세요.",
                    registerIntentNudge(boardId, request, metaCandidates),
                    toConflictEntries(metaCandidates));
        }

        return issueAfterNudge(boardId, request);
    }

    /**
     * MK2 WAVE 2 — nudge proceed/replace 후 호출. 메타 검사를 건너뛰고 token 발급.
     */
    public BmcUploadIntentResponse issueAfterNudge(Long boardId, BmcUploadIntentRequest request) {
        boardModelRepository.findByIdAndIsDeletedFalse(boardId)
                .orElseThrow(() -> new BoardModelNotFoundException(boardId));

        if (bmcRepository.existsByBoardModel_IdAndVersionAndIsDeletedFalse(boardId, request.version())) {
            throw new DuplicateBmcVersionException(boardId, request.version());
        }

        Path targetDir = pathPolicyService.assertWritablePath(request.targetDirectory());
        targetDirectoryPolicyService.validateForIntent(targetDir, request.allowCreateDirectory());

        List<String> warnings = new ArrayList<>();
        if (request.totalBytes() == 0) {
            warnings.add("총 바이트가 0 으로 보고되었습니다. 업로드 전 파일 상태를 확인하세요.");
        }
        if (request.uploadMode() == BmcUploadMode.FOLDER && request.fileCount() == 0) {
            warnings.add("파일 수가 0 으로 보고되었습니다. 폴더가 비어있을 수 있습니다.");
        }

        String token = UUID.randomUUID().toString();
        intents.put(token, new Intent(
                boardId,
                request.targetDirectory(),
                request.uploadMode(),
                request.fileCount(),
                request.totalBytes(),
                request.version(),
                request.entrypointRelativePath(),
                Instant.now()
        ));
        // preExistingMatch 는 deprecated — meta 충돌은 이제 NUDGE_REQUIRED 로 분기되므로 항상 null.
        return new BmcUploadIntentResponse(token, warnings, null);
    }

    private List<BoardBMC> collectMetaNudgeCandidates(Long boardId, String version) {
        return Stream.concat(
                        bmcRepository.findAllByBoardModel_IdAndVersionAndIsDeletedTrue(boardId, version).stream(),
                        bmcRepository.findAllByBoardModel_IdAndVersionAndIsDeprecatedTrueAndIsDeletedFalse(boardId, version).stream())
                .toList();
    }

    private NudgeSession registerIntentNudge(Long boardId, BmcUploadIntentRequest request, List<BoardBMC> candidates) {
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
                NudgeResourceType.BMC,
                boardId,
                candidates.stream().map(BoardBMC::getId).toList(),
                new IntentMetaNudgePayload(attributes));
    }

    private List<NudgeConflictEntry> toConflictEntries(List<BoardBMC> candidates) {
        return candidates.stream()
                .map(b -> new NudgeConflictEntry(
                        b.getId(),
                        LifecycleStage.of(b.isDeprecated(), b.isDeleted()),
                        b.getManifestHash(),
                        b.getName(),
                        b.getVersion(),
                        Instant.now()))
                .toList();
    }

    /**
     * MK2 WAVE 2 — IntentMetaNudgePayload.attributes 로부터 BmcUploadIntentRequest 재구성.
     */
    public BmcUploadIntentRequest reconstructRequestFromAttributes(Map<String, String> attributes) {
        return new BmcUploadIntentRequest(
                attributes.get("targetDirectory"),
                BmcUploadMode.valueOf(attributes.get("uploadMode")),
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

    public record Intent(
            Long boardId,
            String targetDirectory,
            BmcUploadMode uploadMode,
            int fileCount,
            long totalBytes,
            String version,
            String entrypointOverride,
            Instant issuedAt
    ) {}
}
