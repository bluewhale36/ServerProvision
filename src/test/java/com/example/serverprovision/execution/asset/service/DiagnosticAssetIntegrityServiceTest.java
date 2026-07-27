package com.example.serverprovision.execution.asset.service;

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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

/**
 * 진단 자산 무결성 서비스의 <b>단일 슬롯 조회 + 교체·롤백 부품</b> 검증. 통합 대시보드의 6종 집계·전역 봉인은
 * {@code DiagnosticSystemAssetArea} 어댑터가 담당하므로(그쪽은 {@code DiagnosticSystemAssetAreaTest} 가 검증),
 * 여기서는 상세 화면의 단일 슬롯 조회({@code loadSlot})·서빙 게이트({@code isServing}/{@code requireServingRoot})·
 * 교체 재봉인 부품({@code sealOne})만 실제 {@link ProvisionMarkerService}·{@link BundleManifestService} 를 임시
 * 디렉토리에 대고 돌려 확인한다. 판정 문자열은 {@link IntegrityStatus} 를 승계한다. 물리 불요.
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
        SealedFileInspector inspector = new SealedFileInspector(markerService, new BundleManifestService(), hardener);
        service = new DiagnosticAssetIntegrityService(propertiesProvider, inspector);
        given(propertiesProvider.getIfAvailable())
                .willReturn(new PxeAssetsProperties(root.toString(), "http://localhost:7777"));
    }

    // ── 단일 슬롯 조회(상세 화면) ────────────────────────────────────────────

    @Test
    @DisplayName("loadSlot — 봉인 후 단일 슬롯만 조회, ORIGINAL")
    void loadSlot_afterSeal_original() throws IOException {
        Files.writeString(root.resolve("vmlinuz-lts"), "kernel-bytes");
        service.sealOne(DiagnosticAsset.VMLINUZ, root);

        SystemAssetSlotResponse slot = service.loadSlot(DiagnosticAsset.VMLINUZ);

        assertThat(slot.key()).isEqualTo("VMLINUZ");
        assertThat(slot.present()).isTrue();
        assertThat(slot.statusLabel()).isEqualTo(IntegrityStatus.ORIGINAL.getDisplayMessage());
    }

    @Test
    @DisplayName("loadSlot — 봉인 전 단일 슬롯은 MARKER_MISSING(기준선 미설정)")
    void loadSlot_beforeSeal_markerMissing() throws IOException {
        Files.writeString(root.resolve("vmlinuz-lts"), "kernel-bytes");

        SystemAssetSlotResponse slot = service.loadSlot(DiagnosticAsset.VMLINUZ);

        assertThat(slot.statusLabel()).isEqualTo(IntegrityStatus.MARKER_MISSING.getDisplayMessage());
    }

    @Test
    @DisplayName("loadSlot — 서빙 비활성 시 off 상태 슬롯(예외 없이 반환)")
    void loadSlot_servingDisabled_offSlot() {
        given(propertiesProvider.getIfAvailable()).willReturn(null);

        SystemAssetSlotResponse slot = service.loadSlot(DiagnosticAsset.VMLINUZ);

        assertThat(slot.key()).isEqualTo("VMLINUZ");
        assertThat(slot.statusLabel()).isEqualTo("서빙 비활성");
        assertThat(slot.sizeDisplay()).isEqualTo("—");
    }

    // ── 서빙 게이트 ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("isServing — props 유무를 반영(서버 가드와 동일 조건)")
    void isServing_reflectsProps() {
        assertThat(service.isServing()).isTrue();

        given(propertiesProvider.getIfAvailable()).willReturn(null);
        assertThat(service.isServing()).isFalse();
    }

    @Test
    @DisplayName("requireServingRoot — 서빙 비활성이면 409(교체·롤백 전제 가드)")
    void requireServingRoot_servingDisabled_throws() {
        given(propertiesProvider.getIfAvailable()).willReturn(null);

        assertThatThrownBy(() -> service.requireServingRoot())
                .isInstanceOf(SystemAssetServingDisabledException.class);
    }

    // ── 교체 재봉인 부품(sealOne) ────────────────────────────────────────────

    @Test
    @DisplayName("sealOne — 존재하는 슬롯은 마커 기록(true), 이후 조회는 ORIGINAL")
    void sealOne_present_recordsMarker() throws IOException {
        Files.writeString(root.resolve("vmlinuz-lts"), "kernel-bytes");

        boolean recorded = service.sealOne(DiagnosticAsset.VMLINUZ, root);

        assertThat(recorded).isTrue();
        assertThat(service.loadSlot(DiagnosticAsset.VMLINUZ).statusLabel())
                .isEqualTo(IntegrityStatus.ORIGINAL.getDisplayMessage());
    }

    @Test
    @DisplayName("sealOne — 부재 슬롯은 건너뜀(false), 마커를 만들지 않는다")
    void sealOne_absent_skips() {
        boolean recorded = service.sealOne(DiagnosticAsset.VMLINUZ, root);

        assertThat(recorded).isFalse();
    }
}
