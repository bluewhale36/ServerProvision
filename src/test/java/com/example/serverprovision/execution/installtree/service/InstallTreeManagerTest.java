package com.example.serverprovision.execution.installtree.service;

import com.example.serverprovision.execution.installtree.FakeRockyTree;
import com.example.serverprovision.execution.installtree.config.InstallTreeProperties;
import com.example.serverprovision.execution.installtree.enums.InstallMediaLayout;
import com.example.serverprovision.execution.installtree.enums.TreeFailureReason;
import com.example.serverprovision.execution.installtree.enums.TreeState;
import com.example.serverprovision.execution.installtree.vo.InstallTree;
import com.example.serverprovision.execution.installtree.vo.TreeAcquisition;
import com.example.serverprovision.execution.installtree.vo.TreeRelease;
import com.example.serverprovision.management.os.entity.ISO;
import com.example.serverprovision.management.os.repository.ISORepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.task.TaskRejectedException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 작업 트리 수명: 확보 · 공유 · 반납 · 유예 · 취소 · 실패 알림(D-10) · 디렉터리 ISO · 동시 확보 · 기동 정리. */
class InstallTreeManagerTest {

    private static final InstallMediaLayout RHEL = InstallMediaLayout.RHEL_TREE;
    private static final InstallMediaLayout UBUNTU = InstallMediaLayout.UBUNTU_LIVE_SERVER;

    private static final long ISO_ID = 12L;
    private static final UUID G1 = UUID.randomUUID();
    private static final UUID G2 = UUID.randomUUID();
    private static final UUID G3 = UUID.randomUUID();

    @TempDir
    Path temp;
    private Path isoFile;
    private ISORepository isoRepository;
    private InstallTreeExtractor extractor;
    private InstallTreeStore store;
    private InstallTreeToolProbe toolProbe;
    private MutableClock clock;
    private InstallTreeManager manager;
    private final List<CompletableFuture<TreeAcquisition>> extractions = new ArrayList<>();
    private InstallTree tree;

    @BeforeEach
    void setUp() throws IOException {
        isoFile = Files.writeString(temp.resolve("Rocky-9.5.iso"), "iso");
        isoRepository = mock(ISORepository.class);
        extractor = mock(InstallTreeExtractor.class);
        store = mock(InstallTreeStore.class);
        toolProbe = mock(InstallTreeToolProbe.class);
        clock = new MutableClock(Instant.parse("2026-11-17T00:00:00Z"));
        tree = new InstallTree(temp.resolve("trees/iso-12"), new TreeRelease("Rocky Linux", "9.5"), isoFile);
        Files.createDirectories(tree.root());
        when(toolProbe.available()).thenReturn(true);
        when(extractor.extract(anyLong(), any(), any())).thenAnswer(inv -> {
            CompletableFuture<TreeAcquisition> future = new CompletableFuture<>();
            extractions.add(future);
            return future;
        });
        when(store.retire(any())).thenAnswer(inv -> temp.resolve("retired"));
        registerIso(ISO_ID, isoFile.toString());
        manager = manager("/srv/trees");
    }

    @Test
    @DisplayName("미설정 · ISO 없음 · 웹 주소 ISO · 파일 사라짐 · 도구 없음은 즉시 실패하고 상태를 바꾸지 않는다")
    void immediateFailures() {
        assertThat(manager("").acquire(ISO_ID, G1, RHEL)).isEqualTo(TreeAcquisition.failed(TreeFailureReason.NOT_CONFIGURED));
        assertThat(manager.acquire(99L, G1, RHEL)).isEqualTo(TreeAcquisition.failed(TreeFailureReason.ISO_UNAVAILABLE));
        registerIso(20L, "https://mirror.example.com/rocky.iso");
        assertThat(manager.acquire(20L, G1, RHEL)).isEqualTo(TreeAcquisition.failed(TreeFailureReason.HTTP_ISO_UNSUPPORTED));
        registerIso(21L, temp.resolve("gone.iso").toString());
        assertThat(manager.acquire(21L, G1, RHEL)).isEqualTo(TreeAcquisition.failed(TreeFailureReason.ISO_UNAVAILABLE));
        when(toolProbe.available()).thenReturn(false);
        assertThat(manager.acquire(ISO_ID, G1, RHEL)).isEqualTo(TreeAcquisition.failed(TreeFailureReason.TOOL_MISSING));

        assertThat(manager.stateOf(ISO_ID)).isEqualTo(TreeState.ABSENT);
        verify(extractor, never()).extract(anyLong(), any(), any());
    }

