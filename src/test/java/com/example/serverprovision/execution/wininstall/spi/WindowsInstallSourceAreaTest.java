package com.example.serverprovision.execution.wininstall.spi;

import com.example.serverprovision.execution.asset.exception.SystemAssetSealNotSupportedException;
import com.example.serverprovision.execution.asset.spi.AreaAvailability;
import com.example.serverprovision.execution.asset.spi.AssetContextItem;
import com.example.serverprovision.execution.asset.spi.AssetSlotStatus;
import com.example.serverprovision.execution.asset.spi.SystemAssetAreaKey;
import com.example.serverprovision.execution.asset.spi.SystemAssetSlot;
import com.example.serverprovision.execution.wininstall.WindowsInstallSource;
import com.example.serverprovision.execution.wininstall.catalog.FakeWim;
import com.example.serverprovision.execution.wininstall.catalog.InstallSourceCondition;
import com.example.serverprovision.execution.wininstall.catalog.WindowsImageCatalog;
import com.example.serverprovision.execution.wininstall.config.WindowsInstallProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** E4-1-a-2 CP4 → E4-1-a-3 — 대시보드 "Windows 설치 소스" 영역: 미설정 흡수 · 슬롯 4 판정(wimboot 포함) · 관측 chip(해시 앞자리 · 비밀값 미노출) · 봉인 미지원. */
class WindowsInstallSourceAreaTest {

    private static final String SHARE_PASSWORD = "share-secret-9x";
    private static final String STANDARD_KEY = "KEY11-STAND-ARD00-XXXXX-YYYYY";

    @TempDir
    Path root;

    /** E4-1-a-4 — 드라이버 자원 저장소는 mock(기본 빈 목록) · 조립기는 실물(매니페스트 파일로 chip 상태를 낸다). */
    private final com.example.serverprovision.management.subprogram.repository.SubprogramRepository subprogramRepository =
            org.mockito.Mockito.mock(com.example.serverprovision.management.subprogram.repository.SubprogramRepository.class);

    private com.example.serverprovision.execution.engine.windows.WindowsOemPayloadAssembler assembler(WindowsInstallProperties props) {
        return new com.example.serverprovision.execution.engine.windows.WindowsOemPayloadAssembler(props, subprogramRepository,
                new tools.jackson.databind.ObjectMapper());
    }

    private WindowsInstallSourceArea area(WindowsInstallProperties props) {
        return new WindowsInstallSourceArea(props, new WindowsImageCatalog(props), new WindowsInstallSource(props), assembler(props));
    }

    private WindowsInstallProperties configured(String datacenterKey) {
        return new WindowsInstallProperties(root.toString(), "\\\\10.0.0.5\\win2025", "deploy", SHARE_PASSWORD, null,
                new WindowsInstallProperties.ProductKeys(STANDARD_KEY, datacenterKey));
    }

    private static SystemAssetSlot slot(WindowsInstallSourceArea area, InstallSourceSlot wanted) {
        return area.slots().stream().filter(s -> s.slotKey().equals(wanted.name())).findFirst().orElseThrow();
    }

    @Test
    @DisplayName("미설정 — NOT_CONFIGURED · 슬롯 6 은 '서빙 비활성' 판정 · chip 없음 · 봉인 미지원(예외)")
    void notConfigured() {
        WindowsInstallSourceArea area = area(new WindowsInstallProperties("", null, null, null, null, null));

        assertThat(area.areaKey()).isEqualTo(SystemAssetAreaKey.WINDOWS_INSTALL);
        assertThat(area.availability()).isEqualTo(AreaAvailability.NOT_CONFIGURED);
        assertThat(area.slots()).hasSize(6);   // E4-1-a-4 — 설치 후 스크립트 슬롯 2 추가
        for (SystemAssetSlot slot : area.slots()) {
            AssetSlotStatus status = area.inspect(slot);
            assertThat(status.present()).isFalse();
            assertThat(status.condition()).isEqualTo(InstallSourceCondition.NOT_CONFIGURED);
            assertThat(slot.replaceable()).isFalse();
        }
        assertThat(area.context()).isEmpty();
        assertThat(area.supportsSeal()).isFalse();
        assertThatThrownBy(area::seal).isInstanceOf(SystemAssetSealNotSupportedException.class);
    }

    @Test
    @DisplayName("구성 + 소스 존재 — 슬롯 6 PRESENT · chip: 이미지 4종 · wimboot 해시 12자 · UNC 값 · 계정 설정됨 · 시간대 · 키(Standard 설정됨 · Datacenter 미설정) · 비밀값 미노출")
    void configuredWithSource() throws IOException {
        FakeWim.writeSource(root, FakeWim.fixtureXml());
        Files.writeString(root.resolve("wimboot"), "FAKE-WIMBOOT");   // E4-1-a-3 — 소스 루트의 부트로더(슬롯 4)
        FakeWim.writeOemScripts(root);                                 // E4-1-a-4 — 설치 후 스크립트 슬롯 2(조립 액션 산출물)
        WindowsInstallSourceArea area = area(configured(null));

        assertThat(area.availability()).isEqualTo(AreaAvailability.CONFIGURED);
        for (SystemAssetSlot slot : area.slots()) {
            AssetSlotStatus status = area.inspect(slot);
            assertThat(status.present()).isTrue();
            assertThat(status.condition()).isEqualTo(InstallSourceCondition.PRESENT);
            assertThat(status.condition().healthy()).isTrue();
        }
        List<AssetContextItem> chips = area.context();
        assertThat(chips).extracting(AssetContextItem::label).containsExactly(
                "설치 이미지", "wimboot SHA-256", "공유 UNC", "공유 계정", "시간대", "드라이버 페이로드",
                "제품 키 · ServerStandard", "제품 키 · ServerDatacenter");
        assertThat(chips.get(0).value()).isEqualTo("4종 · 빌드 10.0.26100.1742 · ko-KR");
        assertThat(chips.get(1).value()).hasSize(12).matches("[0-9a-f]{12}");   // 런북 §14-4 해시와 눈으로 대조하는 앞자리
        assertThat(chips.get(2).value()).isEqualTo("\\\\10.0.0.5\\win2025");
        assertThat(chips.get(3).value()).isEqualTo("설정됨");
        assertThat(chips.get(4).value()).isEqualTo("Korea Standard Time");
        assertThat(chips.get(5).value()).isEqualTo("미조립");   // E4-1-a-4 — 매니페스트 없음(스크립트 파일만 있어도 미조립)
        assertThat(chips.get(6).value()).isEqualTo("설정됨");
        assertThat(chips.get(7).value()).isEqualTo("미설정");
        // 비밀값은 어떤 chip 에도 실리지 않는다.
        assertThat(chips).extracting(AssetContextItem::value).noneMatch(v -> v.contains(SHARE_PASSWORD) || v.contains(STANDARD_KEY));
    }

