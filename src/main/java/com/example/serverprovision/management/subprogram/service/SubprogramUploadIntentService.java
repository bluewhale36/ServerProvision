package com.example.serverprovision.management.subprogram.service;

import com.example.serverprovision.global.lifecycle.LifecycleStage;
import com.example.serverprovision.global.security.PathPolicyService;
import com.example.serverprovision.management.board.exception.BoardModelNotFoundException;
import com.example.serverprovision.management.board.repository.BoardModelRepository;
import com.example.serverprovision.management.common.filesystem.service.TargetDirectoryPolicyService;
import com.example.serverprovision.management.common.nudge.dto.PreExistingMatchInfo;
import com.example.serverprovision.management.os.exception.InvalidUploadTokenException;
import com.example.serverprovision.management.subprogram.dto.request.SubprogramUploadIntentRequest;
import com.example.serverprovision.management.subprogram.dto.response.SubprogramUploadIntentResponse;
import com.example.serverprovision.management.subprogram.entity.Subprogram;
import com.example.serverprovision.management.subprogram.enums.SubprogramKind;
import com.example.serverprovision.management.subprogram.enums.SubprogramUploadMode;
import com.example.serverprovision.management.subprogram.exception.DuplicateSubprogramVersionException;
import com.example.serverprovision.management.subprogram.repository.SubprogramRepository;
import com.example.serverprovision.management.subprogram.vo.BoardScope;
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

    private final ConcurrentMap<String, Intent> intents = new ConcurrentHashMap<>();

    public SubprogramUploadIntentResponse issue(SubprogramKind kind, BoardScope scope, SubprogramUploadIntentRequest request) {
        // 보드별이면 부모 보드 활성 검증
        if (!scope.isCommon()) {
            boardModelRepository.findByIdAndIsDeletedFalse(scope.boardId())
                    .orElseThrow(() -> new BoardModelNotFoundException(scope.boardId()));
        }

        // 활성 (kind, scope, name=intent 에 없으므로 version+ 만 사전 체크는 불가). version 중복은 본 등록에서 다시 체크.
        // 그래도 사용자 친화적으로 사전 안내 위해 (kind, scope, version) 활성 자원 1 건이라도 있으면 충돌 가능성 알림.
        // BIOS/BMC 는 (boardId, version) 단위 중복이라 단순했지만, Subprogram 은 name 까지 들어가야 정확.
        // → name 사전 체크는 본 업로드에서. intent 단계는 path 점유와 보드 검증만 수행.

        // S3 — allowlist 검증
        Path targetDir = pathPolicyService.assertWritablePath(request.targetDirectory());
        targetDirectoryPolicyService.validateForIntent(targetDir, request.allowCreateDirectory());

        // 트리 충돌 사전 안내
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
        intents.put(token, new Intent(
                kind,
                scope,
                request.targetDirectory(),
                request.uploadMode(),
                request.fileCount(),
                request.totalBytes(),
                request.version(),
                Instant.now()
        ));

        // MK2 단계 A — 동일 메타 (kind, scope, name=null/version) 의 기존 자원이 있으면 preExistingMatch 동봉.
        // intent 단계에선 name 정보가 없으므로 (kind, scope, version) 만으로 first-hit lookup.
        PreExistingMatchInfo preExistingMatch = lookupPreExistingMatch(kind, scope, request.version());

        return new SubprogramUploadIntentResponse(token, warnings, preExistingMatch);
    }

    /**
     * MK2 단계 A — 동일 (kind, scope, version) 의 기존 자원 탐색. 활성 / Deprecated / SoftDeleted 어떤 stage 든
     * 1건 이상 있으면 첫 후보를 반환. 없으면 null.
     *
     * <p>intent 단계에는 name 이 폼에 없으므로 (kind, scope, version) 단위로 충돌 후보를 사전 안내한다.
     * 본 정보는 단순 안내용이며 사용자가 dismiss 하면 단계 B 에서 본격 충돌 nudge 가 다시 결정.</p>
     */
    private PreExistingMatchInfo lookupPreExistingMatch(SubprogramKind kind, BoardScope scope, String version) {
        List<Subprogram> candidates = scope.isCommon()
                ? subprogramRepository.findByKindAndCommonScope(kind)
                : subprogramRepository.findByKindAndBoardId(kind, scope.boardId());
        return candidates.stream()
                .filter(s -> version != null && version.equals(s.getVersion()))
                .findFirst()
                .map(s -> new PreExistingMatchInfo(
                        s.getId(),
                        LifecycleStage.of(s.isDeprecated(), s.isDeleted()),
                        s.getName(),
                        s.getVersion()))
                .orElse(null);
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

    /**
     * Intent 토큰의 메타.
     */
    public record Intent(
            SubprogramKind kind,
            BoardScope scope,
            String targetDirectory,
            SubprogramUploadMode uploadMode,
            int fileCount,
            long totalBytes,
            String version,
            Instant issuedAt
    ) {}
}