    @Test
    @DisplayName("같은 ISO 3 대 동시: 한 번만 풀고, 준비 중이면 대기, 준비되면 모두 같은 트리")
    void sharedExtraction() {
        assertThat(manager.acquire(ISO_ID, G1, RHEL)).isEqualTo(TreeAcquisition.preparing());
        assertThat(manager.acquire(ISO_ID, G2, RHEL)).isEqualTo(TreeAcquisition.preparing());
        assertThat(manager.acquire(ISO_ID, G1, RHEL)).isEqualTo(TreeAcquisition.preparing());
        assertThat(manager.stateOf(ISO_ID)).isEqualTo(TreeState.PREPARING);

        completeReady();

        assertThat(manager.acquire(ISO_ID, G1, RHEL)).isEqualTo(TreeAcquisition.ready(tree));
        assertThat(manager.acquire(ISO_ID, G3, RHEL)).isEqualTo(TreeAcquisition.ready(tree));
        verify(extractor, times(1)).extract(eq(ISO_ID), eq(isoFile.toAbsolutePath().normalize()), eq(RHEL));
    }

    @Test
    @DisplayName("보유자가 남아 있으면 유지, 0 이 되면 유예 시간 뒤 회수 · 삭제")
    void releaseThenGraceDeletion() throws IOException {
        manager.acquire(ISO_ID, G1, RHEL);
        manager.acquire(ISO_ID, G2, RHEL);
        completeReady();

        manager.release(ISO_ID, G1);
        clock.advance(Duration.ofHours(1));
        manager.sweepExpired();
        assertThat(manager.stateOf(ISO_ID)).isEqualTo(TreeState.READY);

        manager.release(ISO_ID, G2);
        clock.advance(Duration.ofMinutes(9));
        manager.sweepExpired();
        assertThat(manager.stateOf(ISO_ID)).isEqualTo(TreeState.READY);
        verify(store, never()).retire(any());

        clock.advance(Duration.ofMinutes(1));
        manager.sweepExpired();
        assertThat(manager.stateOf(ISO_ID)).isEqualTo(TreeState.ABSENT);
        verify(store).retire(tree.root());
        verify(store).delete(temp.resolve("retired"));
    }

    @Test
    @DisplayName("유예 중 새 확보는 삭제를 취소하고 다시 풀지 않는다")
    void reacquireDuringGraceCancels() throws IOException {
        manager.acquire(ISO_ID, G1, RHEL);
        completeReady();
        manager.release(ISO_ID, G1);
        clock.advance(Duration.ofMinutes(5));

        assertThat(manager.acquire(ISO_ID, G1, RHEL)).isEqualTo(TreeAcquisition.ready(tree));
        clock.advance(Duration.ofHours(1));
        manager.sweepExpired();

        assertThat(manager.stateOf(ISO_ID)).isEqualTo(TreeState.READY);
        verify(store, never()).retire(any());
        verify(extractor, times(1)).extract(anyLong(), any(), any());
    }

    @Test
    @DisplayName("추출 중 보유자가 0 이 되어도 끝까지 풀고, 준비된 시점에 유예를 시작한다")
    void graceStartsAtReadyWhenNoHolders() {
        manager.acquire(ISO_ID, G1, RHEL);
        manager.release(ISO_ID, G1);
        clock.advance(Duration.ofMinutes(30));
        completeReady();

        clock.advance(Duration.ofMinutes(9));
        manager.sweepExpired();
        assertThat(manager.stateOf(ISO_ID)).isEqualTo(TreeState.READY);
        clock.advance(Duration.ofMinutes(1));
        manager.sweepExpired();
        assertThat(manager.stateOf(ISO_ID)).isEqualTo(TreeState.ABSENT);
    }

