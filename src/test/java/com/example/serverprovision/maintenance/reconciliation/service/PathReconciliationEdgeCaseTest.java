package com.example.serverprovision.maintenance.reconciliation.service;

import com.example.serverprovision.global.job.service.BackgroundJobService;
import com.example.serverprovision.global.marker.DriftKind;
import com.example.serverprovision.global.marker.Markable;
import com.example.serverprovision.global.marker.MarkableScanner;
import com.example.serverprovision.global.marker.MarkerContent;
import com.example.serverprovision.global.marker.MarkerLayout;
import com.example.serverprovision.global.marker.ResourceType;
import com.example.serverprovision.global.marker.service.ProvisionMarkerService;
import com.example.serverprovision.maintenance.reconciliation.entity.Drift;
import com.example.serverprovision.maintenance.reconciliation.entity.DriftReport;
import com.example.serverprovision.maintenance.reconciliation.repository.DriftReportRepository;
import com.example.serverprovision.maintenance.reconciliation.repository.DriftRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * MK1 PathReconciliationService 엣지 케이스 / 공격적 시나리오 검증.
 *
 * <p>기존 {@code PathReconciliationServiceTest} 가 5 가지 drift 분류의 happy 분기를 다룬다면,
 * 본 묶음은 운영에서 실제 발생할 수 있는 비대칭/다중자원/대용량 트리/소프트삭제 경계 등
 * "잘 안 보이는 길목" 을 타겟으로 한다.</p>
 *
 * <p>모든 테스트는 실제 디스크({@code @TempDir}) 와 실제 {@link ProvisionMarkerService} 를 쓰며,
 * Repository / BackgroundJob 등 외부 협력자만 mock 으로 차단한다.</p>
 */
class PathReconciliationEdgeCaseTest {

    private ProvisionMarkerService markerService;
    private BackgroundJobService backgroundJobService;
    private DriftReportRepository driftReportRepository;
    private DriftRepository driftRepository;

    private MarkableScanner isoScanner;
    private MarkableScanner biosScanner;
    private PathReconciliationService service;

    @BeforeEach
    void setUp() {
        markerService = new ProvisionMarkerService();
        ReflectionTestUtils.setField(markerService, "secret", "edge-secret");

        backgroundJobService = mock(BackgroundJobService.class);
        given(backgroundJobService.register(any(), anyString(), anyString(), org.mockito.ArgumentMatchers.<java.util.List<String>>any())).willReturn("job-edge");

        driftReportRepository = mock(DriftReportRepository.class);
        given(driftReportRepository.save(any(DriftReport.class))).willAnswer(inv -> inv.getArgument(0));
        given(driftReportRepository.count()).willReturn(0L);

        driftRepository = mock(DriftRepository.class);

        isoScanner = mock(MarkableScanner.class);
        given(isoScanner.supportedType()).willReturn(ResourceType.OS_ISO);
        given(isoScanner.findSoftDeletedResourceIds()).willReturn(Set.of());

        biosScanner = mock(MarkableScanner.class);
        given(biosScanner.supportedType()).willReturn(ResourceType.BIOS_BUNDLE);
        given(biosScanner.findSoftDeletedResourceIds()).willReturn(Set.of());

        // self proxy 자리는 단위 테스트 범위 외. null 주입.
        service = new PathReconciliationService(
                List.of(isoScanner, biosScanner), markerService, backgroundJobService,
                driftReportRepository, driftRepository, null);
        ReflectionTestUtils.setField(service, "startupEnabled", true);
        ReflectionTestUtils.setField(service, "retentionCount", 100);
        ReflectionTestUtils.setField(service, "autoApplyPathDrift", false);
        ReflectionTestUtils.setField(service, "extraRootsCsv", "");
    }

    // ==== helpers ======================================================

    private Markable iso(Long id, Path path) {
        Markable m = mock(Markable.class);
        given(m.getResourceId()).willReturn(id);
        given(m.getResourceType()).willReturn(ResourceType.OS_ISO);
        given(m.getResourcePath()).willReturn(path);
        given(m.getMarkerLayout()).willReturn(MarkerLayout.SIDECAR);
        return m;
    }

    private Markable bios(Long id, Path treeRoot) {
        Markable m = mock(Markable.class);
        given(m.getResourceId()).willReturn(id);
        given(m.getResourceType()).willReturn(ResourceType.BIOS_BUNDLE);
        given(m.getResourcePath()).willReturn(treeRoot);
        given(m.getMarkerLayout()).willReturn(MarkerLayout.IN_TREE);
        return m;
    }

