package com.example.serverprovision.execution.asset.area;

import com.example.serverprovision.execution.asset.dto.SealResult;
import com.example.serverprovision.execution.asset.enums.DiagnosticAsset;
import com.example.serverprovision.execution.asset.exception.SystemAssetServingDisabledException;
import com.example.serverprovision.execution.asset.service.SealedFileInspector;
import com.example.serverprovision.execution.asset.spi.AreaAvailability;
import com.example.serverprovision.execution.asset.spi.AssetSlotStatus;
import com.example.serverprovision.execution.asset.spi.SystemAssetSlot;
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
 * 진단 리눅스 자산 영역({@link DiagnosticSystemAssetArea})의 프로덕션 집계·봉인 경로 검증. 통합 대시보드가
 * 실제로 렌더하는 경로가 이 어댑터의 {@code inspect}/{@code seal}/{@code availability} 이므로, 진단 6슬롯의
 * 오케스트레이션(전량 봉인·부재 건너뜀·디렉토리 변조·서빙 비활성)을 여기서 실증한다. 판정 사다리 자체의 전수
 * 검증은 공유 부품 {@code SealedFileInspectorTest} 가, 단일 슬롯 상세 조회는 {@code DiagnosticAssetIntegrityServiceTest}
 * 가 담당한다(집계·상세·부품이 각자 자기 층을 테스트 — 판정 문자열은 {@link IntegrityStatus} 를 승계). 물리 불요.
 */
class DiagnosticSystemAssetAreaTest {

    @TempDir
    Path root;

    private DiagnosticSystemAssetArea area;

    @SuppressWarnings("unchecked")
    private final ObjectProvider<PxeAssetsProperties> propertiesProvider = mock(ObjectProvider.class);

    @BeforeEach
    void setUp() {
        ProvisionMarkerService markerService = new ProvisionMarkerService();
        ReflectionTestUtils.setField(markerService, "secret", "test-secret-e1i3a");
        FileSystemHardener hardener = new FileSystemHardener(mock(FileSystemSecurityProperties.class));
        SealedFileInspector inspector = new SealedFileInspector(markerService, new BundleManifestService(), hardener);
        area = new DiagnosticSystemAssetArea(propertiesProvider, inspector);
        given(propertiesProvider.getIfAvailable())
                .willReturn(new PxeAssetsProperties(root.toString(), "http://localhost:7777"));
    }

    // ── 봉인 → 집계 ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("봉인 후 집계 — 존재하는 6 슬롯 전부 ORIGINAL, healthy=6, SealResult(6,0)")
    void seal_thenInspect_allOriginal() throws IOException {
        stageAllAssets();

        SealResult result = area.seal();
        assertThat(result.sealed()).isEqualTo(6);
        assertThat(result.skipped()).isZero();

        assertThat(area.availability()).isEqualTo(AreaAvailability.CONFIGURED);
        assertThat(healthyCount()).isEqualTo(6);
        for (DiagnosticAsset asset : DiagnosticAsset.values()) {
            assertThat(label(asset)).isEqualTo(IntegrityStatus.ORIGINAL.getDisplayMessage());
        }
    }

    @Test
    @DisplayName("봉인 전 집계 — 마커 없음이라 전부 MARKER_MISSING, healthy=0")
    void noSeal_allMarkerMissing() throws IOException {
        stageAllAssets();

        assertThat(healthyCount()).isZero();
        for (DiagnosticAsset asset : DiagnosticAsset.values()) {
            assertThat(label(asset)).isEqualTo(IntegrityStatus.MARKER_MISSING.getDisplayMessage());
        }
    }

    // ── 드리프트 탐지 ───────────────────────────────────────────────────────

    @Test
    @DisplayName("봉인 후 단일 파일 변조 — 해당 슬롯만 TAMPERED, healthy=5")
    void tamperedFile_detected() throws IOException {
        stageAllAssets();
        area.seal();

        Files.writeString(root.resolve("modloop-lts"), "TAMPERED-bytes", StandardOpenOption.APPEND);

        assertThat(label(DiagnosticAsset.MODLOOP)).isEqualTo(IntegrityStatus.TAMPERED.getDisplayMessage());
        assertThat(healthyCount()).isEqualTo(5);
    }

