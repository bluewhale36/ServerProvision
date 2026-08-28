package com.example.serverprovision.provisioning.service;

import com.example.serverprovision.provisioning.config.BiosResourceProperties;
import com.example.serverprovision.provisioning.domain.BiosAttribute;
import com.example.serverprovision.provisioning.domain.BiosSetupMenu;
import com.example.serverprovision.provisioning.domain.vo.BiosAttributeName;
import com.example.serverprovision.provisioning.domain.vo.BiosEnumOption;
import com.example.serverprovision.provisioning.exception.BiosBoardNotFoundException;
import com.example.serverprovision.provisioning.parser.BiosRegistryParser;
import com.example.serverprovision.provisioning.parser.BiosSetupDataParser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.io.DefaultResourceLoader;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * E3-3 D-1 · D-10 — 채집한 레지스트리 JSON 을 자료 파일의 XML 골격과 결합하면 파일 로드와 같은 모양의 메뉴가 나온다.
 * 캐시 키가 스냅샷 id 를 포함해 파일본과 채집본이 섞이지 않는다.
 */
class BiosSetupLoaderTest {

    private static final String XML = """
            <?xml version="1.0" encoding="UTF-8"?>
            <SetupData><Platform Guid="G" Handle="0x1"><Pages>
              <Page PageID="0x3" PageParentID="0x0" PageTitle="Advanced" PageFlags="0x0">
                <Control AttributeName="Whitley0000" />
              </Page>
            </Pages></Platform></SetupData>
            """;
    private static final String FILE_REGISTRY = registry("Disabled", "Enabled");
    private static final String F44_REGISTRY = registry("Disable", "Enable");

    private static String registry(String... options) {
        String values = String.join(",", java.util.Arrays.stream(options)
                .map(o -> "{\"ValueName\":\"" + o + "\",\"ValueDisplayName\":\"" + o + "\"}").toList());
        return "{\"RegistryEntries\":{\"Attributes\":[{\"AttributeName\":\"Whitley0000\",\"Type\":\"Enumeration\","
                + "\"DisplayName\":\"SpeedStep\",\"ReadOnly\":false,\"ResetRequired\":false,\"DefaultValue\":\"" + options[0] + "\",\"Value\":[" + values + "]}]}}";
    }

    @TempDir Path dir;
    private BiosSetupLoader loader;

    @BeforeEach
    void setUp() throws Exception {
        Files.writeString(dir.resolve("SetupData_MD72-HB3.xml"), XML);
        Files.writeString(dir.resolve("BiosAttributeRegistry_MD72-HB3.json"), FILE_REGISTRY);
        BiosResourceProperties properties = new BiosResourceProperties(dir.toString(), List.of(
                new BiosResourceProperties.Board("MD72-HB3", "BiosAttributeRegistry_MD72-HB3.json", "SetupData_MD72-HB3.xml"),
                new BiosResourceProperties.Board("NO-FILE", "missing.json", "SetupData_MD72-HB3.xml")));
        loader = new BiosSetupLoader(properties, new DefaultResourceLoader(), new BiosRegistryParser(), new BiosSetupDataParser());
    }

    private static List<String> optionsOf(BiosSetupMenu menu) {
        BiosAttribute attr = menu.registry().get(BiosAttributeName.of("Whitley0000"));
        return attr.options().stream().map(BiosEnumOption::valueName).toList();
    }

    @Test
    @DisplayName("채집본 로드 — 레지스트리는 스냅샷 것, 메뉴 골격은 파일 XML 것, 파일 로드와 같은 페이지 구조")
    void snapshotLoad_combinesXmlSkeleton() {
        BiosSetupMenu fromFile = loader.load("MD72-HB3");
        BiosSetupMenu fromSnapshot = loader.load("MD72-HB3", 9L, F44_REGISTRY);

        assertThat(optionsOf(fromFile)).containsExactly("Disabled", "Enabled");
        assertThat(optionsOf(fromSnapshot)).containsExactly("Disable", "Enable");
        assertThat(fromSnapshot.pages().keySet()).containsExactlyElementsOf(fromFile.pages().keySet());
        assertThat(fromSnapshot.menuBar()).hasSize(1);
        // 캐시 — 같은 키는 같은 인스턴스, 다른 스냅샷 id 는 다시 파싱
        assertThat(loader.load("MD72-HB3", 9L, F44_REGISTRY)).isSameAs(fromSnapshot);
        assertThat(loader.load("MD72-HB3", 10L, F44_REGISTRY)).isNotSameAs(fromSnapshot);
        assertThat(loader.load("MD72-HB3")).isSameAs(fromFile);
    }

    @Test
    @DisplayName("자료 항목이 없는 보드는 채집본이 있어도 404 — 메뉴 골격(XML)이 없다")
    void unknownBoard_throws() {
        assertThatThrownBy(() -> loader.load("UNKNOWN", 9L, F44_REGISTRY)).isInstanceOf(BiosBoardNotFoundException.class);
    }

    @Test
    @DisplayName("registryFileExists — 항목의 레지스트리 파일 실존 여부(Q3 의 편집 가능 판정 재료)")
    void registryFileExists() {
        assertThat(loader.registryFileExists("MD72-HB3")).isTrue();
        assertThat(loader.registryFileExists("NO-FILE")).isFalse();
        assertThat(loader.registryFileExists("UNKNOWN")).isFalse();
    }
}