    @Test
    @DisplayName("구성 + 파일 부재 · 해석 실패 — boot.wim 없음 → 파일 없음, 깨진 install.wim → 읽기 실패 + chip '설치 이미지 없음'")
    void configuredWithBrokenSource() throws IOException {
        FakeWim.writeSource(root, FakeWim.fixtureXml());
        Files.delete(root.resolve("sources/boot.wim"));
        Files.writeString(root.resolve("sources/install.wim"), "broken ".repeat(64));
        WindowsInstallSourceArea area = area(configured(null));

        assertThat(area.inspect(slot(area, InstallSourceSlot.BOOT_WIM)).condition()).isEqualTo(InstallSourceCondition.MISSING);
        AssetSlotStatus wim = area.inspect(slot(area, InstallSourceSlot.INSTALL_WIM));
        assertThat(wim.present()).isTrue();
        assertThat(wim.condition()).isEqualTo(InstallSourceCondition.UNREADABLE);
        assertThat(area.inspect(slot(area, InstallSourceSlot.SETUP_EXE)).condition()).isEqualTo(InstallSourceCondition.PRESENT);
        assertThat(area.context().get(0).value()).isEqualTo("없음");
        assertThat(area.context().get(1)).satisfies(chip -> {   // wimboot 부재 — 해시 대신 '없음' 경고
            assertThat(chip.label()).isEqualTo("wimboot");
            assertThat(chip.value()).isEqualTo("없음");
        });
        // 소스에 에디션이 없으므로 제품 키 chip 도 없다.
        assertThat(area.context()).extracting(AssetContextItem::label).noneMatch(l -> l.startsWith("제품 키"));
    }

    // ── E4-1-a-4 — chip "드라이버 페이로드" 3상태 · 조립 버튼 차단 사유 ──────────────────────

    @Test
    @DisplayName("chip — 조립 뒤 '최신 · 0종'(OK) · 매니페스트의 스크립트 해시가 어긋나면 '갱신 필요 — 설치 후 스크립트 변경'(WARN) · 슬롯 2 PRESENT")
    void oemPayloadChip_currentThenStale() throws IOException {
        FakeWim.writeSource(root, FakeWim.fixtureXml());
        Files.writeString(root.resolve("wimboot"), "FAKE-WIMBOOT");
        WindowsInstallProperties props = configured(null);
        assembler(props).sync();   // 자원 0 → 스크립트 2 + 매니페스트
        WindowsInstallSourceArea area = area(props);

        AssetContextItem chip = area.context().stream().filter(c -> c.label().equals("드라이버 페이로드")).findFirst().orElseThrow();
        assertThat(chip.value()).isEqualTo("최신 · 0종");
        assertThat(chip.severity()).isEqualTo(com.example.serverprovision.execution.asset.spi.ObservationSeverity.OK);
        assertThat(area.inspect(slot(area, InstallSourceSlot.OEM_SETUPCOMPLETE)).present()).isTrue();
        assertThat(area.inspect(slot(area, InstallSourceSlot.OEM_REPORT)).present()).isTrue();

        Path manifest = root.resolve("sources").resolve("$OEM$").resolve("spv-oem-manifest.json");
        Files.writeString(manifest, Files.readString(manifest).replaceFirst("\"scriptsHash\":\"[0-9a-f]+\"", "\"scriptsHash\":\"old\""));
        AssetContextItem stale = area.context().stream().filter(c -> c.label().equals("드라이버 페이로드")).findFirst().orElseThrow();
        assertThat(stale.value()).isEqualTo("갱신 필요 — 설치 후 스크립트 변경");
        assertThat(stale.severity()).isEqualTo(com.example.serverprovision.execution.asset.spi.ObservationSeverity.WARN);
    }

    @Test
    @DisplayName("actionBlockReason — 조립기의 blockReason 그대로(미설정 → 사유 · 구성 + sources 있음 → empty) — 버튼 disabled 와 409 가 같은 판정")
    void actionBlockReason_delegates() throws IOException {
        assertThat(area(new WindowsInstallProperties("", null, null, null, null, null)).actionBlockReason())
                .hasValueSatisfying(r -> assertThat(r).contains("설치 소스 미설정"));
        FakeWim.writeSource(root, FakeWim.fixtureXml());
        assertThat(area(configured(null)).actionBlockReason()).isEmpty();
    }
}