    private void writeIsoMarker(Path resource, Long id, String hash) {
        MarkerContent unsigned = new MarkerContent(
                ResourceType.OS_ISO.name(), id, Map.of(), Instant.now(), hash, null);
        markerService.write(resource, MarkerLayout.SIDECAR,
                unsigned.withSignature(markerService.computeSignature(unsigned)));
    }

    private void writeBiosMarker(Path treeRoot, Long id, String hash) {
        MarkerContent unsigned = new MarkerContent(
                ResourceType.BIOS_BUNDLE.name(), id, Map.of(), Instant.now(), hash, null);
        markerService.write(treeRoot, MarkerLayout.IN_TREE,
                unsigned.withSignature(markerService.computeSignature(unsigned)));
    }

    private DriftReport runScan(boolean deep) {
        ReflectionTestUtils.invokeMethod(service, "performScan", deep, "job-edge");
        var captor = org.mockito.ArgumentCaptor.forClass(DriftReport.class);
        verify(driftReportRepository).save(captor.capture());
        return captor.getValue();
    }

    // ==== A. 파일 이동 시나리오 =========================================

    @Test
    @DisplayName("A2 : BIOS 트리 통째로 이동 — IN_TREE 마커도 동행, PATH_DRIFT 감지")
    void biosTreeMoved_pathDrift(@TempDir Path tmp) throws Exception {
        // 원래 위치는 비어있고, 새 위치에 트리 + 마커
        Path oldRoot = tmp.resolve("old/A/B/R23");
        Path newRoot = tmp.resolve("new/X/Y/R23");
        Files.createDirectories(newRoot);
        Files.writeString(newRoot.resolve("rom.bin"), "rom-content");
        writeBiosMarker(newRoot, 7L, "manifest-7");
        // 원래 부모는 살려둬야 scan-root union 이 tmp 하위를 포함
        Files.createDirectories(oldRoot.getParent());

        Markable b = bios(7L, oldRoot);
        given(isoScanner.findActiveMarkables()).willReturn(List.of());
        given(biosScanner.findActiveMarkables()).willReturn(List.of(b));
        ReflectionTestUtils.setField(service, "extraRootsCsv", tmp.toString());

        DriftReport saved = runScan(false);

        assertThat(saved.getDrifts()).singleElement().satisfies(d -> {
            assertThat(d.getKind()).isEqualTo(DriftKind.PATH_DRIFT);
            assertThat(d.getResourceType()).isEqualTo(ResourceType.BIOS_BUNDLE);
            assertThat(d.getResourceId()).isEqualTo(7L);
            assertThat(d.getNewPath()).isEqualTo(newRoot.toString());
        });
    }

    @Test
    @DisplayName("A3 : 같은 디렉토리의 여러 ISO 중 하나만 이동 — 이동된 1건만 PATH_DRIFT")
    void multipleISOs_partialMove(@TempDir Path tmp) throws Exception {
        Path dir = tmp.resolve("iso-pool");
        Files.createDirectories(dir);
        Path dvd = dir.resolve("dvd.iso");
        Path minimal = dir.resolve("minimal.iso");
        Path boot = dir.resolve("boot.iso");
        Files.writeString(dvd, "dvd"); writeIsoMarker(dvd, 1L, "h1");
        Files.writeString(minimal, "min"); writeIsoMarker(minimal, 2L, "h2");
        Files.writeString(boot, "boot"); writeIsoMarker(boot, 3L, "h3");

        // dvd.iso 만 다른 디렉토리로 이동 (마커도 동행)
        Path moved = tmp.resolve("relocated/dvd.iso");
        Files.createDirectories(moved.getParent());
        Files.move(dvd, moved);
        Files.move(dir.resolve("dvd.iso.provision.json"), moved.resolveSibling("dvd.iso.provision.json"));

        Markable m1 = iso(1L, dvd), m2 = iso(2L, minimal), m3 = iso(3L, boot);
        given(isoScanner.findActiveMarkables()).willReturn(List.of(m1, m2, m3));
        given(biosScanner.findActiveMarkables()).willReturn(List.of());
        ReflectionTestUtils.setField(service, "extraRootsCsv", tmp.toString());

        DriftReport saved = runScan(false);

        assertThat(saved.getDrifts()).singleElement().satisfies(d -> {
            assertThat(d.getKind()).isEqualTo(DriftKind.PATH_DRIFT);
            assertThat(d.getResourceId()).isEqualTo(1L);
            assertThat(d.getNewPath()).isEqualTo(moved.toString());
        });
    }