    @Test
    @DisplayName("D-10: 추출 실패 시 기다리던 서버는 다음 확보에서 실패를 한 번 받고, 그 뒤 새 확보(재시도)는 다시 푼다")
    void failureNotifiesWaitersOnceThenRetryReExtracts() {
        manager.acquire(ISO_ID, G1, RHEL);
        manager.acquire(ISO_ID, G2, RHEL);
        extractions.get(0).complete(TreeAcquisition.failed(TreeFailureReason.NOT_ENOUGH_SPACE));
        assertThat(manager.stateOf(ISO_ID)).isEqualTo(TreeState.FAILED);

        assertThat(manager.acquire(ISO_ID, G1, RHEL)).isEqualTo(TreeAcquisition.failed(TreeFailureReason.NOT_ENOUGH_SPACE));
        manager.release(ISO_ID, G2);   // G2 는 실행기가 실패로 닫으며 반납 — 알림 대상에서도 빠진다

        assertThat(manager.acquire(ISO_ID, G1, RHEL)).isEqualTo(TreeAcquisition.preparing());
        assertThat(manager.acquire(ISO_ID, G2, RHEL)).isEqualTo(TreeAcquisition.preparing());
        verify(extractor, times(2)).extract(anyLong(), any(), any());
    }

    @Test
    @DisplayName("추출 future 가 예외로 끝나면 EXTRACTION_FAILED")
    void exceptionalFutureBecomesExtractionFailed() {
        manager.acquire(ISO_ID, G1, RHEL);
        extractions.get(0).completeExceptionally(new IllegalStateException("boom"));

        assertThat(manager.acquire(ISO_ID, G1, RHEL)).isEqualTo(TreeAcquisition.failed(TreeFailureReason.EXTRACTION_FAILED));
    }

    @Test
    @DisplayName("추출 실행기가 작업을 거절하면 그 자리에서 EXTRACTION_FAILED, 다음 확보는 다시 시도")
    void rejectedExtraction() {
        when(extractor.extract(anyLong(), any(), any())).thenThrow(new TaskRejectedException("full"));

        assertThat(manager.acquire(ISO_ID, G1, RHEL)).isEqualTo(TreeAcquisition.failed(TreeFailureReason.EXTRACTION_FAILED));
        assertThat(manager.stateOf(ISO_ID)).isEqualTo(TreeState.FAILED);
        assertThat(manager.acquire(ISO_ID, G1, RHEL)).isEqualTo(TreeAcquisition.failed(TreeFailureReason.EXTRACTION_FAILED));
        verify(extractor, times(2)).extract(anyLong(), any(), any());
    }

    @Test
    @DisplayName("디렉터리 ISO: 풀지 않고 그대로 트리, 보유자 0 이 되어도 지우지 않는다")
    void directoryIso() throws IOException {
        Path dir = FakeRockyTree.writeTree(Files.createDirectories(temp.resolve("rocky-dir")), "Rocky Linux", "8.10");
        registerIso(30L, dir.toString());

        TreeAcquisition result = manager.acquire(30L, G1, RHEL);

        assertThat(result).isInstanceOfSatisfying(TreeAcquisition.Ready.class,
                ready -> assertThat(ready.tree().root()).isEqualTo(dir));
        manager.release(30L, G1);
        clock.advance(Duration.ofDays(1));
        manager.sweepExpired();
        assertThat(manager.stateOf(30L)).isEqualTo(TreeState.ABSENT);
        verify(store, never()).retire(any());
        verify(extractor, never()).extract(anyLong(), any(), any());
    }

    @Test
    @DisplayName("RHEL 트리가 아닌 디렉터리 ISO 는 NOT_RHEL_TREE")
    void directoryIsoNotRhel() throws IOException {
        Path dir = Files.createDirectories(temp.resolve("not-rhel"));
        registerIso(31L, dir.toString());

        assertThat(manager.acquire(31L, G1, RHEL)).isEqualTo(TreeAcquisition.failed(TreeFailureReason.NOT_RHEL_TREE));
    }

    @Test
    @DisplayName("회수에 실패하면 상태를 유지하고 다음 눈금에 다시 시도한다")
    void retireFailureRetries() throws IOException {
        manager.acquire(ISO_ID, G1, RHEL);
        completeReady();
        manager.release(ISO_ID, G1);
        when(store.retire(any())).thenThrow(new IOException("busy")).thenAnswer(inv -> temp.resolve("retired"));

        clock.advance(Duration.ofMinutes(10));
        manager.sweepExpired();
        assertThat(manager.stateOf(ISO_ID)).isEqualTo(TreeState.READY);
        clock.advance(Duration.ofMinutes(10));
        manager.sweepExpired();
        assertThat(manager.stateOf(ISO_ID)).isEqualTo(TreeState.ABSENT);
    }

