package com.example.serverprovision.management.bios.service;

import com.example.serverprovision.global.marker.Markable;
import com.example.serverprovision.global.marker.MarkableScanner;
import com.example.serverprovision.global.marker.ResourceType;
import com.example.serverprovision.global.trash.GhostEvaluator;
import com.example.serverprovision.global.trash.exception.GhostClearTargetNotGhostException;
import com.example.serverprovision.management.bios.entity.BoardBIOS;
import com.example.serverprovision.management.bios.repository.BiosRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * BIOS_BUNDLE 도메인 어댑터. {@link BoardBIOS} 엔티티를 {@code MarkableScanner} SPI 로 노출한다.
 * <p>BIOS 는 디렉토리 자원(IN_TREE) 이라 {@code recomputeManifestHash} 는 트리 전체 SHA-256 — 내용 변조 시 다른 hash.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BoardBiosMarkableScanner implements MarkableScanner {

    private final BiosRepository biosRepository;
    private final BundleManifestService bundleManifestService;
    private final BiosService biosService;

    @Override
    public ResourceType supportedType() {
        return ResourceType.BIOS_BUNDLE;
    }

    @Override
    @Transactional(readOnly = true)
    public List<Markable> findActiveMarkables() {
        return biosRepository.findAllByIsDeletedFalse().stream()
                .<Markable>map(b -> b)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public Set<Long> findSoftDeletedResourceIds() {
        return biosRepository.findIdsByIsDeletedTrue();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Markable> findActiveMarkableById(Long resourceId) {
        return biosRepository.findById(resourceId)
                .filter(b -> !b.isDeleted())
                .map(b -> b);
    }

    @Override
    @Transactional
    public void applyDriftedPath(Long resourceId, Path newPath) {
        BoardBIOS bios = biosRepository.findById(resourceId)
                .orElseThrow(() -> new IllegalStateException("BIOS not found for drift apply : " + resourceId));
        bios.updateTreeRootPath(newPath.toString());
        log.info("[reconciliation] BIOS treeRootPath 갱신. biosId={}, newPath={}", resourceId, newPath);
    }

    @Override
    public Optional<String> recomputeManifestHash(Markable markable) {
        Path treeRoot = markable.getResourcePath();
        try {
            return Optional.of(bundleManifestService.compute(treeRoot).manifestHash());
        } catch (RuntimeException e) {
            log.warn("[reconciliation] BIOS manifestHash 재계산 실패. biosId={}, path={}, msg={}",
                    markable.getResourceId(), treeRoot, e.getMessage());
            return Optional.empty();
        }
    }

    // ---- MK3 — Trash SPI ---------------------------------------------

    @Override
    @Transactional(readOnly = true)
    public List<Markable> findTrashed() {
        return biosRepository.findByIsDeletedTrueOrderByTrashedAtDesc().stream().<Markable>map(b -> b).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Markable> findTrashedById(Long resourceId) {
        return biosRepository.findById(resourceId).filter(BoardBIOS::isDeleted).<Markable>map(b -> b);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Markable> findTrashedBefore(java.time.Instant threshold) {
        return biosRepository.findByIsDeletedTrueAndTrashedAtBefore(threshold).stream().<Markable>map(b -> b).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<Markable> findTrashedBetween(java.time.Instant start, java.time.Instant end) {
        return biosRepository.findByIsDeletedTrueAndTrashedAtBetween(start, end).stream().<Markable>map(b -> b).toList();
    }

    @Override
    @Transactional
    public void extendTrashTtl(Long resourceId) {
        BoardBIOS bios = biosRepository.findById(resourceId)
                .orElseThrow(() -> new IllegalStateException("BIOS not found for TTL extend: " + resourceId));
        bios.markTrashed(bios.getTrashedPath());
    }

    @Override
    public void restoreFromTrash(Long resourceId) {
        BoardBIOS bios = biosRepository.findById(resourceId)
                .orElseThrow(() -> new IllegalStateException("BIOS not found for trash restore: " + resourceId));
        biosService.restore(bios.getBoardModel().getId(), resourceId);
    }

    @Override
    public void purgeFromTrash(Long resourceId) {
        BoardBIOS bios = biosRepository.findById(resourceId)
                .orElseThrow(() -> new IllegalStateException("BIOS not found for trash purge: " + resourceId));
        biosService.purge(bios.getBoardModel().getId(), resourceId);
    }

    // ---- MK3-1 — Ghost SPI -------------------------------------------

    @Override
    @Transactional(readOnly = true)
    public boolean isGhost(Long resourceId) {
        return biosRepository.findById(resourceId).map(GhostEvaluator::isGhost).orElse(false);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Markable> findGhostMarkables() {
        return biosRepository.findByIsDeletedTrueAndTrashedPathIsNull().stream()
                .filter(GhostEvaluator::isGhost)
                .<Markable>map(b -> b)
                .toList();
    }

    @Override
    @Transactional
    public void applyGhostClear(Long resourceId) {
        BoardBIOS bios = biosRepository.findById(resourceId)
                .orElseThrow(() -> new IllegalStateException("BIOS not found for ghost clear: " + resourceId));
        if (!GhostEvaluator.isGhost(bios)) {
            throw new GhostClearTargetNotGhostException(supportedType().name() + "#" + resourceId);
        }
        biosRepository.delete(bios);
        log.info("[ghost] BIOS row 정리. biosId={}", resourceId);
    }

    @Override
    @Transactional
    public void applyForcedClear(Long resourceId) {
        // MK3-2 (DCM3-2.5) — 사용자 명시 "강제 정리". lifecycle / FS 검증 없이 row hard-delete.
        BoardBIOS bios = biosRepository.findById(resourceId)
                .orElseThrow(() -> new IllegalStateException("BIOS not found for forced clear: " + resourceId));
        biosRepository.delete(bios);
        log.info("[forced-clear] BIOS row 정리. biosId={}", resourceId);
    }
}