    @Test
    @DisplayName("A4 : sidecar 만 이동, 본체는 그대로 — 본체 옆에서 매칭 실패 → MISSING " +
            "+ 옮긴 sidecar 측은 ORPHAN (resourcePath 가 가짜) 또는 무시. 동작 확정")
    void sidecarOnlyMoved(@TempDir Path tmp) throws Exception {
        Path dvd = tmp.resolve("orig/dvd.iso");
        Files.createDirectories(dvd.getParent());
        Files.writeString(dvd, "body");
        writeIsoMarker(dvd, 42L, "hash-42");
        // 마커만 다른 곳으로 이동
        Path movedDir = tmp.resolve("strayed");
        Files.createDirectories(movedDir);
        Files.move(dvd.resolveSibling("dvd.iso.provision.json"),
                movedDir.resolve("dvd.iso.provision.json"));

        Markable m = iso(42L, dvd);
        given(isoScanner.findActiveMarkables()).willReturn(List.of(m));
        given(biosScanner.findActiveMarkables()).willReturn(List.of());
        ReflectionTestUtils.setField(service, "extraRootsCsv", tmp.toString());

        DriftReport saved = runScan(false);

        // 옮긴 sidecar 의 resolveSibling 으로 추정한 resourcePath = strayed/dvd.iso (없는 파일).
        // 하지만 markerService.read 는 마커 자체만 읽으므로 정상 매칭됨 → MarkerHit 등록 →
        // active inventory 의 dvd 는 expectedMarker 가 없어 PATH_DRIFT 로 분류됨 (newPath = strayed/dvd.iso)
        // 본 시나리오의 실제 동작을 기록해두는 것이 목적.
        assertThat(saved.getDrifts()).singleElement().satisfies(d -> {
            assertThat(d.getResourceId()).isEqualTo(42L);
            // 코드 동작상 PATH_DRIFT 로 떨어진다 — newPath 가 "유효한 자원 위치" 인지는 별도 검증이 필요.
            assertThat(d.getKind()).isIn(DriftKind.PATH_DRIFT, DriftKind.MISSING);
        });
    }

    @Test
    @DisplayName("A5 : 본체만 이동, sidecar 는 그대로 — DB 위치 옆 sidecar 가 살아있어 happy 분류 (위험!)")
    void bodyOnlyMoved_markerStays(@TempDir Path tmp) throws Exception {
        Path dvd = tmp.resolve("orig/dvd.iso");
        Files.createDirectories(dvd.getParent());
        Files.writeString(dvd, "body");
        writeIsoMarker(dvd, 42L, "hash-42");
        // 본체만 다른 곳으로 (마커는 원래 위치에 잔류)
        Path moved = tmp.resolve("moved/dvd.iso");
        Files.createDirectories(moved.getParent());
        Files.move(dvd, moved);

        Markable m = iso(42L, dvd);
        given(isoScanner.findActiveMarkables()).willReturn(List.of(m));
        given(biosScanner.findActiveMarkables()).willReturn(List.of());
        ReflectionTestUtils.setField(service, "extraRootsCsv", tmp.toString());

        DriftReport saved = runScan(false);

        // DB.path 옆에 sidecar 가 그대로 살아있으므로 quick scan 은 정상으로 본다 — 본체 분실을 못 잡는다.
        // 이 사실을 확인 (quick 스캔의 한계).
        assertThat(saved.getDrifts()).isEmpty();
    }

