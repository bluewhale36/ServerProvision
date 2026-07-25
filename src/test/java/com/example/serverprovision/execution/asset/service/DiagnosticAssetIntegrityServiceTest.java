package com.example.serverprovision.execution.asset.service;

import com.example.serverprovision.execution.asset.dto.SealResult;
import com.example.serverprovision.execution.asset.dto.response.SystemAssetDashboardResponse;
import com.example.serverprovision.execution.asset.dto.response.SystemAssetSlotResponse;
import com.example.serverprovision.execution.asset.enums.DiagnosticAsset;
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
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

/**
 * E1-I-1 CP4 — 진단 자산 무결성 판정표 전수 검증. 실제 {@link ProvisionMarkerService}·{@link BundleManifestService}
 * 를 임시 디렉토리에 대고 돌려(엔진 재사용의 실증) 봉인·검증이 같은 해시로 정합함을 확인한다. 물리 불요.
 */
class DiagnosticAssetIntegrityServiceTest {

    @TempDir
    Path root;

    private DiagnosticAssetIntegrityService service;

    @SuppressWarnings("unchecked")
    private final ObjectProvider<PxeAssetsProperties> propertiesProvider = mock(ObjectProvider.class);

    @BeforeEach
    void setUp() {
        ProvisionMarkerService markerService = new ProvisionMarkerService();
        ReflectionTestUtils.setField(markerService, "secret", "test-secret-e1i1");
        FileSystemHardener hardener = new FileSystemHardener(mock(FileSystemSecurityProperties.class));
        service = new DiagnosticAssetIntegrityService(
                propertiesProvider, markerService, new BundleManifestService(), hardener);
        given(propertiesProvider.getIfAvailable())
                .willReturn(new PxeAssetsProperties(root.toString(), "http://localhost:7777"));
    }

    // ── 봉인 → 조회 ────────────────────────────────────────────────────────

    @Test
    @DisplayName("봉인 후 조회 — 존재하는 6 슬롯 전부 ORIGINAL, okCount=6")
    void seal_thenDashboard_allOriginal() throws IOException {
        stageAllAssets();

        SealResult result = service.seal();
        assertThat(result.sealed()).isEqualTo(6);
        assertThat(result.skipped()).isZero();

        SystemAssetDashboardResponse dashboard = service.loadDashboard();
        assertThat(dashboard.servingActive()).isTrue();
        assertThat(dashboard.okCount()).isEqualTo(6);
        assertThat(dashboard.totalCount()).isEqualTo(6);
        assertThat(dashboard.slots())
                .allSatisfy(slot -> assertThat(slot.statusLabel()).isEqualTo(IntegrityStatus.ORIGINAL.getDisplayMessage()));
    }

    @Test
    @DisplayName("봉인 전 조회 — 마커 없음이라 전부 MARKER_MISSING, okCount=0")
    void noSeal_allMarkerMissing() throws IOException {
        stageAllAssets();

        SystemAssetDashboardResponse dashboard = service.loadDashboard();

        assertThat(dashboard.okCount()).isZero();
        assertThat(dashboard.slots())
                .allSatisfy(slot -> assertThat(slot.statusLabel()).isEqualTo(IntegrityStatus.MARKER_MISSING.getDisplayMessage()));
    }

    // ── 단일 슬롯 조회(E1-I-2-b-2 재구성 — 상세 화면) ────────────────────────

    @Test
    @DisplayName("loadSlot — 봉인 후 단일 슬롯만 조회, ORIGINAL")
    void loadSlot_afterSeal_original() throws IOException {
        stageAllAssets();
        service.seal();

        SystemAssetSlotResponse slot = service.loadSlot(DiagnosticAsset.VMLINUZ);

        assertThat(slot.key()).isEqualTo("VMLINUZ");
        assertThat(slot.statusLabel()).isEqualTo(IntegrityStatus.ORIGINAL.getDisplayMessage());
    }

    @Test
    @DisplayName("loadSlot — 서빙 비활성 시 off 상태 슬롯(예외 없이 반환)")
    void loadSlot_servingDisabled_offSlot() {
        given(propertiesProvider.getIfAvailable()).willReturn(null);

        SystemAssetSlotResponse slot = service.loadSlot(DiagnosticAsset.VMLINUZ);

        assertThat(slot.key()).isEqualTo("VMLINUZ");
        assertThat(slot.statusLabel()).isNotEqualTo(IntegrityStatus.ORIGINAL.getDisplayMessage());
    }

    @Test
    @DisplayName("isServing — props 유무를 반영(서버 가드와 동일 조건)")
    void isServing_reflectsProps() {
        assertThat(service.isServing()).isTrue();

        given(propertiesProvider.getIfAvailable()).willReturn(null);
        assertThat(service.isServing()).isFalse();
    }

    // ── 드리프트 탐지 ───────────────────────────────────────────────────────

