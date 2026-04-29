package com.example.serverprovision.management.os.service;

import com.example.serverprovision.global.marker.Markable;
import com.example.serverprovision.global.marker.MarkableScanner;
import com.example.serverprovision.global.marker.ResourceType;
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
