package com.example.serverprovision.execution.asset.area;

import com.example.serverprovision.execution.asset.dto.SealResult;
import com.example.serverprovision.execution.asset.service.SealedFileInspector;
import com.example.serverprovision.execution.asset.spi.AreaAvailability;
import com.example.serverprovision.execution.asset.spi.AssetSlotStatus;
import com.example.serverprovision.execution.asset.spi.SealedFileCondition;
import com.example.serverprovision.execution.asset.spi.SystemAssetSlot;
import com.example.serverprovision.execution.config.TftpAssetsProperties;
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
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

/**
 * E1-I-3-a CP4 — TFTP 자산 영역 어댑터 검증. 진단 자산과 같은 파일 봉인 모델을 {@link SealedFileInspector} 에
 * 위임하므로, 서빙 비활성 흡수(NOT_CONFIGURED)와 봉인 전/후·변조의 판정 정합을 실제 마커 엔진으로 확인한다.
 * 미구성 환경은 {@code ObjectProvider} 가 null 을 반환하는 것으로 재연한다({@code @ConditionalOnProperty} 동형).
 */
class TftpAssetAreaTest {

    @TempDir
    Path root;

    @SuppressWarnings("unchecked")
    private final ObjectProvider<TftpAssetsProperties> propertiesProvider = mock(ObjectProvider.class);

    private TftpAssetArea area;

    @BeforeEach
    void setUp() {
        ProvisionMarkerService markerService = new ProvisionMarkerService();
        ReflectionTestUtils.setField(markerService, "secret", "test-secret-tftp");
        FileSystemHardener hardener = new FileSystemHardener(mock(FileSystemSecurityProperties.class));
        SealedFileInspector inspector = new SealedFileInspector(markerService, new BundleManifestService(), hardener);
        area = new TftpAssetArea(propertiesProvider, inspector);
        given(propertiesProvider.getIfAvailable()).willReturn(new TftpAssetsProperties(root.toString()));
    }

    private SystemAssetSlot ipxeSlot() {
        return area.slots().get(0);   // IPXE_EFI (고정 1종)
    }

    private Path ipxeFile() {
        return root.resolve("ipxe.efi");
    }

    @Test
    @DisplayName("경로 미구성 — availability NOT_CONFIGURED, 조회는 서빙 비활성으로 흡수(예외 없음)")
    void notConfigured_absorbedAsServingDisabled() {
        given(propertiesProvider.getIfAvailable()).willReturn(null);

        assertThat(area.availability()).isEqualTo(AreaAvailability.NOT_CONFIGURED);
        AssetSlotStatus status = area.inspect(ipxeSlot());
        assertThat(status.present()).isFalse();
        assertThat(status.condition()).isEqualTo(SealedFileCondition.NOT_CONFIGURED);
    }

    @Test
    @DisplayName("파일 부재 — 서빙 활성이나 ipxe.efi 없음 → MISSING")
    void fileAbsent_missing() {
        AssetSlotStatus status = area.inspect(ipxeSlot());

        assertThat(status.present()).isFalse();
        assertThat(status.condition()).isEqualTo(SealedFileCondition.MISSING);
    }

    @Test
    @DisplayName("봉인 전 — 파일은 있으나 마커 없음 → MARKER_MISSING")
    void beforeSeal_markerMissing() throws IOException {
        Files.writeString(ipxeFile(), "efi-bytes-v1");

        assertThat(area.inspect(ipxeSlot()).condition()).isEqualTo(SealedFileCondition.MARKER_MISSING);
    }

    @Test
    @DisplayName("봉인 후 원본 — seal 이 1건 기록하고 즉시 검증은 ORIGINAL")
    void afterSeal_original() throws IOException {
        Files.writeString(ipxeFile(), "efi-bytes-v1");

        SealResult result = area.seal();

        assertThat(result.sealed()).isEqualTo(1);
        assertThat(result.skipped()).isZero();
        assertThat(area.inspect(ipxeSlot()).condition()).isEqualTo(SealedFileCondition.ORIGINAL);
    }

    @Test
    @DisplayName("봉인 후 변조 — 봉인 뒤 바이트가 바뀌면 TAMPERED")
    void afterSeal_tampered() throws IOException {
        Files.writeString(ipxeFile(), "efi-bytes-v1");
        area.seal();
        Files.writeString(ipxeFile(), "TAMPER", StandardOpenOption.APPEND);

        assertThat(area.inspect(ipxeSlot()).condition()).isEqualTo(SealedFileCondition.TAMPERED);
    }
}
