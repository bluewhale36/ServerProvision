package com.example.serverprovision.management.os.service;

import com.example.serverprovision.global.marker.Markable;
import com.example.serverprovision.global.marker.MarkableScanner;
import com.example.serverprovision.global.marker.ResourceType;
import com.example.serverprovision.global.trash.GhostEvaluator;
import com.example.serverprovision.global.trash.exception.GhostClearTargetNotGhostException;
import com.example.serverprovision.management.os.entity.ISO;
import com.example.serverprovision.management.os.repository.ISORepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * OS_ISO 도메인 어댑터. {@link ISO} 엔티티를 {@code MarkableScanner} SPI 로 노출한다.
 * <p>ISO 는 단일 파일 자원(SIDECAR) 이라 {@code recomputeManifestHash} 는 SHA-256(file bytes) — ISO 가 다른 파일로 교체되면 다른 hash.
 * 12GB 면 수십 초~수 분 소요라 deep scan 전용.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class IsoMarkableScanner implements MarkableScanner {

    private final ISORepository isoRepository;
    private final OSImageService osImageService;

    @Override
    public ResourceType supportedType() {
        return ResourceType.OS_ISO;
    }

    @Override
    @Transactional(readOnly = true)
    public List<Markable> findActiveMarkables() {
        return isoRepository.findAllByIsDeletedFalse().stream()
                .<Markable>map(i -> i)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public Set<Long> findSoftDeletedResourceIds() {
        return isoRepository.findIdsByIsDeletedTrue();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Markable> findActiveMarkableById(Long resourceId) {
        return isoRepository.findById(resourceId)
                .filter(i -> !i.isDeleted())
                .map(i -> i);
    }

    @Override
    @Transactional
    public void applyDriftedPath(Long resourceId, Path newPath) {
        ISO iso = isoRepository.findById(resourceId)
                .orElseThrow(() -> new IllegalStateException("ISO not found for drift apply : " + resourceId));
        iso.updateIsoPath(newPath.toString());
        log.info("[reconciliation] ISO isoPath 갱신. isoId={}, newPath={}", resourceId, newPath);
    }

    @Override
    public Optional<String> recomputeManifestHash(Markable markable) {
        Path file = markable.getResourcePath();
        if (!Files.isRegularFile(file)) {
            return Optional.empty();
        }
        try {
            return Optional.of(sha256Hex(file));
        } catch (IOException | NoSuchAlgorithmException e) {
            log.warn("[reconciliation] ISO manifestHash 재계산 실패. isoId={}, path={}, msg={}",
                    markable.getResourceId(), file, e.getMessage());
            return Optional.empty();
        }
    }

    // ---- MK3 — Trash SPI ---------------------------------------------

    @Override
    @Transactional(readOnly = true)
    public List<Markable> findTrashed() {
        return isoRepository.findByIsDeletedTrueOrderByTrashedAtDesc().stream().<Markable>map(i -> i).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<Markable> findTrashedBefore(java.time.Instant threshold) {
        return isoRepository.findByIsDeletedTrueAndTrashedAtBefore(threshold).stream().<Markable>map(i -> i).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<Markable> findTrashedBetween(java.time.Instant start, java.time.Instant end) {
        return isoRepository.findByIsDeletedTrueAndTrashedAtBetween(start, end).stream().<Markable>map(i -> i).toList();
    }

    @Override
    @Transactional
    public void extendTrashTtl(Long resourceId) {
        ISO iso = isoRepository.findById(resourceId)
                .orElseThrow(() -> new IllegalStateException("ISO not found for TTL extend: " + resourceId));
        // trashed_path 그대로 두고 trashed_at 만 갱신 → expiresAt = trashedAt + TTL 가 +TTL 일 연장.
        iso.markTrashed(iso.getTrashedPath());
    }

    @Override
    public void restoreFromTrash(Long resourceId) {
        ISO iso = isoRepository.findById(resourceId)
                .orElseThrow(() -> new IllegalStateException("ISO not found for trash restore: " + resourceId));
        osImageService.restoreISO(iso.getOsImage().getId(), resourceId);
    }

    @Override
    public void purgeFromTrash(Long resourceId) {
        ISO iso = isoRepository.findById(resourceId)
                .orElseThrow(() -> new IllegalStateException("ISO not found for trash purge: " + resourceId));
        osImageService.purgeIso(iso.getOsImage().getId(), resourceId);
    }

    // ---- MK3-1 — Ghost SPI -------------------------------------------

    @Override
    @Transactional(readOnly = true)
    public boolean isGhost(Long resourceId) {
        return isoRepository.findById(resourceId).map(GhostEvaluator::isGhost).orElse(false);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Markable> findGhostMarkables() {
        return isoRepository.findByIsDeletedTrueAndTrashedPathIsNull().stream()
                .filter(GhostEvaluator::isGhost)
                .<Markable>map(i -> i)
                .toList();
    }

    @Override
    @Transactional
    public void applyGhostClear(Long resourceId) {
        ISO iso = isoRepository.findById(resourceId)
                .orElseThrow(() -> new IllegalStateException("ISO not found for ghost clear: " + resourceId));
        if (!GhostEvaluator.isGhost(iso)) {
            throw new GhostClearTargetNotGhostException(supportedType().name() + "#" + resourceId);
        }
        isoRepository.delete(iso);
        log.info("[ghost] ISO row 정리. isoId={}", resourceId);
    }

    @Override
    @Transactional
    public void applyForcedClear(Long resourceId) {
        // MK3-2 (DCM3-2.5) — 사용자 명시 "강제 정리". lifecycle / FS 검증 없이 row hard-delete.
        ISO iso = isoRepository.findById(resourceId)
                .orElseThrow(() -> new IllegalStateException("ISO not found for forced clear: " + resourceId));
        isoRepository.delete(iso);
        log.info("[forced-clear] ISO row 정리. isoId={}", resourceId);
    }

    private static String sha256Hex(Path file) throws IOException, NoSuchAlgorithmException {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        try (InputStream in = Files.newInputStream(file);
             DigestInputStream dis = new DigestInputStream(in, md)) {
            byte[] buf = new byte[8192];
            while (dis.read(buf) >= 0) { /* drain */ }
        }
        return HexFormat.of().formatHex(md.digest());
    }
}
