package com.example.serverprovision.global.asset;

import com.example.serverprovision.global.history.AssetHistoryService;
import com.example.serverprovision.global.history.AssetVersionKey;
import com.example.serverprovision.global.history.entity.AssetVersion;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 테스트용 {@link AssetHistoryService} 페이크 — mock 스텁 대신 실제 바이트를 이력 store 로 복사·보관해
 * {@link AtomicAssetSwap} 의 archive→openVersion 배선과 복원 바이트 정합을 사실적으로 검증한다. archive 시점의
 * 파일 내용을 그대로 떠 놓으므로, 스왑 전 옛 조각이 store 에 남고 뒤이은 restore 가 그 바이트를 되살린다.
 */
public class FakeAssetHistoryService implements AssetHistoryService {

    private final Path store;
    private final AtomicLong idSeq = new AtomicLong();
    private final Map<Long, Path> byId = new HashMap<>();

    public FakeAssetHistoryService(Path store) {
        this.store = store;
    }

    @Override
    public Optional<AssetVersion> archive(AssetVersionKey key, Path currentFile) {
        try {
            long id = idSeq.incrementAndGet();
            Path saved = store.resolve("archived-" + id);
            Files.copy(currentFile, saved);                 // archive 시점(=스왑 전) 바이트를 떠 놓는다
            AssetVersion version = AssetVersion.of(
                    key, saved.toString(), "sha-" + id, Files.size(saved), Instant.now());
            ReflectionTestUtils.setField(version, "id", id);
            byId.put(id, saved);
            return Optional.of(version);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public List<AssetVersion> list(AssetVersionKey key) {
        return List.of();
    }

    @Override
    public Path openVersion(AssetVersionKey key, Long versionId) {
        return byId.get(versionId);
    }

    @Override
    public void prune(AssetVersionKey key) {
        // 테스트에서 보존 상한 정리는 관심 밖 — no-op.
    }

    @Override
    public void remove(AssetVersionKey key, Long versionId) {
        Path removed = byId.remove(versionId);
        if (removed != null) {
            try {
                Files.deleteIfExists(removed);
            } catch (IOException ignore) {
                // 테스트 정리 실패는 무해.
            }
        }
    }

    public int archiveCount() {
        return byId.size();
    }
}
