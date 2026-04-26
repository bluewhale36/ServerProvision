package com.example.serverprovision.management.bmc.service;

import com.example.serverprovision.global.marker.Markable;
import com.example.serverprovision.global.marker.MarkableScanner;
import com.example.serverprovision.global.marker.ResourceType;
import com.example.serverprovision.management.bmc.entity.BoardBMC;
import com.example.serverprovision.management.bmc.repository.BmcRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * BMC_FIRMWARE 도메인 어댑터.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BoardBmcMarkableScanner implements MarkableScanner {

    private final BmcRepository bmcRepository;
    private final com.example.serverprovision.management.bios.service.BundleManifestService bundleManifestService;

    @Override
    public ResourceType supportedType() {
        return ResourceType.BMC_FIRMWARE;
    }

    @Override
    @Transactional(readOnly = true)
    public List<Markable> findActiveMarkables() {
        return bmcRepository.findAllByIsDeletedFalse().stream()
                .<Markable>map(b -> b)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public Set<Long> findSoftDeletedResourceIds() {
        return bmcRepository.findIdsByIsDeletedTrue();
    }

    @Override
    @Transactional
    public void applyDriftedPath(Long resourceId, Path newPath) {
        BoardBMC bmc = bmcRepository.findById(resourceId)
                .orElseThrow(() -> new IllegalStateException("BMC not found for drift apply : " + resourceId));
        bmc.updateTreeRootPath(newPath.toString());
        log.info("[reconciliation] BMC treeRootPath 갱신. bmcId={}, newPath={}", resourceId, newPath);
    }

    @Override
    public Optional<String> recomputeManifestHash(Markable markable) {
        try {
            return Optional.of(bundleManifestService.compute(markable.getResourcePath()).manifestHash());
        } catch (RuntimeException e) {
            log.warn("[reconciliation] BMC manifestHash 재계산 실패. bmcId={}, path={}, msg={}",
                    markable.getResourceId(), markable.getResourcePath(), e.getMessage());
            return Optional.empty();
        }
    }
}
