package com.example.serverprovision.execution.asset.service;

import com.example.serverprovision.execution.asset.dto.response.SystemAssetDashboardResponse;
import com.example.serverprovision.execution.asset.dto.response.SystemAssetSlotResponse;
import com.example.serverprovision.execution.asset.enums.DiagnosticAsset;
import com.example.serverprovision.execution.asset.exception.DiagnosticAssetNotReplaceableException;
import com.example.serverprovision.execution.asset.exception.DiagnosticAssetReplaceEmptyException;
import com.example.serverprovision.execution.asset.exception.SystemAssetServingDisabledException;
import com.example.serverprovision.execution.config.PxeAssetsProperties;
import com.example.serverprovision.global.history.AssetHistoryService;
import com.example.serverprovision.global.history.AssetVersionKey;
import com.example.serverprovision.global.history.exception.AssetVersionArchiveFailedException;
import com.example.serverprovision.global.history.exception.AssetVersionNotFoundException;
import com.example.serverprovision.global.marker.IntegrityStatus;
import com.example.serverprovision.global.marker.service.ProvisionMarkerService;
import com.example.serverprovision.global.security.FileSystemHardener;
import com.example.serverprovision.global.security.config.FileSystemSecurityProperties;
import com.example.serverprovision.management.bios.service.BundleManifestService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * E1-I-2-b-2 CP4 — 활성화 서비스(교체 + 롤백 + 공용 코어) 검증. 실제 마커 엔진을 임시 디렉토리에 대고 돌려
 * 활성화 후 무결성이 즉시 ORIGINAL 로 재봉인됨을 실증한다. 이력 저장소는 mock — 아카이브·openVersion·remove
 * 배선과 fail-closed(아카이브 실패 → 스왑 중단)를 확인한다.
 */
class DiagnosticAssetActivationServiceTest {

    @TempDir
    Path root;
    @TempDir
    Path store;   // 롤백 버전 소스 파일 스테이징(자산 루트 밖)

    @SuppressWarnings("unchecked")
    private final ObjectProvider<PxeAssetsProperties> propertiesProvider = mock(ObjectProvider.class);

    private DiagnosticAssetIntegrityService integrityService;
    private AssetHistoryService historyService;
    private DiagnosticAssetActivationService activationService;

    @BeforeEach
    void setUp() throws IOException {
        ProvisionMarkerService markerService = new ProvisionMarkerService();
        ReflectionTestUtils.setField(markerService, "secret", "test-secret-e1i2b2");
        FileSystemHardener hardener = new FileSystemHardener(mock(FileSystemSecurityProperties.class));
        integrityService = new DiagnosticAssetIntegrityService(
                propertiesProvider, markerService, new BundleManifestService(), hardener);
        historyService = mock(AssetHistoryService.class);
        activationService = new DiagnosticAssetActivationService(integrityService, historyService, hardener);
        given(propertiesProvider.getIfAvailable())
                .willReturn(new PxeAssetsProperties(root.toString(), "http://localhost:7777"));
        stageSingleFiles();
    }

    // ── 교체(replace) — E1-I-2-a 회귀 ────────────────────────────────────────

    @Test
    @DisplayName("교체 — 디스크 내용 갱신 + 자동 재봉인 → ORIGINAL")
    void replace_swapsAndReseals() throws IOException {
        integrityService.seal();
        MultipartFile upload = file("vmlinuz-lts", "NEW-KERNEL-BYTES-v2");

        activationService.replace(DiagnosticAsset.VMLINUZ, upload);

        assertThat(Files.readString(root.resolve("vmlinuz-lts"))).isEqualTo("NEW-KERNEL-BYTES-v2");
        assertThat(slot(DiagnosticAsset.VMLINUZ).statusLabel())
                .isEqualTo(IntegrityStatus.ORIGINAL.getDisplayMessage());
    }

    @Test
    @DisplayName("드리프트 슬롯 교체 — 새 파일이 기준이 되어 ORIGINAL 복구")
    void replace_driftedSlot_recovers() throws IOException {
        integrityService.seal();
        Files.writeString(root.resolve("modloop-lts"), "TAMPER", StandardOpenOption.APPEND);
        assertThat(slot(DiagnosticAsset.MODLOOP).statusLabel())
                .isEqualTo(IntegrityStatus.TAMPERED.getDisplayMessage());

        activationService.replace(DiagnosticAsset.MODLOOP, file("modloop-lts", "REBUILT-MODLOOP"));

        assertThat(slot(DiagnosticAsset.MODLOOP).statusLabel())
                .isEqualTo(IntegrityStatus.ORIGINAL.getDisplayMessage());
    }

