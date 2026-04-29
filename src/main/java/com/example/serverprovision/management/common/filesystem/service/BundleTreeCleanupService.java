package com.example.serverprovision.management.common.filesystem.service;

import com.example.serverprovision.global.security.PathPolicyService;
import com.example.serverprovision.global.security.config.FileSystemSecurityProperties;
import com.example.serverprovision.global.security.exception.PathOutsideAllowedRootsException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.stream.Stream;

/**
 * BIOS/BMC/Subprogram 번들 트리 정리 공통 helper.
 *
 * <p>S3.1 (A2) — DB 가 신뢰 가능한 path 만 저장한다는 가정이 깨지는 경우를 막기 위해
 * {@link #purgeExistingTree(Path, String)} 진입부에서 {@link PathPolicyService#assertWritablePath} 가드를 통과시킨다.
 * allowed-roots 밖의 path 가 (역사 데이터 / 침해된 어드민 / 프로필 변경 등으로) DB 에 저장돼 있어도
 * 시스템 디렉토리 재귀 삭제로 이어지지 않는다.</p>
 *
 * <p>S3.1 (A3) — {@code Files.walk} 호출에 {@code maxDepth} 인자를 명시. 무한 재귀 / 깊은 nested directory 폭주 방어.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BundleTreeCleanupService {

    private final PathPolicyService pathPolicyService;
    private final FileSystemSecurityProperties fileSystemSecurityProperties;

    public void purgeExistingTree(Path treeRoot, String warnContext) {
        if (treeRoot == null) return;
        // S3.1 (A2) — allowed-roots 밖의 path 면 거절. DB 가 변형된 흔적을 그대로 walk + delete 하지 않는다.
        try {
            pathPolicyService.assertWritablePath(treeRoot.toString());
        } catch (PathOutsideAllowedRootsException e) {
            log.warn("[{}] purgeExistingTree 가드 거절 (allowed-roots 밖) — 작업 중단. path={}",
                    warnContext, treeRoot);
            throw e;
        }
        if (!Files.exists(treeRoot)) return;
        // S3.1 (A3) — maxDepth 인자 명시로 무한 재귀 / 깊은 nested directory 방어.
        try (Stream<Path> walker = Files.walk(treeRoot, fileSystemSecurityProperties.maxDepth())) {
            walker.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException ignored) {
                    // best-effort
                }
            });
        } catch (IOException e) {
            log.warn("[{}] 기존 트리 삭제 중 IO 문제 : {}", warnContext, treeRoot, e);
        }
    }

    public void cleanupFailedUpload(Path targetDir,
                                    String warnContext,
                                    String resourceLabel,
                                    RuntimeException cause) {
        try {
            purgeExistingTree(targetDir, warnContext);
            log.warn("[{}] 실패 후 대상 디렉토리 정리 완료. path={}, cause={}",
                    resourceLabel, targetDir, cause.getMessage());
        } catch (com.example.serverprovision.global.security.exception.PathOutsideAllowedRootsException
                | com.example.serverprovision.global.security.exception.PathTraversalException
                cleanupGuard) {
            // S3.2 (K1) — 보안 가드 예외는 swallow 하지 않음. 가드 발동 사실이 호출자 / 사용자 응답에 도달.
            log.warn("[{}] 실패 후 대상 디렉토리 정리에서 보안 가드 거절. path={}, cause={}",
                    resourceLabel, targetDir, cleanupGuard.getMessage());
            throw cleanupGuard;
        } catch (RuntimeException cleanupError) {
            log.warn("[{}] 실패 후 대상 디렉토리 정리 중 추가 오류. path={}, cleanupMsg={}",
                    resourceLabel, targetDir, cleanupError.getMessage());
        }
    }
}
