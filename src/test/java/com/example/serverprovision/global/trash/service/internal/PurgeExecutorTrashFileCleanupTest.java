package com.example.serverprovision.global.trash.service.internal;

import com.example.serverprovision.global.entity.LifecycleEntity;
import com.example.serverprovision.global.marker.Markable;
import com.example.serverprovision.global.marker.MarkableScanner;
import com.example.serverprovision.global.marker.MarkerLayout;
import com.example.serverprovision.global.marker.ResourceType;
import com.example.serverprovision.global.trash.PurgeRequest;
import com.example.serverprovision.global.trash.PurgeResult;
import com.example.serverprovision.global.trash.entity.PurgeLog;
import com.example.serverprovision.global.trash.repository.PurgeLogRepository;
import com.example.serverprovision.global.trash.service.TrashSettingsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

/**
 * S6-2-3 — 영구삭제의 휴지통 실물 잔존 공백 보강 검증.
 *
 * <p>도메인 purge 는 원위치 부산물만 정리한다(soft-delete 가 원위치 경로를 보존하는 설계).
 * 종전에는 영구삭제가 끝나도 휴지통 실물이 남아 점검 수색 제외 구역에 영원히 잔존했다 —
 * 세 진입경로가 모두 지나는 PurgeExecutor 단일 지점에서 함께 정리함을 고정한다.</p>
 */
class PurgeExecutorTrashFileCleanupTest {

    private MarkableScanner isoScanner;
    private PurgeExecutorImpl executor;

    private static class DeletedIso extends LifecycleEntity implements Markable {
        private final Long id;
        private final Path path;
        DeletedIso(Long id, Path path, String trashedPath) {
            this.id = id;
            this.path = path;
            softDelete();
            if (trashedPath != null) markTrashed(trashedPath);
        }
        @Override protected Long resourceId() { return id; }
        @Override protected LifecycleEntity parentLifecycle() { return null; }
        @Override public Long getResourceId() { return id; }
        @Override public ResourceType getResourceType() { return ResourceType.OS_ISO; }
        @Override public Path getResourcePath() { return path; }
        @Override public MarkerLayout getMarkerLayout() { return MarkerLayout.SIDECAR; }
        @Override public String getManifestHash() { return "hash-abc"; }
        @Override public String getMarkerSignature() { return "sig"; }
        @Override public void reissueMarker(String h, String sg) { }
    }

    private com.example.serverprovision.global.trash.TrashPolicy trashPolicy;
    private Path trashRoot;

    @BeforeEach
    void setUp(@TempDir Path tmp) {
        isoScanner = mock(MarkableScanner.class);
        given(isoScanner.supportedType()).willReturn(ResourceType.OS_ISO);
        TrashSettingsService settings = mock(TrashSettingsService.class);
        given(settings.getRetryBackoffBaseMs()).willReturn(0L);
        PurgeLogRepository purgeLogRepository = mock(PurgeLogRepository.class);
        given(purgeLogRepository.save(any(PurgeLog.class))).willAnswer(inv -> inv.getArgument(0));
        // HF6 — 재귀 삭제의 안전 경계. 실제 레이아웃(루트/자원종류/ID/실물)을 @TempDir 로 재현한다.
        trashRoot = tmp.resolve("soft-deleted");
        trashPolicy = mock(com.example.serverprovision.global.trash.TrashPolicy.class);
        given(trashPolicy.getTrashRoot()).willReturn(trashRoot);
        executor = new PurgeExecutorImpl(List.of(isoScanner), settings, purgeLogRepository, trashPolicy);
    }

    /** 휴지통 레이아웃대로 실물을 만든다: 루트/OS_ISO/{id}/{name} */
    private Path trashedFile(Long id, String name, String body) throws Exception {
        Path p = trashRoot.resolve("OS_ISO/" + id + "/" + name);
        Files.createDirectories(p.getParent());
        Files.writeString(p, body);
        return p;
    }

    @Test
    @DisplayName("purge 성공 시 휴지통 실물 + 빈 ID 디렉토리까지 정리 — 껍데기 잔존(실측 14개) 차단")
    void purge_alsoDeletesTrashedFile(@TempDir Path tmp) throws Exception {
        Path trashed = trashedFile(42L, "dvd_x.iso", "body");
        given(isoScanner.findTrashedById(42L)).willReturn(
                Optional.of(new DeletedIso(42L, tmp.resolve("iso/dvd.iso"), trashed.toString())));

        PurgeResult result = executor.execute(PurgeRequest.forUserDirect(ResourceType.OS_ISO, 42L, "tester", "dvd"));

        assertThat(result).isInstanceOf(PurgeResult.Success.class);
        assertThat(Files.exists(trashed)).isFalse();               // S6-2-3 보강 — 실물 동반 정리
        assertThat(Files.exists(trashed.getParent())).isFalse();   // HF6 — ID 디렉토리 껍데기까지
    }

    @Test
    @DisplayName("DRIFT_TRASH_LOST 진입 : 실물이 이미 없어도 성공 + 감사 기록 (기록 정리 경로)")
    void purge_driftTrashLost_succeedsWithoutFile(@TempDir Path tmp) {
        String trashed = trashRoot.resolve("OS_ISO/42/gone.iso").toString(); // 부재
        given(isoScanner.findTrashedById(42L)).willReturn(
                Optional.of(new DeletedIso(42L, tmp.resolve("iso/dvd.iso"), trashed)));

        PurgeResult result = executor.execute(PurgeRequest.forDriftTrashLost(ResourceType.OS_ISO, 42L));

        assertThat(result).isInstanceOf(PurgeResult.Success.class);
    }

