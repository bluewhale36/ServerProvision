package com.example.serverprovision.execution.installtree.service;

import com.example.serverprovision.execution.installtree.config.InstallTreeProperties;
import com.example.serverprovision.execution.installtree.enums.TreeFailureReason;
import com.example.serverprovision.execution.installtree.enums.TreeState;
import com.example.serverprovision.execution.installtree.vo.InstallTree;
import com.example.serverprovision.execution.installtree.vo.TreeAcquisition;
import com.example.serverprovision.management.os.entity.ISO;
import com.example.serverprovision.management.os.repository.ISORepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * RHEL 설치 작업 트리의 수명 관리(plan E4-1-b-1 방향 B). 설치가 그 ISO 를 처음 필요로 할 때 풀고, 같은 ISO 로
 * 설치 중인 서버(보유자)가 0 대가 된 뒤 유예 시간이 지나면 지운다. 상태는 메모리뿐이다(D-4) — 재기동하면
 * 기동 정리가 디스크를 비우고 다시 시작한다.
 *
 * <p>동시성: ISO 마다 {@link IsoTreeEntry} 하나를 두고 그 객체로 잠근다(D-9). 같은 ISO 의 동시 확보는 추출을 한 번만
 * 시작하고, 다른 ISO 는 서로 막지 않는다. 디스크 삭제는 잠금 밖에서 한다 — 잠금 안에서는 트리를 rename 으로
 * 서빙 경로에서 빼기만 한다.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class InstallTreeManager implements SmartInitializingSingleton {

    private final InstallTreeProperties properties;
    private final ISORepository isoRepository;
    private final InstallTreeExtractor extractor;
    private final InstallTreeStore store;
    private final InstallTreeInspector inspector;
    private final InstallTreeToolProbe toolProbe;
    private final Clock clock;

    private final ConcurrentHashMap<Long, IsoTreeEntry> entries = new ConcurrentHashMap<>();

    /**
     * 그 서버의 설치를 위해 ISO 작업 트리를 확보한다. 같은 서버가 다시 불러도 안전하다(보유자는 집합).
     * 추출 단계에서 실패한 ISO 를 기다리던 서버는 다음 호출에서 {@code Failed} 를 한 번 받고 보유자에서 빠진다.
     * 그 뒤의 새 호출(운영자 [재시도])은 다시 추출한다(D-10).
     */
    public TreeAcquisition acquire(long isoId, UUID guestId) {
        if (!properties.configured()) {
            return TreeAcquisition.failed(TreeFailureReason.NOT_CONFIGURED);
        }
        IsoSource source = resolveSource(isoId);
        IsoTreeEntry entry = entries.computeIfAbsent(isoId, id -> new IsoTreeEntry());
        synchronized (entry) {
            if (entry.failedHolders.remove(guestId)) {
                return TreeAcquisition.failed(entry.failure);
            }
            switch (entry.state) {
                case READY -> {
                    if (!Files.isDirectory(entry.tree.root())) {
                        log.warn("[install-tree] 준비된 트리가 디스크에서 사라짐 — 다시 확보. isoId={}, tree={}", isoId, entry.tree.root());
                        entry.reset();
                        return source.acquireInto(this, entry, isoId, guestId);
                    }
                    entry.holders.add(guestId);
                    entry.releasedAt = null;
                    return TreeAcquisition.ready(entry.tree);
                }
                case PREPARING -> {
                    entry.holders.add(guestId);
                    return TreeAcquisition.preparing();
                }
                default -> {
                    return source.acquireInto(this, entry, isoId, guestId);
                }
            }
        }
    }

    /** 그 서버의 설치가 끝났다(완료 · 실패 · 시한 · 교체). 보유자가 0 이 되면 유예 시간을 잰다. */
    public void release(long isoId, UUID guestId) {
        IsoTreeEntry entry = entries.get(isoId);
        if (entry == null) {
            return;
        }
        synchronized (entry) {
            entry.failedHolders.remove(guestId);
            if (!entry.holders.remove(guestId) || !entry.holders.isEmpty() || entry.state != TreeState.READY) {
                return;
            }
            if (entry.appOwned) {
                entry.releasedAt = clock.instant();
                log.info("[install-tree] 보유자 0 — 유예 시작. isoId={}, grace={}", isoId, properties.releaseGrace());
            } else {
                entry.reset();
            }
        }
    }

    public TreeState stateOf(long isoId) {
        IsoTreeEntry entry = entries.get(isoId);
        if (entry == null) {
            return TreeState.ABSENT;
        }
        synchronized (entry) {
            return entry.state;
        }
    }

    /** 유예가 지난 트리를 지운다. 분 단위 눈금이면 충분하다(유예 기본 10 분). */
    @Scheduled(fixedDelay = 60_000L)
    public void sweepExpired() {
        Instant now = clock.instant();
        entries.forEach((isoId, entry) -> {
            Path retired = null;
            synchronized (entry) {
                if (!entry.expired(now, properties)) {
                    return;
                }
                if (!Files.exists(entry.tree.root())) {
                    entry.reset();
                    return;
                }
                try {
                    retired = store.retire(entry.tree.root());
                    entry.reset();
                    log.info("[install-tree] 유예 만료 — 트리 회수. isoId={}", isoId);
                } catch (IOException e) {
                    entry.releasedAt = now;
                    log.warn("[install-tree] 트리 회수 실패 — 다음 눈금에 다시. isoId={}, msg={}", isoId, e.getMessage());
                }
            }
            if (retired != null) {
                store.delete(retired);
            }
        });
    }

    /**
     * 웹 서버가 뜨기 전(모든 싱글턴 생성 직후)에 기동 정리를 한다. 루트를 준비하지 못하면 포트가 열리기 전에 기동이 실패한다
     * (CP5 O-1: {@code ApplicationReadyEvent} 에서는 "Started" 뒤에 실패해 기동 성공으로 오판될 수 있었다).
     */
    @Override
    public void afterSingletonsInstantiated() {
        cleanOnStartup();
    }

    public void cleanOnStartup() {
        if (!properties.configured()) {
            return;
        }
        try {
            InstallTreeStore.CleanupResult result = store.cleanOnStartup();
            if (result.failed() > 0) {
                log.warn("[install-tree] 기동 정리 중 지우지 못한 항목이 있다. removed={}, failed={}", result.removed(), result.failed());
            } else {
                log.info("[install-tree] 기동 정리 완료. removed={}", result.removed());
            }
        } catch (IOException e) {
            throw new IllegalStateException("provision.rhel-install.tree-root 를 준비하지 못했다 (RHEL_INSTALL_TREE_ROOT) : " + e.getMessage(), e);
        }
        if (!toolProbe.available()) {
            log.warn("[install-tree] bsdtar · 7z 가 모두 없다 — RHEL 설치는 TOOL_MISSING 으로 실패한다. bsdtar 패키지를 설치해야 한다.");
        }
    }

    private TreeAcquisition acquireFromFile(IsoTreeEntry entry, long isoId, UUID guestId, Path isoFile) {
        if (!toolProbe.available()) {
            return TreeAcquisition.failed(TreeFailureReason.TOOL_MISSING);
        }
        entry.state = TreeState.PREPARING;
        entry.failure = null;
        entry.holders.add(guestId);
        try {
            extractor.extract(isoId, isoFile).whenComplete((result, error) ->
                    onExtracted(isoId, error == null ? result : TreeAcquisition.failed(TreeFailureReason.EXTRACTION_FAILED)));
        } catch (TaskRejectedException e) {
            log.warn("[install-tree] 추출 실행기가 작업을 받지 않음. isoId={}, msg={}", isoId, e.getMessage());
            onExtracted(isoId, TreeAcquisition.failed(TreeFailureReason.EXTRACTION_FAILED));
            entry.failedHolders.remove(guestId);
            return TreeAcquisition.failed(TreeFailureReason.EXTRACTION_FAILED);
        }
        log.info("[install-tree] 추출 요청. isoId={}, guestId={}", isoId, guestId);
        return TreeAcquisition.preparing();
    }

    private TreeAcquisition acquireFromDirectory(IsoTreeEntry entry, UUID guestId, Path directory) {
        return inspector.inspect(directory)
                .map(release -> {
                    entry.state = TreeState.READY;
                    entry.appOwned = false;
                    entry.tree = new InstallTree(directory, release);
                    entry.holders.add(guestId);
                    return TreeAcquisition.ready(entry.tree);
                })
                .orElseGet(() -> TreeAcquisition.failed(TreeFailureReason.NOT_RHEL_TREE));
    }

    private void onExtracted(long isoId, TreeAcquisition result) {
        IsoTreeEntry entry = entries.get(isoId);
        synchronized (entry) {
            if (result instanceof TreeAcquisition.Ready ready) {
                entry.state = TreeState.READY;
                entry.appOwned = true;
                entry.tree = ready.tree();
                entry.releasedAt = entry.holders.isEmpty() ? clock.instant() : null;
                return;
            }
            entry.state = TreeState.FAILED;
            entry.failure = result instanceof TreeAcquisition.Failed failed
                    ? failed.reason() : TreeFailureReason.EXTRACTION_FAILED;
            entry.failedHolders.addAll(entry.holders);
            entry.holders.clear();
            log.warn("[install-tree] 추출 실패로 닫음. isoId={}, reason={}, 알릴 서버={}", isoId, entry.failure, entry.failedHolders.size());
        }
    }

    private IsoSource resolveSource(long isoId) {
        String raw = isoRepository.findById(isoId).map(ISO::getIsoPath).orElse(null);
        if (raw == null || raw.isBlank()) {
            return new IsoSource.Unusable(TreeFailureReason.ISO_UNAVAILABLE);
        }
        if (raw.startsWith("http://") || raw.startsWith("https://")) {
            return new IsoSource.Unusable(TreeFailureReason.HTTP_ISO_UNSUPPORTED);
        }
        Path path;
        try {
            path = Path.of(raw).toAbsolutePath().normalize();
        } catch (InvalidPathException e) {
            return new IsoSource.Unusable(TreeFailureReason.ISO_UNAVAILABLE);
        }
        if (Files.isDirectory(path)) {
            return new IsoSource.Directory(path);
        }
        return Files.isRegularFile(path)
                ? new IsoSource.File(path)
                : new IsoSource.Unusable(TreeFailureReason.ISO_UNAVAILABLE);
    }

    /** 등록된 ISO 경로의 모양. 모양마다 트리를 얻는 법이 다르다(D-8: 디렉터리는 풀지 않고 그대로 쓴다). */
    private sealed interface IsoSource {

        TreeAcquisition acquireInto(InstallTreeManager manager, IsoTreeEntry entry, long isoId, UUID guestId);

        record File(Path path) implements IsoSource {
            @Override
            public TreeAcquisition acquireInto(InstallTreeManager manager, IsoTreeEntry entry, long isoId, UUID guestId) {
                return manager.acquireFromFile(entry, isoId, guestId, path);
            }
        }

        record Directory(Path path) implements IsoSource {
            @Override
            public TreeAcquisition acquireInto(InstallTreeManager manager, IsoTreeEntry entry, long isoId, UUID guestId) {
                return manager.acquireFromDirectory(entry, guestId, path);
            }
        }

        record Unusable(TreeFailureReason reason) implements IsoSource {
            @Override
            public TreeAcquisition acquireInto(InstallTreeManager manager, IsoTreeEntry entry, long isoId, UUID guestId) {
                return TreeAcquisition.failed(reason);
            }
        }
    }

    /** ISO 하나의 메모리 상태. 모든 필드는 이 객체의 모니터 아래에서만 읽고 쓴다. */
    private static final class IsoTreeEntry {
        TreeState state = TreeState.ABSENT;
        final Set<UUID> holders = new HashSet<>();
        final Set<UUID> failedHolders = new HashSet<>();
        InstallTree tree;
        TreeFailureReason failure;
        Instant releasedAt;
        boolean appOwned;

        boolean expired(Instant now, InstallTreeProperties properties) {
            return state == TreeState.READY && appOwned && holders.isEmpty() && releasedAt != null
                    && !now.isBefore(releasedAt.plus(properties.releaseGrace()));
        }

        void reset() {
            state = TreeState.ABSENT;
            tree = null;
            releasedAt = null;
            appOwned = false;
        }
    }
}
