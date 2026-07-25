package com.example.serverprovision.global.history.service.internal;

import com.example.serverprovision.global.history.AssetHistoryProperties;
import com.example.serverprovision.global.history.AssetHistorySettingsService;
import com.example.serverprovision.global.history.dto.response.AssetHistorySettingsResponse;
import com.example.serverprovision.global.history.entity.AssetHistorySettings;
import com.example.serverprovision.global.history.repository.AssetHistorySettingsRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZoneId;
import java.util.Objects;

/**
 * {@link AssetHistorySettingsService} 본체(E1-I-2-b-2). {@code TrashSettingsServiceImpl} 의 singleton 시드 +
 * 캐시 패턴을 필드 하나로 축소해 미러링한다.
 *
 * <p><b>Singleton 보장</b>: {@code count()==1} 기준. 시작 시 0 이면 property 기본값으로 시드, 1 이면 정상,
 * 2 이상이면 첫 행만 신뢰하고 나머지는 삭제한다.</p>
 *
 * <p><b>캐시</b>: {@code prune} 이 교체마다 {@link #getRetentionCount()} 를 호출하므로 DB 부하를 캐시로 막는다.
 * {@link #update} 시 캐시를 갱신한다.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AssetHistorySettingsServiceImpl implements AssetHistorySettingsService {

    private final AssetHistorySettingsRepository repository;
    private final AssetHistoryProperties properties;

    private volatile AssetHistorySettings cached;

    @PostConstruct
    @Transactional
    public void seedOrReconcile() {
        long count = repository.count();
        if (count == 0L) {
            cached = repository.save(AssetHistorySettings.seed(properties.getRetentionCount()));
            log.info("[asset-history-settings] singleton 행 없음 — property 기본값({})으로 시드. id={}",
                    properties.getRetentionCount(), cached.getId());
            return;
        }
        AssetHistorySettings first = repository.findFirstByOrderByIdAsc().orElseThrow();
        if (count > 1L) {
            log.warn("[asset-history-settings] singleton 위반 — count={} 행. id={} 만 신뢰, 나머지 삭제.",
                    count, first.getId());
            repository.findAll().stream()
                    .filter(s -> !Objects.equals(s.getId(), first.getId()))
                    .forEach(repository::delete);
        }
        cached = first;
    }

    @Override
    public int getRetentionCount() {
        return currentEntity().getRetentionCount();
    }

    @Override
    public AssetHistorySettingsResponse current() {
        return toResponse(currentEntity());
    }

    @Override
    @Transactional
    public AssetHistorySettingsResponse update(int retentionCount) {
        AssetHistorySettings settings = currentEntity();
        settings.apply(retentionCount);
        AssetHistorySettings saved = repository.save(settings);
        cached = saved;
        log.info("[asset-history-settings] 보존 개수 갱신 — {}개", saved.getRetentionCount());
        return toResponse(saved);
    }

    private AssetHistorySettings currentEntity() {
        AssetHistorySettings c = cached;
        if (c != null) {
            return c;
        }
        synchronized (this) {
            if (cached == null) {
                cached = repository.findFirstByOrderByIdAsc()
                        .orElseGet(() -> repository.save(AssetHistorySettings.seed(properties.getRetentionCount())));
            }
            return cached;
        }
    }

    private AssetHistorySettingsResponse toResponse(AssetHistorySettings settings) {
        return new AssetHistorySettingsResponse(
                settings.getRetentionCount(),
                settings.getUpdatedAt() == null
                        ? null
                        : settings.getUpdatedAt().atZone(ZoneId.systemDefault()).toInstant());
    }
}
