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

    @BeforeEach
    void setUp() {
        isoScanner = mock(MarkableScanner.class);
        given(isoScanner.supportedType()).willReturn(ResourceType.OS_ISO);
        TrashSettingsService settings = mock(TrashSettingsService.class);
        given(settings.getRetryBackoffBaseMs()).willReturn(0L);
        PurgeLogRepository purgeLogRepository = mock(PurgeLogRepository.class);
        given(purgeLogRepository.save(any(PurgeLog.class))).willAnswer(inv -> inv.getArgument(0));
        executor = new PurgeExecutorImpl(List.of(isoScanner), settings, purgeLogRepository);
    }

    @Test
    @DisplayName("purge 성공 시 휴지통 실물도 함께 정리 — 종전엔 스캔 제외 구역에 영구 잔존하던 공백")
    void purge_alsoDeletesTrashedFile(@TempDir Path tmp) throws Exception {
        Path trashed = tmp.resolve("trash/dvd_x.iso");
        Files.createDirectories(trashed.getParent());
        Files.writeString(trashed, "body");
        given(isoScanner.findTrashedById(42L)).willReturn(
                Optional.of(new DeletedIso(42L, tmp.resolve("iso/dvd.iso"), trashed.toString())));

        PurgeResult result = executor.execute(PurgeRequest.forUserDirect(ResourceType.OS_ISO, 42L, "tester", "dvd"));

        assertThat(result).isInstanceOf(PurgeResult.Success.class);
        assertThat(Files.exists(trashed)).isFalse(); // S6-2-3 보강 — 실물 동반 정리
    }

    @Test
    @DisplayName("DRIFT_TRASH_LOST 진입 : 실물이 이미 없어도 성공 + 감사 기록 (기록 정리 경로)")
    void purge_driftTrashLost_succeedsWithoutFile(@TempDir Path tmp) {
        String trashed = tmp.resolve("trash/gone.iso").toString(); // 부재
        given(isoScanner.findTrashedById(42L)).willReturn(
                Optional.of(new DeletedIso(42L, tmp.resolve("iso/dvd.iso"), trashed)));

        PurgeResult result = executor.execute(PurgeRequest.forDriftTrashLost(ResourceType.OS_ISO, 42L));

        assertThat(result).isInstanceOf(PurgeResult.Success.class);
    }
}