    @Test
    @DisplayName("원자성 — 활성화 후 임시 파일 잔존 0")
    void activate_leavesNoTempFile() throws IOException {
        activationService.replace(DiagnosticAsset.AGENT, file("agent.sh", "#!/bin/sh\necho v2\n"));

        try (Stream<Path> entries = Files.list(root)) {
            assertThat(entries.map(p -> p.getFileName().toString()))
                    .noneMatch(name -> name.contains(".activate-"));
        }
    }

    @Test
    @DisplayName("비대상(apkovl) 교체 → NotReplaceable(409)")
    void replace_notReplaceable_throws() throws IOException {
        Files.writeString(root.resolve("diag.apkovl.tar.gz"), "apkovl");
        assertThatThrownBy(() -> activationService.replace(DiagnosticAsset.APKOVL, file("diag.apkovl.tar.gz", "x")))
                .isInstanceOf(DiagnosticAssetNotReplaceableException.class);
    }

    @Test
    @DisplayName("서빙 비활성 교체 → ServingDisabled(409)")
    void replace_servingDisabled_throws() {
        given(propertiesProvider.getIfAvailable()).willReturn(null);
        assertThatThrownBy(() -> activationService.replace(DiagnosticAsset.VMLINUZ, file("vmlinuz-lts", "x")))
                .isInstanceOf(SystemAssetServingDisabledException.class);
    }

    @Test
    @DisplayName("빈 파일 교체 → ReplaceEmpty(400)")
    void replace_emptyFile_throws() {
        MultipartFile empty = new MockMultipartFile("file", "vmlinuz-lts", "application/octet-stream", new byte[0]);
        assertThatThrownBy(() -> activationService.replace(DiagnosticAsset.VMLINUZ, empty))
                .isInstanceOf(DiagnosticAssetReplaceEmptyException.class);
    }

    @Test
    @DisplayName("sealOne — 지정 슬롯만 봉인(다른 슬롯 마커 미생성)")
    void sealOne_onlyTargetSlot() {
        boolean sealed = integrityService.sealOne(DiagnosticAsset.VMLINUZ, root);

        assertThat(sealed).isTrue();
        assertThat(slot(DiagnosticAsset.VMLINUZ).statusLabel())
                .isEqualTo(IntegrityStatus.ORIGINAL.getDisplayMessage());
        assertThat(slot(DiagnosticAsset.INITRAMFS).statusLabel())
                .isEqualTo(IntegrityStatus.MARKER_MISSING.getDisplayMessage());
    }

    @Test
    @DisplayName("교체 — 스왑 전 현재(옛) 버전을 이력에 위임(키·시점 검증)")
    void replace_archivesCurrentVersionBeforeSwap() throws IOException {
        integrityService.seal();
        AtomicReference<String> seenAtArchive = new AtomicReference<>();
        given(historyService.archive(any(), any())).willAnswer(inv -> {
            seenAtArchive.set(Files.readString(inv.getArgument(1, Path.class)));   // 아카이브 시점 파일 = 옛 버전
            return Optional.empty();
        });

        activationService.replace(DiagnosticAsset.VMLINUZ, file("vmlinuz-lts", "NEW-KERNEL-v9"));

        assertThat(seenAtArchive.get()).isEqualTo("kernel-v1");                       // 아카이브는 스왑 전(옛 내용)
        assertThat(Files.readString(root.resolve("vmlinuz-lts"))).isEqualTo("NEW-KERNEL-v9");  // 디스크는 스왑 후
        ArgumentCaptor<AssetVersionKey> keyCaptor = ArgumentCaptor.forClass(AssetVersionKey.class);
        verify(historyService).archive(keyCaptor.capture(), any());
        assertThat(keyCaptor.getValue()).isEqualTo(new AssetVersionKey("DIAGNOSTIC", "vmlinuz-lts"));
    }

    @Test
    @DisplayName("교체 — 아카이브 실패 → 스왑 중단(디스크 옛 버전 유지)")
    void replace_archiveFailure_abortsSwap() throws IOException {
        given(historyService.archive(any(), any()))
                .willThrow(new AssetVersionArchiveFailedException("아카이브 실패", new IOException("boom")));

        assertThatThrownBy(() -> activationService.replace(DiagnosticAsset.VMLINUZ, file("vmlinuz-lts", "NEW")))
                .isInstanceOf(AssetVersionArchiveFailedException.class);

        assertThat(Files.readString(root.resolve("vmlinuz-lts"))).isEqualTo("kernel-v1");   // 스왑 미실행
    }

    // ── 롤백(rollback) — E1-I-2-b-2 신규 ─────────────────────────────────────

