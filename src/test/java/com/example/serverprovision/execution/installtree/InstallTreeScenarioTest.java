package com.example.serverprovision.execution.installtree;

import com.example.serverprovision.execution.installtree.config.InstallTreeConfig;
import com.example.serverprovision.execution.installtree.enums.TreeFailureReason;
import com.example.serverprovision.execution.installtree.enums.TreeState;
import com.example.serverprovision.execution.installtree.service.InstallTreeExtractor;
import com.example.serverprovision.execution.installtree.service.InstallTreeInspector;
import com.example.serverprovision.execution.installtree.service.InstallTreeManager;
import com.example.serverprovision.execution.installtree.service.InstallTreeStore;
import com.example.serverprovision.execution.installtree.service.InstallTreeToolProbe;
import com.example.serverprovision.execution.installtree.vo.TreeAcquisition;
import com.example.serverprovision.global.config.AsyncConfig;
import com.example.serverprovision.management.os.entity.ISO;
import com.example.serverprovision.management.os.repository.ISORepository;
import com.example.serverprovision.management.os.service.iso.IsoPreparationService;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * E4-1-b-1 통합 시나리오 — Spring 컨텍스트(실제 @Async 실행기) + 실제 bsdtar + 가짜 Rocky ISO + 실제 디스크.
 * plan 데모 프리셋 6 개와 같은 이야기다. CP5 하네스(scripts/installtree-fixture/verify-install-tree.sh)는
 * 환경변수 {@code INSTALLTREE_EVIDENCE_DIR} 를 주어 이 시험을 돌리고, 단계마다 남는 상태 · 디스크 목록을 채증한다.
 */
@SpringJUnitConfig(InstallTreeScenarioTest.Wiring.class)
class InstallTreeScenarioTest {

    private static final Path WORK;
    private static final Path TREE_ROOT;
    private static final UUID G1 = UUID.fromString("00000000-0000-0000-0000-0000000000a1");
    private static final UUID G2 = UUID.fromString("00000000-0000-0000-0000-0000000000a2");
    private static final UUID G3 = UUID.fromString("00000000-0000-0000-0000-0000000000a3");
    private static final UUID G4 = UUID.fromString("00000000-0000-0000-0000-0000000000a4");
    private static Path iso95;
    private static Path iso810;