    @Test
    @DisplayName("HF6 핵심 — 디렉토리형 자원(번들 트리)도 통째로 정리된다. 종전 deleteIfExists 는 못 지우던 살아 있는 결함")
    void purge_deletesDirectoryTree(@TempDir Path tmp) throws Exception {
        Path bundle = trashRoot.resolve("OS_ISO/7/AB_CC_260628_3b5445ce");
        Files.createDirectories(bundle.resolve("docs"));
        Files.writeString(bundle.resolve("update.nsh"), "x");
        Files.writeString(bundle.resolve("docs/README.txt"), "y");
        given(isoScanner.findTrashedById(7L)).willReturn(
                Optional.of(new DeletedIso(7L, tmp.resolve("iso/bundle"), bundle.toString())));

        PurgeResult result = executor.execute(PurgeRequest.forUserDirect(ResourceType.OS_ISO, 7L, "tester", "bundle"));

        assertThat(result).isInstanceOf(PurgeResult.Success.class);
        assertThat(Files.exists(bundle)).isFalse();
        assertThat(Files.exists(bundle.getParent())).isFalse(); // ID 디렉토리까지
    }

    @Test
    @DisplayName("HF6 보수 정책 — ID 디렉토리에 다른 잔존물이 있으면 껍데기를 보존한다 (그 대사는 MK4 소관)")
    void purge_keepsIdDirectoryWhenNotEmpty(@TempDir Path tmp) throws Exception {
        Path trashed = trashedFile(42L, "dvd_x.iso", "body");
        Path leftover = trashed.getParent().resolve("older-escaped.iso");
        Files.writeString(leftover, "과거 이탈 실물");
        given(isoScanner.findTrashedById(42L)).willReturn(
                Optional.of(new DeletedIso(42L, tmp.resolve("iso/dvd.iso"), trashed.toString())));

        PurgeResult result = executor.execute(PurgeRequest.forUserDirect(ResourceType.OS_ISO, 42L, "tester", "dvd"));

        assertThat(result).isInstanceOf(PurgeResult.Success.class);
        assertThat(Files.exists(trashed)).isFalse();
        assertThat(Files.exists(leftover)).isTrue();            // 잔존물 보존
        assertThat(Files.exists(trashed.getParent())).isTrue(); // 껍데기도 보존
    }

    @Test
    @DisplayName("HF6 가드 — trashed_path 가 휴지통 루트 밖이면 아무것도 지우지 않는다 (DB 오염 · 변조 방어)")
    void purge_refusesPathOutsideTrashRoot(@TempDir Path tmp) throws Exception {
        Path decoy = tmp.resolve("outside/decoy.iso");
        Files.createDirectories(decoy.getParent());
        Files.writeString(decoy, "귀중한 파일");
        given(isoScanner.findTrashedById(42L)).willReturn(
                Optional.of(new DeletedIso(42L, tmp.resolve("iso/dvd.iso"), decoy.toString())));

        PurgeResult result = executor.execute(PurgeRequest.forUserDirect(ResourceType.OS_ISO, 42L, "tester", "dvd"));

        assertThat(result).isInstanceOf(PurgeResult.Success.class); // purge(기록 삭제)는 성공 유지
        assertThat(Files.exists(decoy)).isTrue();                   // 밖 파일은 손대지 않는다
    }

    @Test
    @DisplayName("HF6 가드 — 레이아웃보다 얕은 경로(자원종류 층)는 거부한다. 오염된 값 하나가 트리 전체를 지우는 사고 차단")
    void purge_refusesShallowPath(@TempDir Path tmp) throws Exception {
        Path typeDir = trashRoot.resolve("OS_ISO");
        Files.createDirectories(typeDir.resolve("42"));
        Files.writeString(typeDir.resolve("42/dvd.iso"), "body");
        given(isoScanner.findTrashedById(42L)).willReturn(
                Optional.of(new DeletedIso(42L, tmp.resolve("iso/dvd.iso"), typeDir.toString())));

        PurgeResult result = executor.execute(PurgeRequest.forUserDirect(ResourceType.OS_ISO, 42L, "tester", "dvd"));

        assertThat(result).isInstanceOf(PurgeResult.Success.class);
        assertThat(Files.exists(typeDir.resolve("42/dvd.iso"))).isTrue(); // 트리 보존
    }

    @Test
    @DisplayName("HF6 — 트리 안 심볼릭 링크는 링크만 지워지고 타깃은 보존된다")
    void purge_deletesSymlinkNotTarget(@TempDir Path tmp) throws Exception {
        Path precious = tmp.resolve("precious/data.bin");
        Files.createDirectories(precious.getParent());
        Files.writeString(precious, "타깃");
        Path bundle = trashRoot.resolve("OS_ISO/7/bundle_x");
        Files.createDirectories(bundle);
        Files.createSymbolicLink(bundle.resolve("link-out"), precious);
        given(isoScanner.findTrashedById(7L)).willReturn(
                Optional.of(new DeletedIso(7L, tmp.resolve("iso/bundle"), bundle.toString())));

        PurgeResult result = executor.execute(PurgeRequest.forUserDirect(ResourceType.OS_ISO, 7L, "tester", "bundle"));

        assertThat(result).isInstanceOf(PurgeResult.Success.class);
        assertThat(Files.exists(bundle)).isFalse();
        assertThat(Files.exists(precious)).isTrue(); // 링크 타깃 무사
    }
}
