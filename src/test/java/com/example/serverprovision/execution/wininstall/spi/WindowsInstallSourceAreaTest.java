package com.example.serverprovision.execution.wininstall.spi;

import com.example.serverprovision.execution.asset.exception.SystemAssetSealNotSupportedException;
import com.example.serverprovision.execution.asset.spi.AreaAvailability;
import com.example.serverprovision.execution.asset.spi.AssetContextItem;
import com.example.serverprovision.execution.asset.spi.AssetSlotStatus;
import com.example.serverprovision.execution.asset.spi.SystemAssetAreaKey;
import com.example.serverprovision.execution.asset.spi.SystemAssetSlot;
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

/** E4-1-a-2 CP4 — 대시보드 "Windows 설치 소스" 영역: 미설정 흡수 · 슬롯 3 판정 · 관측 chip(비밀값 미노출) · 봉인 미지원. */
class WindowsInstallSourceAreaTest {

    private static final String SHARE_PASSWORD = "share-secret-9x";
    private static final String STANDARD_KEY = "KEY11-STAND-ARD00-XXXXX-YYYYY";

    @TempDir
    Path root;

    private static WindowsInstallSourceArea area(WindowsInstallProperties props) {
        return new WindowsInstallSourceArea(props, new WindowsImageCatalog(props));
    }

    private WindowsInstallProperties configured(String datacenterKey) {
        return new WindowsInstallProperties(root.toString(), "\\\\10.0.0.5\\win2025", "deploy", SHARE_PASSWORD, null,
                new WindowsInstallProperties.ProductKeys(STANDARD_KEY, datacenterKey));
    }

    private static SystemAssetSlot slot(WindowsInstallSourceArea area, InstallSourceSlot wanted) {
        return area.slots().stream().filter(s -> s.slotKey().equals(wanted.name())).findFirst().orElseThrow();
    }

    @Test
    @DisplayName("미설정 — NOT_CONFIGURED · 슬롯 3 은 '서빙 비활성' 판정 · chip 없음 · 봉인 미지원(예외)")
    void notConfigured() {
        WindowsInstallSourceArea area = area(new WindowsInstallProperties("", null, null, null, null, null));

        assertThat(area.areaKey()).isEqualTo(SystemAssetAreaKey.WINDOWS_INSTALL);
        assertThat(area.availability()).isEqualTo(AreaAvailability.NOT_CONFIGURED);
        assertThat(area.slots()).hasSize(3);
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
    @DisplayName("구성 + 소스 존재 — 슬롯 3 PRESENT · chip: 이미지 4종 · UNC 값 · 계정 설정됨 · 시간대 · 키(Standard 설정됨 · Datacenter 미설정) · 비밀값 미노출")
    void configuredWithSource() throws IOException {
        FakeWim.writeSource(root, FakeWim.fixtureXml());
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
                "설치 이미지", "공유 UNC", "공유 계정", "시간대", "제품 키 · ServerStandard", "제품 키 · ServerDatacenter");
        assertThat(chips.get(0).value()).isEqualTo("4종 · 빌드 10.0.26100.1742 · ko-KR");
        assertThat(chips.get(1).value()).isEqualTo("\\\\10.0.0.5\\win2025");
        assertThat(chips.get(2).value()).isEqualTo("설정됨");
        assertThat(chips.get(3).value()).isEqualTo("Korea Standard Time");
        assertThat(chips.get(4).value()).isEqualTo("설정됨");
        assertThat(chips.get(5).value()).isEqualTo("미설정");
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
        // 소스에 에디션이 없으므로 제품 키 chip 도 없다.
        assertThat(area.context()).extracting(AssetContextItem::label).noneMatch(l -> l.startsWith("제품 키"));
    }
}