    static {
        try {
            WORK = Files.createTempDirectory("installtree-scenario-");
            TREE_ROOT = WORK.resolve("install-trees");
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    @Configuration
    @Import({AsyncConfig.class, InstallTreeConfig.class, InstallTreeManager.class, InstallTreeExtractor.class,
            InstallTreeStore.class, InstallTreeInspector.class, InstallTreeToolProbe.class, IsoPreparationService.class})
    static class Wiring {
        @Bean
        MutableClock clock() {
            return new MutableClock(Instant.parse("2026-11-17T00:00:00Z"));
        }
    }

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("provision.rhel-install.tree-root", TREE_ROOT::toString);
        registry.add("provision.rhel-install.release-grace", () -> "10m");
    }

    @MockitoBean
    ISORepository isoRepository;
    @Autowired
    InstallTreeManager manager;
    @Autowired
    IsoPreparationService isoPreparationService;
    @Autowired
    MutableClock clock;
    @org.springframework.test.context.bean.override.mockito.MockitoSpyBean
    InstallTreeStore store;

    private String scenario;

    @BeforeAll
    static void buildIsos() throws Exception {
        iso95 = FakeRockyTree.buildIso(WORK, "Rocky Linux", "9.5").orElse(null);
        iso810 = FakeRockyTree.buildIso(WORK, "Rocky Linux", "8.10").orElse(null);
    }

    @AfterAll
    static void removeWorkDir() throws IOException {
        if (!Files.exists(WORK)) {
            return;
        }
        try (Stream<Path> walk = Files.walk(WORK)) {
            for (Path p : walk.sorted(Comparator.reverseOrder()).toList()) {
                p.toFile().setWritable(true);
                Files.deleteIfExists(p);
            }
        }
    }

    @BeforeEach
    void setUp(TestInfo info) throws IOException {
        assumeTrue(isoPreparationService.extractionToolAvailable(), "bsdtar · 7z 없음");
        assumeTrue(iso95 != null && iso810 != null, "ISO 생성기 없음");
        scenario = info.getDisplayName().substring(0, 2);
        registerIso(12L, iso95);
        registerIso(13L, iso810);
        manager.cleanOnStartup();   // 시나리오마다 재기동한 것처럼 디스크를 비운다(메모리는 각 시나리오가 반납으로 비움)
    }

    @AfterEach
    void tearDown() {
        for (UUID g : new UUID[]{G1, G2, G3, G4}) {
            manager.release(12L, g);
            manager.release(13L, g);
        }
        clock.advance(Duration.ofDays(1));
        manager.sweepExpired();
    }

    @Test
    @DisplayName("S1 1 대 설치: 확보하면 준비 중, 풀리면 준비됨, 반납 뒤 유예 10 분이 지나면 삭제")
    void s1_single() throws Exception {
        record("G1 확보", manager.acquire(12L, G1));
        awaitReady(12L);
        TreeAcquisition ready = manager.acquire(12L, G1);
        record("G1 다시 확보", ready);
        assertThat(ready).isInstanceOf(TreeAcquisition.Ready.class);
        assertThat(TREE_ROOT.resolve("iso-12/images/pxeboot/vmlinuz")).hasContent("kernel");
        snapshot("준비됨");

        manager.release(12L, G1);
        clock.advance(Duration.ofMinutes(9));
        manager.sweepExpired();
        snapshot("반납 + 9 분");
        assertThat(TREE_ROOT.resolve("iso-12")).isDirectory();

        clock.advance(Duration.ofMinutes(1));
        manager.sweepExpired();
        snapshot("반납 + 10 분");
        assertThat(TREE_ROOT.resolve("iso-12")).doesNotExist();
        assertThat(manager.stateOf(12L)).isEqualTo(TreeState.ABSENT);
    }

    @Test
    @DisplayName("S2 같은 ISO 3 대 동시: 한 번만 풀고 셋이 같은 트리를 쓴다")
    void s2_sharedThree() throws Exception {
        record("G1 확보", manager.acquire(12L, G1));
        record("G2 확보", manager.acquire(12L, G2));
        record("G3 확보", manager.acquire(12L, G3));
        awaitReady(12L);
        Path root1 = treeRoot(manager.acquire(12L, G1));
        Path root3 = treeRoot(manager.acquire(12L, G3));
        assertThat(root1).isEqualTo(root3).isEqualTo(TREE_ROOT.resolve("iso-12"));
        snapshot("준비됨(트리 하나)");
        assertThat(listTreeDirs()).containsExactly("iso-12");

        manager.release(12L, G1);
        manager.release(12L, G2);
        clock.advance(Duration.ofHours(1));
        manager.sweepExpired();
        snapshot("2 대 반납 + 1 시간(G3 남음)");
        assertThat(TREE_ROOT.resolve("iso-12")).isDirectory();
    }

    @Test
    @DisplayName("S3 서로 다른 ISO 2 개: 트리 둘, 각각의 마지막 반납 뒤 각각 삭제")
    void s3_twoIsos() throws Exception {
        record("G1 확보(ISO-12)", manager.acquire(12L, G1));
        record("G4 확보(ISO-13)", manager.acquire(13L, G4));
        awaitReady(12L);
        awaitReady(13L);
        record("G4 다시 확보", manager.acquire(13L, G4));
        snapshot("준비됨(트리 둘)");
        assertThat(listTreeDirs()).containsExactlyInAnyOrder("iso-12", "iso-13");

        manager.release(13L, G4);
        clock.advance(Duration.ofMinutes(10));
        manager.sweepExpired();
        snapshot("ISO-13 반납 + 10 분");
        assertThat(listTreeDirs()).containsExactly("iso-12");
    }

    @Test
    @DisplayName("S4 유예 중 재시도: 삭제를 취소하고 다시 풀지 않는다")
    void s4_retryDuringGrace() throws Exception {
        manager.acquire(12L, G1);
        awaitReady(12L);
        Instant firstBuilt = Files.getLastModifiedTime(TREE_ROOT.resolve("iso-12")).toInstant();
        manager.release(12L, G1);
        clock.advance(Duration.ofMinutes(5));
        snapshot("반납 + 5 분(유예 중)");

        record("G1 재시도 확보", manager.acquire(12L, G1));
        clock.advance(Duration.ofHours(1));
        manager.sweepExpired();
        snapshot("재확보 + 1 시간");
        assertThat(manager.stateOf(12L)).isEqualTo(TreeState.READY);
        assertThat(Files.getLastModifiedTime(TREE_ROOT.resolve("iso-12")).toInstant()).isEqualTo(firstBuilt);
    }

    @Test
    @DisplayName("S5 공간 부족: 풀지 않고 실패, 기다리던 서버는 실패를 받는다")
    void s5_notEnoughSpace() throws Exception {
        long isoSize = Files.size(iso95);
        doReturn(isoSize).when(store).usableSpace();   // 남은 공간 측정값만 ISO 크기로 고정(필요 = 1.2 배). 그 밖은 실제
        evidence("  (남은 공간 측정값을 " + isoSize + " B 로 고정, 필요 = ISO " + isoSize + " B × 1.2)");
        try {
            record("G2 확보(ISO-12)", manager.acquire(12L, G2));
            awaitState(12L, TreeState.FAILED);
            TreeAcquisition failed = manager.acquire(12L, G2);
            record("G2 다음 확보", failed);
            assertThat(failed).isEqualTo(TreeAcquisition.failed(TreeFailureReason.NOT_ENOUGH_SPACE));
            snapshot("실패 뒤(트리 · staging 없음)");
            assertThat(listTreeDirs()).isEmpty();
        } finally {
            org.mockito.Mockito.reset(store);
        }
        record("공간 회복 뒤 G2 재시도 확보", manager.acquire(12L, G2));
        awaitReady(12L);
        snapshot("재시도로 준비됨");
    }

    @Test
    @DisplayName("S6 앱 재기동: 남은 트리 · 반쯤 풀린 staging 은 지우고 규칙 밖 파일은 남긴다")
    void s6_restartCleanup() throws Exception {
        manager.acquire(12L, G1);
        awaitReady(12L);
        Files.createDirectories(TREE_ROOT.resolve(".staging/half-extracted/images"));
        Files.writeString(TREE_ROOT.resolve("operator-note.txt"), "keep");
        snapshot("재기동 전");

        manager.cleanOnStartup();
        snapshot("기동 정리 뒤");
        assertThat(listAll()).containsExactly("operator-note.txt");
        Files.delete(TREE_ROOT.resolve("operator-note.txt"));
    }

    // ---- 도우미 ----

    private void registerIso(long id, Path path) {
        ISO iso = mock(ISO.class);
        when(iso.getIsoPath()).thenReturn(path.toString());
        when(isoRepository.findById(id)).thenReturn(Optional.of(iso));
    }

    private void awaitReady(long isoId) throws InterruptedException {
        awaitState(isoId, TreeState.READY);
    }

    private void awaitState(long isoId, TreeState expected) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 30_000;
        while (manager.stateOf(isoId) != expected && System.currentTimeMillis() < deadline) {
            Thread.sleep(50);
        }
        assertThat(manager.stateOf(isoId)).isEqualTo(expected);
        evidence("  ISO-" + isoId + " 상태 → " + expected);
    }