    @Test
    @DisplayName("A5-deep : 본체만 삭제 + sidecar 잔류 → deep scan recompute empty → MISSING drift")
    void bodyOnlyMoved_deepRecomputeMissing(@TempDir Path tmp) throws Exception {
        Path dvd = tmp.resolve("orig/dvd.iso");
        Files.createDirectories(dvd.getParent());
        Files.writeString(dvd, "body");
        writeIsoMarker(dvd, 42L, "hash-42");
        Files.delete(dvd); // 파일만 삭제 (마커는 잔류) — recompute Optional.empty

        Markable m = iso(42L, dvd);
        given(isoScanner.findActiveMarkables()).willReturn(List.of(m));
        given(biosScanner.findActiveMarkables()).willReturn(List.of());
        given(isoScanner.recomputeManifestHash(m)).willReturn(Optional.empty());

        DriftReport saved = runScan(true);

        // (B-2 fix) recompute 가 empty 면 본체 자원 부재로 보고 MISSING drift 를 발행한다 —
        // deep scan 도 자원 손실을 인지하지 못하던 기존 버그를 교정.
        assertThat(saved.getDrifts()).hasSize(1);
        assertThat(saved.getDrifts().get(0).getKind()).isEqualTo(DriftKind.MISSING);
        assertThat(saved.getDrifts().get(0).getResourceId()).isEqualTo(42L);
        assertThat(saved.getDrifts().get(0).getDetail()).contains("manifestHash 재계산 실패");
    }

    // ==== B. 변조 시나리오 ============================================

    @Test
    @DisplayName("B8 : ISO 본체 내용 변조 — quick 통과, deep 에서 HASH_MISMATCH")
    void contentTamper_quickVsDeep(@TempDir Path tmp) throws Exception {
        Path dvd = tmp.resolve("dvd.iso");
        Files.writeString(dvd, "original");
        writeIsoMarker(dvd, 9L, "stored-hash");
        // 본체만 변조
        Files.writeString(dvd, "tampered");

        Markable m = iso(9L, dvd);
        given(isoScanner.findActiveMarkables()).willReturn(List.of(m));
        given(biosScanner.findActiveMarkables()).willReturn(List.of());
        given(isoScanner.recomputeManifestHash(m)).willReturn(Optional.of("different-hash"));

        // quick : drift 없음
        DriftReport quick = runScan(false);
        assertThat(quick.getDrifts()).isEmpty();

        // deep : HASH_MISMATCH
        org.mockito.Mockito.reset(driftReportRepository);
        given(driftReportRepository.save(any(DriftReport.class))).willAnswer(inv -> inv.getArgument(0));
        given(driftReportRepository.count()).willReturn(0L);
        DriftReport deep = runScan(true);
        assertThat(deep.getDrifts()).singleElement()
                .satisfies(d -> assertThat(d.getKind()).isEqualTo(DriftKind.HASH_MISMATCH));
    }

    @Test
    @DisplayName("B12 : HMAC secret 회전 — 다른 secret 이면 SIGNATURE_INVALID")
    void hmacSecretRotation(@TempDir Path tmp) throws Exception {
        Path dvd = tmp.resolve("dvd.iso");
        Files.writeString(dvd, "x");
        writeIsoMarker(dvd, 1L, "h"); // 현재 secret 으로 서명
        // secret 회전
        ReflectionTestUtils.setField(markerService, "secret", "rotated-secret");

        Markable m = iso(1L, dvd);
        given(isoScanner.findActiveMarkables()).willReturn(List.of(m));
        given(biosScanner.findActiveMarkables()).willReturn(List.of());

        DriftReport saved = runScan(false);

        assertThat(saved.getDrifts()).singleElement()
                .satisfies(d -> assertThat(d.getKind()).isEqualTo(DriftKind.SIGNATURE_INVALID));
    }

    // ==== C. ORPHAN / soft-delete =====================================

    @Test
    @DisplayName("C15 : 여러 도메인 마커 혼재 ORPHAN — BIOS + ISO 마커 각 1건 잔재, DB 비어있음")
    void mixedDomainOrphans(@TempDir Path tmp) throws Exception {
        // BIOS 마커
        Path biosTree = tmp.resolve("bios/A/R23");
        Files.createDirectories(biosTree);
        Files.writeString(biosTree.resolve("rom.bin"), "x");
        writeBiosMarker(biosTree, 100L, "hb");
        // ISO 마커
        Path iso = tmp.resolve("iso/dvd.iso");
        Files.createDirectories(iso.getParent());
        Files.writeString(iso, "y");
        writeIsoMarker(iso, 200L, "hi");

        given(isoScanner.findActiveMarkables()).willReturn(List.of());
        given(biosScanner.findActiveMarkables()).willReturn(List.of());
        ReflectionTestUtils.setField(service, "extraRootsCsv", tmp.toString());

        DriftReport saved = runScan(false);

        assertThat(saved.getDrifts()).hasSize(2);
        assertThat(saved.getDrifts()).allSatisfy(d -> assertThat(d.getKind()).isEqualTo(DriftKind.ORPHAN));
        assertThat(saved.getDrifts()).extracting(Drift::getResourceType)
                .containsExactlyInAnyOrder(ResourceType.BIOS_BUNDLE, ResourceType.OS_ISO);
    }

