package com.example.serverprovision.management.bios.service;

import com.example.serverprovision.global.lifecycle.LifecycleStage;
import com.example.serverprovision.management.bios.dto.request.BiosUploadIntentRequest;
import com.example.serverprovision.management.bios.dto.response.BiosUploadIntentResponse;
import com.example.serverprovision.management.bios.enums.BiosUploadMode;
import com.example.serverprovision.management.bios.exception.DuplicateBiosVersionException;
import com.example.serverprovision.management.bios.repository.BiosRepository;
import com.example.serverprovision.global.security.PathPolicyService;
import com.example.serverprovision.management.common.filesystem.service.TargetDirectoryPolicyService;
import com.example.serverprovision.management.common.nudge.dto.PreExistingMatchInfo;
import com.example.serverprovision.management.board.exception.BoardModelNotFoundException;
import com.example.serverprovision.management.board.repository.BoardModelRepository;
import com.example.serverprovision.management.os.exception.InvalidUploadTokenException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

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

    private final ConcurrentMap<String, Intent> intents = new ConcurrentHashMap<>();

    public BiosUploadIntentResponse issue(Long boardId, BiosUploadIntentRequest request) {
        boardModelRepository.findByIdAndIsDeletedFalse(boardId)
                .orElseThrow(() -> new BoardModelNotFoundException(boardId));

        // 활성 동일 (board, version) 중복 — 하드 거절
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

        // MK2 (단계 A) — 메타가 같은 기존 자원 사전 매칭. SoftDeleted/Deprecated/Active 무관.
        // 활성 (board, version) 은 위에서 이미 거절했으므로 본 lookup 의 ACTIVE 매칭은 발생하지 않는다 —
        // 결과는 항상 SoftDeleted 또는 Deprecated. 사용자에게 "휴지통/Deprecated 에 같은 메타가 있으니
        // 진행하면 단계 B 에서 nudge 결정이 필요할 수 있다" 안내용.
        PreExistingMatchInfo preExistingMatch = biosRepository
                .findFirstByBoardModel_IdAndVersion(boardId, request.version())
                .map(b -> new PreExistingMatchInfo(
                        b.getId(),
                        LifecycleStage.of(b.isDeprecated(), b.isDeleted()),
                        b.getName(),
                        b.getVersion()))
                .orElse(null);

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
        log.info("[BiosUploadIntentService] issued token={}, boardId={}, mode={}, target={}, preExisting={}",
                token, boardId, request.uploadMode(), request.targetDirectory(),
                preExistingMatch != null ? preExistingMatch.id() : null);
        return new BiosUploadIntentResponse(token, warnings, preExistingMatch);
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

    public int size() { return intents.size(); }

    public record Intent(
            Long boardId,
            String targetDirectory,
            BiosUploadMode uploadMode,
            int fileCount,
            long totalBytes,
            String version,
            String entrypointOverride,
            Instant issuedAt
    ) {}
}
