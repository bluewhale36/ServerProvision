package com.example.serverprovision.management.os.service;

import com.example.serverprovision.global.marker.Markable;
import com.example.serverprovision.global.marker.MarkableScanner;
import com.example.serverprovision.global.marker.ResourceType;
import com.example.serverprovision.management.os.repository.OSImageRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * S5-2-3+ — OSImage 도메인 어댑터. <strong>메타 자원</strong> (디렉토리/파일 없음) 이므로
 * 마커 / reconciliation / trash 이동 흐름은 적용하지 않고, 휴지통 페이지 노출에 필요한 lifecycle
 * 메타만 SPI 로 노출한다.
 *
 * <p>대부분 SPI 메서드는 default 빈 구현 그대로. {@link #findTrashed()} 만 override —
 * is_deleted=true 인 OSImage 를 가져와 Markable 목록으로 반환.</p>
 */
@Service
public class OSImageMarkableScanner implements MarkableScanner {

    private final OSImageRepository osImageRepository;
    private final org.springframework.beans.factory.ObjectProvider<OSImageService> osImageServiceProvider;

    public OSImageMarkableScanner(OSImageRepository osImageRepository,
                                  org.springframework.beans.factory.ObjectProvider<OSImageService> osImageServiceProvider) {
        this.osImageRepository = osImageRepository;
        this.osImageServiceProvider = osImageServiceProvider;
    }

    @Override
    public ResourceType supportedType() {
        return ResourceType.OS_IMAGE;
    }

    /** 메타 자원 — 마커 인벤토리 없음. */
    @Override
    public List<Markable> findActiveMarkables() {
        return Collections.emptyList();
    }

    /** 메타 자원 — soft-deleted ID 셋은 reconciliation 의 ORPHAN 분류 제외용 SPI. 빈 셋 충분. */
    @Override
    public Set<Long> findSoftDeletedResourceIds() {
        return Collections.emptySet();
    }

    /** 메타 자원 — 디스크 path 없음. no-op. */
    @Override
    public void applyDriftedPath(Long resourceId, Path newPath) {
        // 메타 자원에는 path 가 없으므로 호출되지 않아야 함.
    }

    /** 메타 자원 — manifest hash 없음. */
    @Override
    public Optional<String> recomputeManifestHash(Markable markable) {
        return Optional.empty();
    }

    /** 휴지통 페이지 노출용 — is_deleted=true 인 OSImage 를 Markable 로 반환. */
    @Override
    @Transactional(readOnly = true)
    public List<Markable> findTrashed() {
        return osImageRepository.findAllByIsDeletedTrue().stream()
                .<Markable>map(o -> o)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Markable> findTrashedById(Long resourceId) {
        return osImageRepository.findByIdAndIsDeletedTrue(resourceId).<Markable>map(o -> o);
    }

    /** 휴지통 페이지 복원 액션 — OSImageService.restore 위임. cascade 옵션 지원. */
    @Override
    public void restoreFromTrash(Long resourceId, boolean cascade) {
        osImageServiceProvider.getObject().restore(resourceId, cascade);
    }

    /** cascade 옵션 없는 default 도 동일 위임 (cascade=false). */
    @Override
    public void restoreFromTrash(Long resourceId) {
        osImageServiceProvider.getObject().restore(resourceId, false);
    }

    /** 휴지통 영구삭제 — OSImageService.purgeImage 위임 (자식 ISO sidecar 정리 + DB row 제거). */
    @Override
    public void purgeFromTrash(Long resourceId) {
        osImageServiceProvider.getObject().purgeImage(resourceId);
    }

    /** 휴지통 cascade preview — soft-deleted 자식 ISO 의 파일명 list. */
    @Override
    @Transactional(readOnly = true)
    public List<String> findDeletedChildLabels(Long resourceId) {
        return osImageRepository.findById(resourceId)
                .map(image -> image.getIsos().stream()
                        .filter(iso -> iso.isDeleted())
                        .map(iso -> iso.getIsoPath().replaceAll(".*/", ""))
                        .collect(Collectors.toList()))
                .orElse(Collections.emptyList());
    }
}