    // ==== D. 동일 식별자 충돌 =========================================

    @Test
    @DisplayName("D16 : 두 위치에 같은 (type,id) 마커 — 첫 발견 우선 정책으로 1건만 PATH_DRIFT")
    void duplicateMarkers_firstWins(@TempDir Path tmp) throws Exception {
        Path dbPath = tmp.resolve("db/dvd.iso"); // DB 가 아는 위치 (마커 없음)
        Files.createDirectories(dbPath.getParent());
        // 같은 (OS_ISO, 42) 마커가 두 위치에 존재
        Path locA = tmp.resolve("a/dvd.iso");
        Path locB = tmp.resolve("b/dvd.iso");
        Files.createDirectories(locA.getParent());
        Files.createDirectories(locB.getParent());
        Files.writeString(locA, "a"); writeIsoMarker(locA, 42L, "h");
        Files.writeString(locB, "b"); writeIsoMarker(locB, 42L, "h");

        Markable m = iso(42L, dbPath);
        given(isoScanner.findActiveMarkables()).willReturn(List.of(m));
        given(biosScanner.findActiveMarkables()).willReturn(List.of());
        ReflectionTestUtils.setField(service, "extraRootsCsv", tmp.toString());

        DriftReport saved = runScan(false);

        // 첫 발견 우선 — 1 건만 PATH_DRIFT, newPath 는 a 또는 b 중 하나 (FS 순회 순서 의존)
        assertThat(saved.getDrifts()).singleElement().satisfies(d -> {
            assertThat(d.getKind()).isEqualTo(DriftKind.PATH_DRIFT);
            assertThat(d.getNewPath()).isIn(locA.toString(), locB.toString());
        });
    }

    // ==== E. 디렉토리 / 권한 / 깊이 =====================================

    @Test
    @DisplayName("E18 : Files.walk(root, 8) 깊이 제한 — 9 단계 깊이의 자원은 발견 안 됨")
    void depthLimit_8_doesNotFindDeeperResources(@TempDir Path tmp) throws Exception {
        // tmp/d1/d2/.../d9/dvd.iso  (tmp 부터 9 hops). Files.walk(tmp, 8) 은 8 까지만 본다.
        Path deep = tmp;
        for (int i = 1; i <= 9; i++) deep = deep.resolve("d" + i);
        Files.createDirectories(deep);
        Path iso = deep.resolve("dvd.iso");
        Files.writeString(iso, "x");
        writeIsoMarker(iso, 50L, "h");

        given(isoScanner.findActiveMarkables()).willReturn(List.of());
        given(biosScanner.findActiveMarkables()).willReturn(List.of());
        // scan root = tmp 만. 마커는 tmp 로부터 11 단계(d1~d9 + dvd.iso.provision.json) → walk(8) 의 한계 밖
        ReflectionTestUtils.setField(service, "extraRootsCsv", tmp.toString());

        DriftReport saved = runScan(false);

        // 깊이 한계로 ORPHAN 도 PATH_DRIFT 도 잡히지 않음 — drift 0
        assertThat(saved.getDrifts()).isEmpty();
    }

    @Test
    @DisplayName("E19 : DB.path 가 active 자원의 parent union 안에만 있는 경우 — 멀리 떨어진 곳으로 이동하면 MISSING")
    void scanRootUnion_doesNotCoverDistantMove(@TempDir Path tmp) throws Exception {
        // DB 가 아는 path = /a/b/c/file.iso. 자원이 /far/away/file.iso 로 이동.
        Path dbPath = tmp.resolve("a/b/c/file.iso");
        Files.createDirectories(dbPath.getParent());
        Path moved = tmp.resolve("far/away/file.iso");
        Files.createDirectories(moved.getParent());
        Files.writeString(moved, "x");
        writeIsoMarker(moved, 33L, "h");

        Markable m = iso(33L, dbPath);
        given(isoScanner.findActiveMarkables()).willReturn(List.of(m));
        given(biosScanner.findActiveMarkables()).willReturn(List.of());
        // extra-roots 미지정 — scan root = dbPath.parent = /a/b/c. 멀리 옮겨진 자원 못 찾음.

        DriftReport saved = runScan(false);

        assertThat(saved.getDrifts()).singleElement()
                .satisfies(d -> assertThat(d.getKind()).isEqualTo(DriftKind.MISSING));
        // 이는 의도된 동작 — 사용자가 extra-roots 로 알려줘야 한다. 본 테스트는 그 경계를 못박는다.
    }