    @Test
    @DisplayName("롤백 — 선택 버전 바이트로 활성본 갱신 + 현재본 archive + 재봉인 + 버전 제거")
    void rollback_restoresVersion() throws IOException {
        integrityService.seal();
        AssetVersionKey key = new AssetVersionKey("DIAGNOSTIC", "vmlinuz-lts");
        Path versionFile = Files.writeString(store.resolve("archived-v3"), "KERNEL-v3-RESTORED");
        given(historyService.openVersion(key, 3L)).willReturn(versionFile);
        AtomicReference<String> archivedContent = new AtomicReference<>();
        given(historyService.archive(any(), any())).willAnswer(inv -> {
            archivedContent.set(Files.readString(inv.getArgument(1, Path.class)));   // archive 시점 = 현재 활성본
            return Optional.empty();
        });

        activationService.rollback(DiagnosticAsset.VMLINUZ, 3L);

        assertThat(Files.readString(root.resolve("vmlinuz-lts"))).isEqualTo("KERNEL-v3-RESTORED");  // 활성본 = 버전 바이트
        assertThat(archivedContent.get()).isEqualTo("kernel-v1");                                    // 현재본이 archive 됨
        assertThat(slot(DiagnosticAsset.VMLINUZ).statusLabel())
                .isEqualTo(IntegrityStatus.ORIGINAL.getDisplayMessage());                            // 재봉인
        verify(historyService).remove(key, 3L);                                                       // 되살린 버전 제거(승격)
    }

    @Test
    @DisplayName("롤백 — 없는/타슬롯 버전 → NotFound(404)")
    void rollback_versionNotFound_throws() {
        AssetVersionKey key = new AssetVersionKey("DIAGNOSTIC", "vmlinuz-lts");
        given(historyService.openVersion(key, 99L)).willThrow(AssetVersionNotFoundException.of(key, 99L));

        assertThatThrownBy(() -> activationService.rollback(DiagnosticAsset.VMLINUZ, 99L))
                .isInstanceOf(AssetVersionNotFoundException.class);
    }

    @Test
    @DisplayName("롤백 — 비대상(apkovl) → NotReplaceable(409)")
    void rollback_notReplaceable_throws() {
        assertThatThrownBy(() -> activationService.rollback(DiagnosticAsset.APKOVL, 1L))
                .isInstanceOf(DiagnosticAssetNotReplaceableException.class);
    }

    @Test
    @DisplayName("롤백 — 서빙 비활성 → ServingDisabled(409)")
    void rollback_servingDisabled_throws() {
        given(propertiesProvider.getIfAvailable()).willReturn(null);
        assertThatThrownBy(() -> activationService.rollback(DiagnosticAsset.VMLINUZ, 1L))
                .isInstanceOf(SystemAssetServingDisabledException.class);
    }

    @Test
    @DisplayName("롤백 — 아카이브 실패 → 스왑 중단(활성본 유지, 버전 미제거)")
    void rollback_archiveFailure_abortsSwap() throws IOException {
        AssetVersionKey key = new AssetVersionKey("DIAGNOSTIC", "vmlinuz-lts");
        Path versionFile = Files.writeString(store.resolve("archived-v3"), "KERNEL-v3-RESTORED");
        given(historyService.openVersion(key, 3L)).willReturn(versionFile);
        given(historyService.archive(any(), any()))
                .willThrow(new AssetVersionArchiveFailedException("아카이브 실패", new IOException("boom")));

        assertThatThrownBy(() -> activationService.rollback(DiagnosticAsset.VMLINUZ, 3L))
                .isInstanceOf(AssetVersionArchiveFailedException.class);

        assertThat(Files.readString(root.resolve("vmlinuz-lts"))).isEqualTo("kernel-v1");   // 스왑 미실행
        verify(historyService, never()).remove(any(), any());                               // 버전 미제거
    }

    // ── 픽스처 ──────────────────────────────────────────────────────────────

    private void stageSingleFiles() throws IOException {
        Files.writeString(root.resolve("vmlinuz-lts"), "kernel-v1");
        Files.writeString(root.resolve("initramfs-lts"), "initramfs-v1");
        Files.writeString(root.resolve("modloop-lts"), "modloop-v1");
        Files.writeString(root.resolve("agent.sh"), "#!/bin/sh\necho v1\n");
    }

    private static MultipartFile file(String filename, String content) {
        return new MockMultipartFile("file", filename, "application/octet-stream",
                content.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    private SystemAssetSlotResponse slot(DiagnosticAsset asset) {
        SystemAssetDashboardResponse dashboard = integrityService.loadDashboard();
        List<SystemAssetSlotResponse> slots = dashboard.slots();
        return slots.stream()
                .filter(s -> s.filename().equals(asset.filename()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("슬롯 없음 : " + asset.filename()));
    }
}
