package com.example.serverprovision.management.common.filesystem.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.stream.Stream;

/**
 * BIOS/BMC 번들 트리 정리 공통 helper.
 * 실제 feature wiring 과 테스트 보강은 MA4-1-5 CP4 에서 마무리한다.
 */
@Slf4j
@Service
public class BundleTreeCleanupService {

    public void purgeExistingTree(Path treeRoot, String warnContext) {
        if (!Files.exists(treeRoot)) return;
        try (Stream<Path> walker = Files.walk(treeRoot)) {
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
        } catch (RuntimeException cleanupError) {
            log.warn("[{}] 실패 후 대상 디렉토리 정리 중 추가 오류. path={}, cleanupMsg={}",
                    resourceLabel, targetDir, cleanupError.getMessage());
        }
    }
}