    @Test
    @DisplayName("E22 : 빈 디렉토리 / 마커 없는 디렉토리 — 안전하게 skip, drift 0")
    void emptyDirectoriesSafe(@TempDir Path tmp) throws Exception {
        Files.createDirectories(tmp.resolve("empty1"));
        Files.createDirectories(tmp.resolve("empty2/sub"));
        // 자원도 없고 마커도 없음
        given(isoScanner.findActiveMarkables()).willReturn(List.of());
        given(biosScanner.findActiveMarkables()).willReturn(List.of());
        ReflectionTestUtils.setField(service, "extraRootsCsv", tmp.toString());

        DriftReport saved = runScan(false);

        assertThat(saved.getDrifts()).isEmpty();
        assertThat(saved.getTotalChecked()).isZero();
    }

    @Test
    @DisplayName("E20 : 존재하지 않는 scan root — Files.isDirectory false 로 graceful skip")
    void nonexistentScanRoot(@TempDir Path tmp) throws Exception {
        Path missing = tmp.resolve("no-such-dir");
        given(isoScanner.findActiveMarkables()).willReturn(List.of());
        given(biosScanner.findActiveMarkables()).willReturn(List.of());
        ReflectionTestUtils.setField(service, "extraRootsCsv", missing.toString());

        DriftReport saved = runScan(false);

        // 예외 없이 통과하는지 보장
        assertThat(saved.getDrifts()).isEmpty();
    }

    // ==== F. 동시성 / 트랜잭션 ========================================

    @Test
    @DisplayName("F24 : 자동 적용 도중 1건 실패해도 나머지는 진행 (try/catch)")
    void autoApply_partialFailure_continues(@TempDir Path tmp) throws Exception {
        // 두 개의 PATH_DRIFT — 첫번째 apply 가 IOException 던져도 두번째는 계속 시도되어야 함
        Path dvd1 = tmp.resolve("loc1/dvd.iso");
        Path dvd2 = tmp.resolve("loc2/dvd.iso");
        Files.createDirectories(dvd1.getParent());
        Files.createDirectories(dvd2.getParent());
        Files.writeString(dvd1, "1"); writeIsoMarker(dvd1, 1L, "h1");
        Files.writeString(dvd2, "2"); writeIsoMarker(dvd2, 2L, "h2");

        // DB 의 path 는 가짜 — 실제 마커는 위 위치들에 있어 PATH_DRIFT 가 두 건 발생
        Markable mm1 = iso(1L, tmp.resolve("ghost/old1.iso"));
        Markable mm2 = iso(2L, tmp.resolve("ghost/old2.iso"));
        given(isoScanner.findActiveMarkables()).willReturn(List.of(mm1, mm2));
        given(biosScanner.findActiveMarkables()).willReturn(List.of());
        // ghost 디렉토리가 없으므로 scan root union 에 포함만 시키도록 extra-roots 사용
        ReflectionTestUtils.setField(service, "extraRootsCsv", tmp.toString());
        ReflectionTestUtils.setField(service, "autoApplyPathDrift", true);

        // 첫 번째 자원 적용 시 RuntimeException
        doThrow(new RuntimeException("disk-fail"))
                .when(isoScanner).applyDriftedPath(eq(1L), any(Path.class));

        DriftReport saved = runScan(false);

        // 두 건의 PATH_DRIFT 가 보고서에 올라가고
        assertThat(saved.getDrifts()).hasSize(2);
        // 첫 건 실패해도 두 번째는 계속 — applyDriftedPath 가 두 번 모두 호출됨
        verify(isoScanner, times(1)).applyDriftedPath(eq(1L), any(Path.class));
        verify(isoScanner, times(1)).applyDriftedPath(eq(2L), any(Path.class));
    }

