package com.example.serverprovision.execution.asset.service;

import com.example.serverprovision.execution.asset.dto.response.AssetVersionView;
import com.example.serverprovision.execution.asset.enums.DiagnosticAsset;
import com.example.serverprovision.global.history.AssetHistoryService;
import com.example.serverprovision.global.history.AssetVersionKey;
import com.example.serverprovision.global.history.entity.AssetVersion;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

/**
 * E1-I-2-b-2 CP4 — 상세 화면 버전 목록 조회 검증. 한 슬롯 이력만 조회하고 행이 뷰로 미리 계산되는지 확인.
 */
class DiagnosticAssetVersionServiceTest {

    private final AssetHistoryService historyService = mock(AssetHistoryService.class);
    private final DiagnosticAssetVersionService service = new DiagnosticAssetVersionService(historyService);

    @Test
    @DisplayName("listVersions — 한 슬롯 이력을 뷰로 매핑(id·짧은해시·크기)")
    void listVersions_mapsRows() {
        AssetVersion version = AssetVersion.of(
                new AssetVersionKey("DIAGNOSTIC", "vmlinuz-lts"),
                "DIAGNOSTIC/vmlinuz-lts/260724-010203-123_abcd1234_vmlinuz-lts",
                "abcdef0123456789abcdef0123456789abcdef0123456789abcdef0123456789",
                208_000_000L, Instant.now());
        ReflectionTestUtils.setField(version, "id", 3L);
        given(historyService.list(new AssetVersionKey("DIAGNOSTIC", "vmlinuz-lts")))
                .willReturn(List.of(version));

        List<AssetVersionView> views = service.listVersions(DiagnosticAsset.VMLINUZ);

        assertThat(views).hasSize(1);
        AssetVersionView view = views.get(0);
        assertThat(view.versionId()).isEqualTo(3L);
        assertThat(view.shortSha()).isEqualTo("abcdef012345");   // 앞 12자
        assertThat(view.sizeDisplay()).contains("MB");
    }

    @Test
    @DisplayName("listVersions — 이력 없는 슬롯은 빈 목록")
    void listVersions_empty() {
        given(historyService.list(new AssetVersionKey("DIAGNOSTIC", "diag.apkovl.tar.gz")))
                .willReturn(List.of());

        assertThat(service.listVersions(DiagnosticAsset.APKOVL)).isEmpty();
    }
}