    @Test
    @DisplayName("봉인 후 디렉토리(repo) 멤버 추가 — repo 슬롯 TAMPERED")
    void tamperedDirectory_detected() throws IOException {
        stageAllAssets();
        area.seal();

        Files.writeString(root.resolve("repo/main/x86_64/ipmitool-1.0.apk"), "new-pkg");

        assertThat(label(DiagnosticAsset.REPO)).isEqualTo(IntegrityStatus.TAMPERED.getDisplayMessage());
    }

    @Test
    @DisplayName("마커 손상(파싱 불가) — SIGNATURE_INVALID")
    void corruptMarker_signatureInvalid() throws IOException {
        stageAllAssets();
        area.seal();

        Files.writeString(root.resolve("vmlinuz-lts.provision.json"), "not-a-valid-json");

        assertThat(label(DiagnosticAsset.VMLINUZ)).isEqualTo(IntegrityStatus.SIGNATURE_INVALID.getDisplayMessage());
    }

    @Test
    @DisplayName("봉인 → 변조 → 재봉인 — ORIGINAL 로 복구(현재 상태가 새 기준)")
    void sealTamperReseal_recovers() throws IOException {
        stageAllAssets();
        area.seal();
        Files.writeString(root.resolve("initramfs-lts"), "changed", StandardOpenOption.APPEND);
        assertThat(label(DiagnosticAsset.INITRAMFS)).isEqualTo(IntegrityStatus.TAMPERED.getDisplayMessage());

        area.seal();

        assertThat(label(DiagnosticAsset.INITRAMFS)).isEqualTo(IntegrityStatus.ORIGINAL.getDisplayMessage());
    }

    // ── 부재 ────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("자산 부재 — 예외 아닌 부재 상태, 봉인은 그 슬롯 건너뜀 SealResult(5,1)")
    void absentAsset_isStatus_and_sealSkips() throws IOException {
        stageAllAssets();
        Files.delete(root.resolve("agent.sh"));

        SealResult result = area.seal();
        assertThat(result.sealed()).isEqualTo(5);
        assertThat(result.skipped()).isEqualTo(1);

        AssetSlotStatus agent = inspect(DiagnosticAsset.AGENT);
        assertThat(agent.present()).isFalse();
        assertThat(agent.condition().label()).isEqualTo("자산 없음");
    }

    // ── 서빙 비활성 ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("서빙 비활성 — 집계는 오류 없이 전부 서빙 비활성, 봉인은 409 거절")
    void servingDisabled_inspectGraceful_sealRejected() {
        given(propertiesProvider.getIfAvailable()).willReturn(null);

        assertThat(area.availability()).isEqualTo(AreaAvailability.NOT_CONFIGURED);
        for (DiagnosticAsset asset : DiagnosticAsset.values()) {
            assertThat(label(asset)).isEqualTo("서빙 비활성");
        }

        assertThatThrownBy(() -> area.seal())
                .isInstanceOf(SystemAssetServingDisabledException.class);
    }

    // ── 헬퍼 ────────────────────────────────────────────────────────────────

    /** 이 영역이 발급한 슬롯 중 주어진 enum 에 해당하는 것을 프로브한다(slotKey = enum 상수명). */
    private AssetSlotStatus inspect(DiagnosticAsset asset) {
        SystemAssetSlot slot = area.slots().stream()
                .filter(s -> s.slotKey().equals(asset.name()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("슬롯 없음 : " + asset.name()));
        return area.inspect(slot);
    }

    private String label(DiagnosticAsset asset) {
        return inspect(asset).condition().label();
    }

    private long healthyCount() {
        return area.slots().stream().filter(s -> area.inspect(s).condition().healthy()).count();
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
}
