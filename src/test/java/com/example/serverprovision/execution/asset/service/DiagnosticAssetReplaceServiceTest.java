package com.example.serverprovision.execution.asset.service;

import com.example.serverprovision.execution.asset.dto.response.SystemAssetDashboardResponse;
import com.example.serverprovision.execution.asset.dto.response.SystemAssetSlotResponse;
import com.example.serverprovision.execution.asset.enums.DiagnosticAsset;
import com.example.serverprovision.execution.asset.exception.DiagnosticAssetNotReplaceableException;
import com.example.serverprovision.execution.asset.exception.DiagnosticAssetReplaceEmptyException;
import com.example.serverprovision.execution.asset.exception.SystemAssetServingDisabledException;
import com.example.serverprovision.execution.config.PxeAssetsProperties;
import com.example.serverprovision.global.marker.IntegrityStatus;
import com.example.serverprovision.global.marker.service.ProvisionMarkerService;
import com.example.serverprovision.global.security.FileSystemHardener;
import com.example.serverprovision.global.security.config.FileSystemSecurityProperties;
import com.example.serverprovision.management.bios.service.BundleManifestService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

/**
 * E1-I-2-a CP4 — 교체(원자적 스왑 + 자동 재봉인)와 가드 검증. 실제 마커 엔진을 임시 디렉토리에 대고 돌려
 * 교체 후 무결성이 즉시 ORIGINAL 로 재봉인됨을 실증한다.
 */
class DiagnosticAssetReplaceServiceTest {

    @TempDir
    Path root;

    @SuppressWarnings("unchecked")
    private final ObjectProvider<PxeAssetsProperties> propertiesProvider = mock(ObjectProvider.class);

    private DiagnosticAssetIntegrityService integrityService;
    private DiagnosticAssetReplaceService replaceService;

    @BeforeEach
    void setUp() throws IOException {
        ProvisionMarkerService markerService = new ProvisionMarkerService();
        ReflectionTestUtils.setField(markerService, "secret", "test-secret-e1i2a");
        FileSystemHardener hardener = new FileSystemHardener(mock(FileSystemSecurityProperties.class));
        integrityService = new DiagnosticAssetIntegrityService(
                propertiesProvider, markerService, new BundleManifestService(), hardener);
        replaceService = new DiagnosticAssetReplaceService(integrityService, hardener);
        given(propertiesProvider.getIfAvailable())
                .willReturn(new PxeAssetsProperties(root.toString(), "http://localhost:7777"));
        stageSingleFiles();
    }

    @Test
    @DisplayName("교체 — 디스크 내용 갱신 + 자동 재봉인 → ORIGINAL")
    void replace_swapsAndReseals() throws IOException {
        integrityService.seal();
        MultipartFile upload = file("vmlinuz-lts", "NEW-KERNEL-BYTES-v2");

        replaceService.replace(DiagnosticAsset.VMLINUZ, upload);

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

        replaceService.replace(DiagnosticAsset.MODLOOP, file("modloop-lts", "REBUILT-MODLOOP"));

        assertThat(slot(DiagnosticAsset.MODLOOP).statusLabel())
                .isEqualTo(IntegrityStatus.ORIGINAL.getDisplayMessage());
    }

    @Test
    @DisplayName("원자성 — 교체 후 임시 파일 잔존 0")
    void replace_leavesNoTempFile() throws IOException {
        replaceService.replace(DiagnosticAsset.AGENT, file("agent.sh", "#!/bin/sh\necho v2\n"));

        try (Stream<Path> entries = Files.list(root)) {
            assertThat(entries.map(p -> p.getFileName().toString()))
                    .noneMatch(name -> name.contains(".replace-"));
        }
    }

    @Test
    @DisplayName("비대상(apkovl) 교체 → NotReplaceable(409)")
    void replace_notReplaceable_throws() throws IOException {
        Files.writeString(root.resolve("diag.apkovl.tar.gz"), "apkovl");
        assertThatThrownBy(() -> replaceService.replace(DiagnosticAsset.APKOVL, file("diag.apkovl.tar.gz", "x")))
                .isInstanceOf(DiagnosticAssetNotReplaceableException.class);
    }

    @Test
    @DisplayName("서빙 비활성 교체 → ServingDisabled(409)")
    void replace_servingDisabled_throws() {
        given(propertiesProvider.getIfAvailable()).willReturn(null);
        assertThatThrownBy(() -> replaceService.replace(DiagnosticAsset.VMLINUZ, file("vmlinuz-lts", "x")))
                .isInstanceOf(SystemAssetServingDisabledException.class);
    }

    @Test
    @DisplayName("빈 파일 교체 → ReplaceEmpty(400)")
    void replace_emptyFile_throws() {
        MultipartFile empty = new MockMultipartFile("file", "vmlinuz-lts", "application/octet-stream", new byte[0]);
        assertThatThrownBy(() -> replaceService.replace(DiagnosticAsset.VMLINUZ, empty))
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
