package com.example.serverprovision.global.history.service.internal;

import com.example.serverprovision.global.history.AssetHistoryProperties;
import com.example.serverprovision.global.history.AssetHistorySettingsService;
import com.example.serverprovision.global.history.AssetHistoryService;
import com.example.serverprovision.global.history.AssetVersionKey;
import com.example.serverprovision.global.history.entity.AssetVersion;
import com.example.serverprovision.global.history.exception.AssetVersionArchiveFailedException;
import com.example.serverprovision.global.history.exception.AssetVersionNotFoundException;
import com.example.serverprovision.global.history.repository.AssetVersionRepository;
import com.example.serverprovision.global.security.FileSystemHardener;
import com.example.serverprovision.global.util.FileDigest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * {@link AssetHistoryService} 본체(E1-I-2-b-1). 이력 파일은 {@code <root>/<category>/<name>/<ts>_<uuid8>_<name>}
 * 로 보관하고({@code TrashPolicy.resolveTrashDirectory} + 파일명 합성 미러) 행은 {@code asset_version} 에 기록한다.
 *
 * <p>아카이브는 <b>복사</b>(move 아님)다 — 아카이브 시점의 현재 파일은 아직 활성본이라 저장소로 복사만 하고,
 * 활성본은 직후 교체 스왑이 덮어쓴다. 복사 → 저장(행) 순서라 <b>행은 항상 실재 파일을 가리킨다</b>(저장 실패 시
 * 롤백돼도 남는 것은 고아 파일뿐 — 무해).</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AssetHistoryServiceImpl implements AssetHistoryService {

    /** 파일명 충돌 방지 timestamp(UTC, µs 미만은 SSS). {@code TrashService.generateTrashedFileName} 관례. */
    private static final DateTimeFormatter TS =
            DateTimeFormatter.ofPattern("yyMMdd-HHmmss-SSS").withZone(ZoneOffset.UTC);

    private final AssetVersionRepository repository;
    private final AssetHistoryProperties properties;
    private final AssetHistorySettingsService settingsService;
    private final FileSystemHardener fileSystemHardener;

    @Override
    @Transactional
    public Optional<AssetVersion> archive(AssetVersionKey key, Path currentFile) {
        if (currentFile == null || !Files.isRegularFile(currentFile)) {
            return Optional.empty();   // 아카이브할 옛 버전 없음(최초 생성 교체)
        }
        Path root = properties.getRoot();
        Path dir = root.resolve(key.category()).resolve(key.name());
        String fileName = currentFile.getFileName().toString();
        Instant now = Instant.now();
        String archivedName = TS.format(now) + "_" + UUID.randomUUID().toString().substring(0, 8) + "_" + fileName;
        Path dest = dir.resolve(archivedName);

        String sha;
        long size;
        try {
            Files.createDirectories(dir);
            Files.copy(currentFile, dest, StandardCopyOption.COPY_ATTRIBUTES);   // 활성본은 남기고 복사만
            sha = FileDigest.sha256(dest);
            size = Files.size(dest);
        } catch (IOException e) {
            throw new AssetVersionArchiveFailedException("버전 아카이브 실패 : " + dest, e);
        }
        fileSystemHardener.applyDefaultPermissionsForFile(dest);

        String relPath = root.relativize(dest).toString();
        AssetVersion saved = repository.save(AssetVersion.of(key, relPath, sha, size, now));
        prune(key);   // 같은 트랜잭션 내 정리(자기 호출이라 프록시 우회 — 의도된 동작)
        log.info("[asset-history] 아카이브 — key={}/{}, size={}B, rel={}", key.category(), key.name(), size, relPath);
        return Optional.of(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public List<AssetVersion> list(AssetVersionKey key) {
        return repository.findByCategoryAndNameOrderByIdDesc(key.category(), key.name());
    }

    @Override
    @Transactional(readOnly = true)
    public Path openVersion(AssetVersionKey key, Long versionId) {
        AssetVersion version = repository.findById(versionId)
                .filter(v -> v.getCategory().equals(key.category()) && v.getName().equals(key.name()))
                .orElseThrow(() -> AssetVersionNotFoundException.of(key, versionId));
        return properties.getRoot().resolve(version.getArchivedRelPath());
    }

    @Override
    @Transactional
    public void prune(AssetVersionKey key) {
        long over = repository.countByCategoryAndName(key.category(), key.name()) - settingsService.getRetentionCount();
        if (over <= 0) {
            return;
        }
        Path root = properties.getRoot();
        Page<AssetVersion> oldest = repository.findByCategoryAndNameOrderByIdAsc(
                key.category(), key.name(), PageRequest.of(0, (int) over));
        for (AssetVersion version : oldest) {
            deleteFileQuietly(root, version);   // 파일 먼저
        }
        repository.deleteAll(oldest.getContent());
    }

    @Override
    @Transactional
    public void remove(AssetVersionKey key, Long versionId) {
        repository.findById(versionId)
                .filter(v -> v.getCategory().equals(key.category()) && v.getName().equals(key.name()))
                .ifPresent(v -> {
                    deleteFileQuietly(properties.getRoot(), v);
                    repository.delete(v);
                });
    }

    /** 이력 파일 삭제 — 실패는 로그만(행은 지워 카운트를 정확히 유지, 고아 파일은 무해). */
    private void deleteFileQuietly(Path root, AssetVersion version) {
        try {
            Files.deleteIfExists(root.resolve(version.getArchivedRelPath()));
        } catch (IOException e) {
            log.warn("[asset-history] 이력 파일 삭제 실패(행은 삭제) : {}", version.getArchivedRelPath(), e);
        }
    }
}
