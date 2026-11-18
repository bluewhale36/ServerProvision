package com.example.serverprovision.execution.installtree.service;

import com.example.serverprovision.execution.installtree.enums.TreeFailureReason;
import com.example.serverprovision.execution.installtree.vo.InstallTree;
import com.example.serverprovision.execution.installtree.vo.TreeAcquisition;
import com.example.serverprovision.execution.installtree.vo.TreeRelease;
import com.example.serverprovision.management.os.exception.ExtractionFailedException;
import com.example.serverprovision.management.os.service.iso.IsoPreparationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * ISO 하나를 작업 트리로 푸는 전용 실행기 작업: 공간 확인 → staging 에 풀기 → 트리 확인 → 원자적 게시.
 * 결과는 future 로만 돌려준다 — 매니저를 되부르지 않아 생성자 순환이 없다. 예상한 실패는 값({@code Failed})이고,
 * 그 밖의 예외는 future 를 예외로 끝내 매니저가 {@code EXTRACTION_FAILED} 로 닫는다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class InstallTreeExtractor {

    /** 필요 공간 = ISO 크기 × 이 배수(풀린 트리는 ISO 보다 약간 크고, 파일 시스템 여유도 남긴다). */
    static final double SPACE_HEADROOM = 1.2;

    private final IsoPreparationService isoPreparationService;
    private final InstallTreeInspector inspector;
    private final InstallTreeStore store;

    @Async("installTreeExecutor")
    public CompletableFuture<TreeAcquisition> extract(long isoId, Path isoFile) {
        return CompletableFuture.completedFuture(extractNow(isoId, isoFile));
    }

    TreeAcquisition extractNow(long isoId, Path isoFile) {
        Path staging = null;
        try {
            long required = (long) (Files.size(isoFile) * SPACE_HEADROOM);
            long usable = store.usableSpace();
            if (usable < required) {
                log.warn("[install-tree] 공간 부족. isoId={}, required={}, usable={}", isoId, required, usable);
                return TreeAcquisition.failed(TreeFailureReason.NOT_ENOUGH_SPACE);
            }
            staging = store.newStagingDir();
            long started = System.nanoTime();
            log.info("[install-tree] 추출 시작. isoId={}, iso={}, staging={}", isoId, isoFile, staging);
            isoPreparationService.extractInto(isoFile, staging);
            Optional<TreeRelease> release = inspector.inspect(staging);
            if (release.isEmpty()) {
                return TreeAcquisition.failed(TreeFailureReason.NOT_RHEL_TREE);
            }
            Path published = store.publish(staging, isoId);
            staging = null;
            log.info("[install-tree] 추출 완료. isoId={}, release={}, tree={}, 소요={}s",
                    isoId, release.get().label(), published, (System.nanoTime() - started) / 1_000_000_000L);
            return TreeAcquisition.ready(new InstallTree(published, release.get()));
        } catch (IOException | ExtractionFailedException e) {
            log.warn("[install-tree] 추출 실패. isoId={}, iso={}, msg={}", isoId, isoFile, e.getMessage());
            return TreeAcquisition.failed(TreeFailureReason.EXTRACTION_FAILED);
        } finally {
            if (staging != null) {
                store.delete(staging);
            }
        }
    }
}