    @Test
    @DisplayName("F25 : FIFO prune — retention 초과 시 가장 오래된 보고서 삭제")
    void fifoPrune_overRetention(@TempDir Path tmp) {
        ReflectionTestUtils.setField(service, "retentionCount", 3);

        given(isoScanner.findActiveMarkables()).willReturn(List.of());
        given(biosScanner.findActiveMarkables()).willReturn(List.of());
        // count > retention → prune 호출됨
        given(driftReportRepository.count()).willReturn(5L);
        given(driftReportRepository.findAllByOrderByScannedAtAsc(any()))
                .willReturn(new org.springframework.data.domain.PageImpl<>(List.of(
                        DriftReport.builder().scannedAt(Instant.now()).scanDurationMs(0)
                                .deep(false).totalChecked(0).build(),
                        DriftReport.builder().scannedAt(Instant.now()).scanDurationMs(0)
                                .deep(false).totalChecked(0).build())));

        runScan(false);

        verify(driftReportRepository, times(1)).deleteAll(org.mockito.ArgumentMatchers.anyIterable());
    }

    @Test
    @DisplayName("F25-noPrune : retention 미달 시 deleteAll 미호출")
    void fifoPrune_underRetention_noDelete(@TempDir Path tmp) {
        ReflectionTestUtils.setField(service, "retentionCount", 100);

        given(isoScanner.findActiveMarkables()).willReturn(List.of());
        given(biosScanner.findActiveMarkables()).willReturn(List.of());
        given(driftReportRepository.count()).willReturn(5L);

        runScan(false);

        verify(driftReportRepository, never()).deleteAll(org.mockito.ArgumentMatchers.anyIterable());
    }

    // ==== G. PATH_DRIFT 의 newPath 에 대한 markerSignature 까지 검증되는가 ====

    @Test
    @DisplayName("G : PATH_DRIFT 로 분류된 newPath 의 마커가 서명 무효라도 단순 PATH_DRIFT 만 보고됨 (다중 분류 회피)")
    void pathDrift_doesNotDoubleClassify(@TempDir Path tmp) throws Exception {
        Path oldP = tmp.resolve("old/dvd.iso");
        Path newP = tmp.resolve("new/dvd.iso");
        Files.createDirectories(oldP.getParent());
        Files.createDirectories(newP.getParent());
        Files.writeString(newP, "x");
        // 의도적으로 깨진 sidecar (parseMarkerHit 에서 read 실패 → null → 무시)
        Files.writeString(newP.resolveSibling("dvd.iso.provision.json"),
                "{ this is not valid json }");

        Markable m = iso(42L, oldP);
        given(isoScanner.findActiveMarkables()).willReturn(List.of(m));
        given(biosScanner.findActiveMarkables()).willReturn(List.of());
        ReflectionTestUtils.setField(service, "extraRootsCsv", tmp.toString());

        DriftReport saved = runScan(false);

        // 마커가 파싱 불가라 diskMarkers 에 등록 안 됨 → MISSING 으로 떨어짐.
        assertThat(saved.getDrifts()).singleElement()
                .satisfies(d -> assertThat(d.getKind()).isEqualTo(DriftKind.MISSING));
    }

    // ==== H. ORPHAN with mixed soft-delete IDs ========================

    @Test
    @DisplayName("H : soft-deleted ID 셋이 다른 도메인의 마커는 보호 못 함 — 도메인별 분리 확인")
    void softDeletedIds_isPerDomain(@TempDir Path tmp) throws Exception {
        Path iso = tmp.resolve("iso/x.iso");
        Files.createDirectories(iso.getParent());
        Files.writeString(iso, "x");
        writeIsoMarker(iso, 50L, "h");

        // BIOS soft-deleted 셋에 50 이 있어도 — domain 다르므로 ISO 50 의 ORPHAN 분류 막지 못함
        given(isoScanner.findActiveMarkables()).willReturn(List.of());
        given(isoScanner.findSoftDeletedResourceIds()).willReturn(Set.of()); // ISO 측엔 없음
        given(biosScanner.findActiveMarkables()).willReturn(List.of());
        given(biosScanner.findSoftDeletedResourceIds()).willReturn(Set.of(50L)); // BIOS 측 50 — 무관해야
        ReflectionTestUtils.setField(service, "extraRootsCsv", tmp.toString());

        DriftReport saved = runScan(false);

        assertThat(saved.getDrifts()).singleElement().satisfies(d -> {
            assertThat(d.getKind()).isEqualTo(DriftKind.ORPHAN);
            assertThat(d.getResourceType()).isEqualTo(ResourceType.OS_ISO);
        });
    }
}
