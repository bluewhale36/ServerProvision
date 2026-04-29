package com.example.serverprovision.management.bios.service;

import com.example.serverprovision.global.marker.Markable;
import com.example.serverprovision.global.marker.MarkableScanner;
import com.example.serverprovision.global.marker.ResourceType;
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
}