    private static Path treeRoot(TreeAcquisition acquisition) {
        return ((TreeAcquisition.Ready) acquisition).tree().root();
    }

    private static java.util.List<String> listTreeDirs() throws IOException {
        return listAll().stream().filter(n -> n.startsWith("iso-") || n.equals(".staging") && hasStagingChildren()).toList();
    }

    private static boolean hasStagingChildren() {
        try (Stream<Path> s = Files.list(TREE_ROOT.resolve(".staging"))) {
            return s.findAny().isPresent();
        } catch (IOException e) {
            return false;
        }
    }

    private static java.util.List<String> listAll() throws IOException {
        if (!Files.isDirectory(TREE_ROOT)) {
            return java.util.List.of();
        }
        try (Stream<Path> s = Files.list(TREE_ROOT)) {
            return s.map(p -> p.getFileName().toString()).sorted().toList();
        }
    }

    private void record(String action, TreeAcquisition result) {
        evidence("  " + action + " → " + result);
    }

    private void snapshot(String label) throws IOException {
        String listing;
        if (!Files.isDirectory(TREE_ROOT)) {
            listing = "    (루트 없음)";
        } else {
            try (Stream<Path> walk = Files.walk(TREE_ROOT, 4)) {
                listing = walk.filter(p -> !p.equals(TREE_ROOT)).sorted(Comparator.naturalOrder())
                        .map(p -> "    " + TREE_ROOT.relativize(p) + (Files.isDirectory(p) ? "/" : ""))
                        .collect(Collectors.joining("\n"));
            }
        }
        evidence("  [디스크 · " + label + "] " + "{TREE_ROOT}\n" + (listing.isEmpty() ? "    (비어 있음)" : listing));
    }

    private void evidence(String line) {
        String dir = System.getenv("INSTALLTREE_EVIDENCE_DIR");
        if (dir == null || dir.isBlank()) {
            return;
        }
        try {
            Path file = Files.createDirectories(Path.of(dir)).resolve(scenario + ".txt");
            Files.writeString(file, line + "\n", StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    /** 시험용 움직이는 시계(매니저가 주입받는 {@link Clock}). */
    static final class MutableClock extends Clock {
        private volatile Instant now;

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