    @Test
    @DisplayName("봉인 후 단일 파일 변조 — 해당 슬롯만 TAMPERED, 나머지 ORIGINAL")
    void tamperedFile_detected() throws IOException {
        stageAllAssets();
        service.seal();

        Files.writeString(root.resolve("modloop-lts"), "TAMPERED-bytes", StandardOpenOption.APPEND);

        SystemAssetDashboardResponse dashboard = service.loadDashboard();
        assertThat(slot(dashboard, DiagnosticAsset.MODLOOP).statusLabel())
                .isEqualTo(IntegrityStatus.TAMPERED.getDisplayMessage());
        assertThat(dashboard.okCount()).isEqualTo(5);
    }

    @Test
    @DisplayName("봉인 후 디렉토리(repo) 멤버 추가 — repo 슬롯 TAMPERED")
    void tamperedDirectory_detected() throws IOException {
        stageAllAssets();
        service.seal();

        Files.writeString(root.resolve("repo/main/x86_64/ipmitool-1.0.apk"), "new-pkg");

        SystemAssetDashboardResponse dashboard = service.loadDashboard();
        assertThat(slot(dashboard, DiagnosticAsset.REPO).statusLabel())
                .isEqualTo(IntegrityStatus.TAMPERED.getDisplayMessage());
    }

    @Test
    @DisplayName("마커 손상(파싱 불가) — SIGNATURE_INVALID")
    void corruptMarker_signatureInvalid() throws IOException {
        stageAllAssets();
        service.seal();

        Files.writeString(root.resolve("vmlinuz-lts.provision.json"), "not-a-valid-json");

        SystemAssetDashboardResponse dashboard = service.loadDashboard();
        assertThat(slot(dashboard, DiagnosticAsset.VMLINUZ).statusLabel())
                .isEqualTo(IntegrityStatus.SIGNATURE_INVALID.getDisplayMessage());
    }

    @Test
    @DisplayName("봉인 → 변조 → 재봉인 — ORIGINAL 로 복구(현재 상태가 새 기준)")
    void sealTamperReseal_recovers() throws IOException {
        stageAllAssets();
        service.seal();
        Files.writeString(root.resolve("initramfs-lts"), "changed", StandardOpenOption.APPEND);
        assertThat(slot(service.loadDashboard(), DiagnosticAsset.INITRAMFS).statusLabel())
                .isEqualTo(IntegrityStatus.TAMPERED.getDisplayMessage());

        service.seal();

        assertThat(slot(service.loadDashboard(), DiagnosticAsset.INITRAMFS).statusLabel())
                .isEqualTo(IntegrityStatus.ORIGINAL.getDisplayMessage());
    }

    // ── 부재 ────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("자산 부재 — 예외 아닌 ABSENT 상태(자산 없음), 봉인은 그 슬롯 건너뜀")
    void absentAsset_isStatus_and_sealSkips() throws IOException {
        stageAllAssets();
        Files.delete(root.resolve("agent.sh"));

        SealResult result = service.seal();
        assertThat(result.sealed()).isEqualTo(5);
        assertThat(result.skipped()).isEqualTo(1);

        SystemAssetSlotResponse agent = slot(service.loadDashboard(), DiagnosticAsset.AGENT);
        assertThat(agent.present()).isFalse();
        assertThat(agent.statusLabel()).isEqualTo("자산 없음");
        assertThat(agent.sizeDisplay()).isEqualTo("—");
    }

    // ── 서빙 비활성 ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("서빙 비활성 — 대시보드는 오류 없이 조회, 봉인은 409 거절")
    void servingDisabled_dashboardGraceful_sealRejected() {
        given(propertiesProvider.getIfAvailable()).willReturn(null);

        SystemAssetDashboardResponse dashboard = service.loadDashboard();
        assertThat(dashboard.servingActive()).isFalse();
        assertThat(dashboard.assetsRoot()).isNull();
        assertThat(dashboard.okCount()).isZero();
        assertThat(dashboard.slots())
                .allSatisfy(slot -> assertThat(slot.statusLabel()).isEqualTo("서빙 비활성"));

        assertThatThrownBy(() -> service.seal())
                .isInstanceOf(SystemAssetServingDisabledException.class);
    }

    // ── 픽스처 ──────────────────────────────────────────────────────────────

    private void stageAllAssets() throws IOException {
        Files.writeString(root.resolve("vmlinuz-lts"), "kernel-bytes");
        Files.writeString(root.resolve("initramfs-lts"), "initramfs-bytes");
        Files.writeString(root.resolve("modloop-lts"), "modloop-bytes");
        Files.writeString(root.resolve("diag.apkovl.tar.gz"), "apkovl-bytes");
        Files.writeString(root.resolve("agent.sh"), "#!/bin/sh\necho diag\n");
        Path repoArch = root.resolve("repo/main/x86_64");
        Files.createDirectories(repoArch);
        Files.writeString(repoArch.resolve("APKINDEX.tar.gz"), "index-bytes");
        Files.writeString(repoArch.resolve("dmidecode-3.5.apk"), "dmidecode-pkg");
    }

    private static SystemAssetSlotResponse slot(SystemAssetDashboardResponse dashboard, DiagnosticAsset asset) {
        return dashboard.slots().stream()
                .filter(s -> s.filename().equals(asset.filename()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("슬롯 없음 : " + asset.filename()));
    }
}