    @Test
    @DisplayName("준비된 트리가 디스크에서 사라졌으면 죽은 트리를 내주지 않고 다시 푼다")
    void vanishedTreeIsReExtracted() throws IOException {
        manager.acquire(ISO_ID, G1, RHEL);
        completeReady();
        Files.delete(tree.root());

        assertThat(manager.acquire(ISO_ID, G2, RHEL)).isEqualTo(TreeAcquisition.preparing());
        assertThat(manager.stateOf(ISO_ID)).isEqualTo(TreeState.PREPARING);
        verify(extractor, times(2)).extract(anyLong(), any(), any());
    }

    @Test
    @DisplayName("유예가 지난 트리가 이미 디스크에 없으면 회수 없이 상태만 비운다")
    void sweepOfVanishedTreeResets() throws IOException {
        manager.acquire(ISO_ID, G1, RHEL);
        completeReady();
        manager.release(ISO_ID, G1);
        Files.delete(tree.root());

        clock.advance(Duration.ofMinutes(10));
        manager.sweepExpired();

        assertThat(manager.stateOf(ISO_ID)).isEqualTo(TreeState.ABSENT);
        verify(store, never()).retire(any());
    }

    @Test
    @DisplayName("알 수 없는 ISO 의 반납 · 보유하지 않은 서버의 반납은 아무 일도 하지 않는다")
    void releaseNoop() {
        manager.release(77L, G1);
        manager.acquire(ISO_ID, G1, RHEL);
        completeReady();
        manager.release(ISO_ID, G2);
        clock.advance(Duration.ofHours(1));
        manager.sweepExpired();
        assertThat(manager.stateOf(ISO_ID)).isEqualTo(TreeState.READY);
    }

