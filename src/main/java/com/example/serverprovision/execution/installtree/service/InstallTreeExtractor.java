package com.example.serverprovision.execution.installtree.service;

import com.example.serverprovision.execution.installtree.enums.InstallMediaLayout;
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
 * ISO 하나를 작업 트리로 꺼내는 전용 실행기 작업: 공간 확인, staging 에 꺼내기, 매체 판별, 원자적 게시.
 * 얼마나 꺼내고 무엇으로 판별하는지는 {@link InstallMediaLayout} 이 정한다. 결과는 future 로만 돌려준다.
 * 매니저를 되부르지 않아 생성자 순환이 없다. 예상한 실패는 값({@code Failed})이고, 그 밖의 예외는 future 를 예외로 끝내
 * 매니저가 {@code EXTRACTION_FAILED} 로 닫는다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class InstallTreeExtractor {

    private final IsoPreparationService isoPreparationService;
    private final InstallTreeStore store;

    @Async("installTreeExecutor")
    public CompletableFuture<TreeAcquisition> extract(long isoId, Path isoFile, InstallMediaLayout layout) {
        return CompletableFuture.completedFuture(extractNow(isoId, isoFile, layout));
    }

    TreeAcquisition extractNow(long isoId, Path isoFile, InstallMediaLayout layout) {
        Path staging = null;
        try {
            long required = layout.requiredSpace(Files.size(isoFile));
            long usable = store.usableSpace();
            if (usable < required) {
                log.warn("[install-tree] 공간 부족. isoId={}, layout={}, required={}, usable={}", isoId, layout, required, usable);
                return TreeAcquisition.failed(TreeFailureReason.NOT_ENOUGH_SPACE);
            }
            staging = store.newStagingDir();
            long started = System.nanoTime();
            log.info("[install-tree] 추출 시작. isoId={}, layout={}, iso={}, staging={}", isoId, layout, isoFile, staging);
            layout.extract(isoPreparationService, isoFile, staging);
            Optional<TreeRelease> release = layout.inspect(staging);
            if (release.isEmpty()) {
                log.info("[install-tree] 매체 판별 실패. isoId={}, layout={}, reason={}", isoId, layout, layout.notRecognized());
                return TreeAcquisition.failed(layout.notRecognized());
            }
            Path published = store.publish(staging, isoId);
            staging = null;
            log.info("[install-tree] 추출 완료. isoId={}, release={}, tree={}, 소요={}s",
                    isoId, release.get().label(), published, (System.nanoTime() - started) / 1_000_000_000L);
            return TreeAcquisition.ready(new InstallTree(published, release.get(), isoFile));
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
