package com.example.serverprovision.management.bmc.service;

import com.example.serverprovision.global.marker.service.ProvisionMarkerService;
import com.example.serverprovision.management.common.filesystem.service.TargetDirectoryPolicyService;
import com.example.serverprovision.management.board.exception.BoardModelNotFoundException;
import com.example.serverprovision.management.board.repository.BoardModelRepository;
import com.example.serverprovision.management.bmc.dto.request.BmcUploadIntentRequest;
import com.example.serverprovision.management.bmc.dto.response.BmcUploadIntentResponse;
import com.example.serverprovision.management.bmc.enums.BmcUploadMode;
import com.example.serverprovision.management.bmc.exception.DuplicateBmcVersionException;
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

@Slf4j
@Service
@RequiredArgsConstructor
public class BmcUploadIntentService {

    private static final Duration TTL = Duration.ofHours(2);

    private final BoardModelRepository boardModelRepository;
    private final com.example.serverprovision.management.bmc.repository.BmcRepository bmcRepository;
    private final TargetDirectoryPolicyService targetDirectoryPolicyService;

    private final ConcurrentMap<String, Intent> intents = new ConcurrentHashMap<>();

    public BmcUploadIntentResponse issue(Long boardId, BmcUploadIntentRequest request) {
        boardModelRepository.findByIdAndIsDeletedFalse(boardId)
                .orElseThrow(() -> new BoardModelNotFoundException(boardId));

        if (bmcRepository.existsByBoardModel_IdAndVersionAndIsDeletedFalse(boardId, request.version())) {
            throw new DuplicateBmcVersionException(boardId, request.version());
        }

        Path targetDir = Path.of(request.targetDirectory());
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
        return new BmcUploadIntentResponse(token, warnings);
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