    @Test
    @DisplayName("16 스레드가 같은 ISO 를 동시에 확보해도 추출은 한 번")
    void concurrentAcquireExtractsOnce() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(16);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<TreeAcquisition>> results = new ArrayList<>();
        for (int i = 0; i < 16; i++) {
            UUID guest = UUID.randomUUID();
            results.add(pool.submit(() -> {
                start.await();
                return manager.acquire(ISO_ID, guest, RHEL);
            }));
        }
        start.countDown();
        for (Future<TreeAcquisition> r : results) {
            assertThat(r.get(5, TimeUnit.SECONDS)).isEqualTo(TreeAcquisition.preparing());
        }
        pool.shutdown();
        verify(extractor, times(1)).extract(anyLong(), any(), any());
    }

    @Test
    @DisplayName("기동 정리: 설정되어 있으면 저장소 정리, 꺼져 있으면 건드리지 않음, 실패하면 기동 실패")
    void startupCleanup() throws IOException {
        when(store.cleanOnStartup()).thenReturn(new InstallTreeStore.CleanupResult(2, 0));
        manager.afterSingletonsInstantiated();
        verify(store).cleanOnStartup();

        InstallTreeManager off = manager("");
        off.cleanOnStartup();
        verify(store, times(1)).cleanOnStartup();

        when(store.cleanOnStartup()).thenThrow(new IOException("permission denied"));
        assertThatThrownBy(manager::cleanOnStartup)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("INSTALL_TREE_ROOT");
    }

    @Test
    @DisplayName("inspect(b-4-2) — 사전 점검은 부수효과가 없다: 추출을 시작하지 않고 보유자도 더하지 않는다")
    void inspect_hasNoSideEffect() {
        assertThat(manager.inspect(ISO_ID, RHEL)).isEmpty();
        assertThat(extractions).isEmpty();
        assertThat(manager.stateOf(ISO_ID)).isEqualTo(TreeState.ABSENT);
    }

    @Test
    @DisplayName("inspect — 루트 미설정 NOT_CONFIGURED · 도구 없음 TOOL_MISSING · 웹 주소 HTTP_ISO_UNSUPPORTED · 없는 파일 ISO_UNAVAILABLE")
    void inspect_reasons() {
        assertThat(manager("").inspect(ISO_ID, RHEL)).contains(TreeFailureReason.NOT_CONFIGURED);
        when(toolProbe.available()).thenReturn(false);
        assertThat(manager.inspect(ISO_ID, RHEL)).contains(TreeFailureReason.TOOL_MISSING);
        registerIso(20L, "https://mirror.example/Rocky-9.5.iso");
        assertThat(manager.inspect(20L, RHEL)).contains(TreeFailureReason.HTTP_ISO_UNSUPPORTED);
        registerIso(21L, temp.resolve("missing.iso").toString());
        assertThat(manager.inspect(21L, RHEL)).contains(TreeFailureReason.ISO_UNAVAILABLE);
        when(isoRepository.findById(22L)).thenReturn(Optional.empty());
        assertThat(manager.inspect(22L, RHEL)).contains(TreeFailureReason.ISO_UNAVAILABLE);
    }

    @Test
    @DisplayName("inspect — 디렉터리로 등록된 ISO 는 풀지 않으므로 도구가 없어도 사유가 없다 · 이미 준비된 트리도 사유가 없다")
    void inspect_directoryAndReadyNeedNoTool() throws IOException {
        when(toolProbe.available()).thenReturn(false);
        Path dir = Files.createDirectories(temp.resolve("iso-dir"));
        registerIso(30L, dir.toString());
        assertThat(manager.inspect(30L, RHEL)).isEmpty();

        when(toolProbe.available()).thenReturn(true);
        manager.acquire(ISO_ID, G1, RHEL);
        completeReady();
        when(toolProbe.available()).thenReturn(false);
        assertThat(manager.inspect(ISO_ID, RHEL)).isEmpty();
    }

    @Test
    @DisplayName("Ubuntu: 디렉터리로 등록된 ISO 는 추출 없이 즉시 DIRECTORY_ISO_UNSUPPORTED, 사전 점검도 같은 사유(도구 유무 무관)")
    void ubuntuRefusesDirectoryIso() throws IOException {
        Path dir = Files.createDirectories(temp.resolve("ubuntu-dir/casper"));
        registerIso(40L, dir.getParent().toString());

        assertThat(manager.acquire(40L, G1, UBUNTU)).isEqualTo(TreeAcquisition.failed(TreeFailureReason.DIRECTORY_ISO_UNSUPPORTED));
        assertThat(manager.stateOf(40L)).isEqualTo(TreeState.ABSENT);
        verify(extractor, never()).extract(anyLong(), any(), any());
        when(toolProbe.available()).thenReturn(false);
        assertThat(manager.inspect(40L, UBUNTU)).contains(TreeFailureReason.DIRECTORY_ISO_UNSUPPORTED);
        assertThat(manager.inspect(40L, RHEL)).isEmpty();
    }

    @Test
    @DisplayName("Ubuntu: ISO 파일이면 계열 모양을 추출기에 넘기고, 수명 규칙(공유 · 반납 · 유예)은 RHEL 과 같다")
    void ubuntuFileUsesSameLifecycle() {
        assertThat(manager.acquire(ISO_ID, G1, UBUNTU)).isEqualTo(TreeAcquisition.preparing());
        assertThat(manager.acquire(ISO_ID, G2, UBUNTU)).isEqualTo(TreeAcquisition.preparing());
        verify(extractor, times(1)).extract(eq(ISO_ID), eq(isoFile.toAbsolutePath().normalize()), eq(UBUNTU));

        completeReady();
        manager.release(ISO_ID, G1);
        manager.release(ISO_ID, G2);
        clock.advance(Duration.ofMinutes(10));
        manager.sweepExpired();
        assertThat(manager.stateOf(ISO_ID)).isEqualTo(TreeState.ABSENT);
    }

    private void completeReady() {
        extractions.get(extractions.size() - 1).complete(TreeAcquisition.ready(tree));
    }

    private void registerIso(long id, String path) {
        ISO iso = mock(ISO.class);
        when(iso.getIsoPath()).thenReturn(path);
        when(isoRepository.findById(id)).thenReturn(Optional.of(iso));
    }

    private InstallTreeManager manager(String root) {
        return new InstallTreeManager(new InstallTreeProperties(root, Duration.ofMinutes(10)),
                isoRepository, extractor, store, toolProbe, clock);
    }

    /** 시험용 움직이는 시계. */
    static final class MutableClock extends Clock {
        private Instant now;

        MutableClock(Instant now) {
            this.now = now;
        }

        void advance(Duration d) {
            now = now.plus(d);
        }

        @Override
        public ZoneId getZone() {
            return ZoneId.of("Asia/Seoul");
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
