package com.example.serverprovision.provisioning.biossetting.service;

import com.example.serverprovision.provisioning.biossetting.dto.response.BiosSettingTemplateDetailResponse;
import com.example.serverprovision.provisioning.biossetting.dto.response.BiosSettingTemplateEditViewResponse;
import com.example.serverprovision.provisioning.biossetting.entity.BiosSettingTemplate;
import com.example.serverprovision.provisioning.biossetting.exception.BiosSettingTemplateNotFoundException;
import com.example.serverprovision.provisioning.biossetting.repository.BiosSettingTemplateRepository;
import com.example.serverprovision.management.board.entity.BoardModel;
import com.example.serverprovision.management.board.repository.BoardModelRepository;
import com.example.serverprovision.provisioning.biossetting.dto.response.BiosSettingBoardCardResponse;
import com.example.serverprovision.provisioning.biossetting.vo.BiosSettingValues;
import com.example.serverprovision.provisioning.config.BiosResourceProperties;
import com.example.serverprovision.provisioning.domain.BiosAttribute;
import com.example.serverprovision.provisioning.domain.BiosAttributeControl;
import com.example.serverprovision.provisioning.domain.BiosPage;
import com.example.serverprovision.provisioning.domain.BiosSetupMenu;
import com.example.serverprovision.provisioning.domain.enums.BiosAttributeType;
import com.example.serverprovision.provisioning.domain.enums.BiosComplexHint;
import com.example.serverprovision.provisioning.domain.vo.BiosAttributeName;
import com.example.serverprovision.provisioning.domain.vo.BiosAttributeValue;
import com.example.serverprovision.provisioning.domain.vo.BiosEnumOption;
import com.example.serverprovision.provisioning.domain.vo.IntegerBounds;
import com.example.serverprovision.provisioning.domain.vo.PageId;
import com.example.serverprovision.provisioning.dto.response.BiosSetupPageResponse;
import com.example.serverprovision.provisioning.dto.response.BiosWidgetRowResponse;
import com.example.serverprovision.provisioning.exception.BiosBoardNotFoundException;
import com.example.serverprovision.provisioning.service.BiosRedfishPayloadAssembler;
import com.example.serverprovision.provisioning.service.BiosSetupLoader;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.json.JsonMapper;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

/**
 * U2-2-2 CP4 — 상세(재조인 그룹·stale·Redfish 프리뷰·degraded) + 수정 편집기 overlay 단위 테스트.
 * 재조인·조립 로직이 실제로 실행되도록 registry/조립기는 실 인스턴스, repository/loader 만 mock.
 */
@ExtendWith(MockitoExtension.class)
class BiosSettingTemplateQueryServiceTest {

    @Mock BiosSettingTemplateRepository repository;
    @Mock com.example.serverprovision.provisioning.biossetting.BiosSettingTemplateUsageChecker usageChecker;
    @Mock BoardModelRepository boardModelRepository;
    @Mock BiosSetupLoader loader;

    private BiosSettingTemplateQueryService service;

    private static final String BOARD = "MS73-HB1";

    // 카탈로그 존재 판정용 실 properties — MS73-HB1 만 등록(카탈로그 보유), 그 외 미보유.
    private final BiosResourceProperties properties = new BiosResourceProperties("redfish_materials",
            List.of(new BiosResourceProperties.Board(BOARD, "r.json", "s.xml")));

    @BeforeEach
    void setUp() {
        service = new BiosSettingTemplateQueryService(
                repository, usageChecker, boardModelRepository, properties, loader,
                new BiosRedfishPayloadAssembler(), JsonMapper.builder().build());
    }

    // FK 전환 — BoardModel 은 mock(getId/getModelName/getVendor 만 소비).
    private static BoardModel boardMock(Long id, String modelName) {
        BoardModel board = org.mockito.Mockito.mock(BoardModel.class);
        org.mockito.Mockito.lenient().when(board.getId()).thenReturn(id);
        org.mockito.Mockito.lenient().when(board.getModelName()).thenReturn(modelName);
        return board;
    }

    private static BiosAttribute enumAttr(String name, String menuPath, boolean resetRequired) {
        return new BiosAttribute(BiosAttributeName.of(name), BiosAttributeType.ENUMERATION,
                "  TPM State", null, menuPath, false, resetRequired, "Disable",
                List.of(new BiosEnumOption("Disable", "비활성"), new BiosEnumOption("Enable", "활성")),
                null, null);
    }

    private static BiosAttribute intAttr(String name, String menuPath) {
        return new BiosAttribute(BiosAttributeName.of(name), BiosAttributeType.INTEGER,
                "Link Timeout", null, menuPath, false, false, "1000",
                List.of(), new IntegerBounds(10, 10000, 10), null);
    }

    private static BiosSetupMenu menu(BiosAttribute... attrs) {
        Map<BiosAttributeName, BiosAttribute> registry = new LinkedHashMap<>();
        for (BiosAttribute a : attrs) registry.put(a.name(), a);
        // 편집기 overlay 테스트용 페이지 1장 — leaf 컨트롤로 registry 속성 전부 배치.
        List<com.example.serverprovision.provisioning.domain.BiosControl> controls = new java.util.ArrayList<>();
        for (BiosAttribute a : attrs) controls.add(new BiosAttributeControl(a.name(), BiosComplexHint.NONE));
        BiosPage page = new BiosPage(PageId.of("0x1"), PageId.ROOT, "Advanced", "0x0", List.copyOf(controls));
        return new BiosSetupMenu(BOARD, List.of(PageId.of("0x1")), Map.of(PageId.of("0x1"), page), registry, List.of());
    }

    private static BiosSettingTemplate template(Map<BiosAttributeName, BiosAttributeValue> values) {
        return BiosSettingTemplate.builder()
                .name("Rocky9 표준").description("설명").boardModel(boardMock(6L, BOARD))
                .values(new BiosSettingValues(values)).build();
    }

    @Test
    @DisplayName("findBoards — BoardModel 실데이터 + 카탈로그 보유 판정(catalogAvailable)")
    void findBoards_flagsCatalogAvailability() {
        BoardModel withCatalog = boardMock(6L, BOARD);
        BoardModel withoutCatalog = boardMock(3L, "RX1330M6");
        org.mockito.Mockito.lenient().when(withCatalog.getVendor()).thenReturn(com.example.serverprovision.management.board.enums.Vendor.GIGABYTE);
        org.mockito.Mockito.lenient().when(withoutCatalog.getVendor()).thenReturn(com.example.serverprovision.management.board.enums.Vendor.FUJITSU);
        given(boardModelRepository.findAllByIsDeletedFalseOrderByVendorAscCreatedAtDesc())
                .willReturn(List.of(withCatalog, withoutCatalog));

        List<BiosSettingBoardCardResponse> boards = service.findBoards();

        assertThat(boards).hasSize(2);
        assertThat(boards.get(0).catalogAvailable()).isTrue();   // MS73-HB1 — properties 등록
        assertThat(boards.get(1).catalogAvailable()).isFalse();  // RX1330M6 — 카탈로그 미보유 → UI disabled
    }

    @Test
    @DisplayName("findDetail — MenuPath 그룹 + DisplayName 라벨 + enum 표시명 + default→stored + resetRequired OR")
    void findDetail_joinsAndGroups() {
        Map<BiosAttributeName, BiosAttributeValue> values = new LinkedHashMap<>();
        values.put(BiosAttributeName.of("TCG003"), BiosAttributeValue.ofString("Enable"));
        values.put(BiosAttributeName.of("IIO0012"), BiosAttributeValue.ofLong(500L));
        BiosSettingTemplate fixture = template(values);
        given(repository.findById(1L)).willReturn(Optional.of(fixture));
        given(loader.load(BOARD)).willReturn(menu(
                enumAttr("TCG003", "./Advanced/Trusted Computing", true),
                intAttr("IIO0012", "./Chipset/IIO")));

        BiosSettingTemplateDetailResponse detail = service.findDetail(1L);

        assertThat(detail.catalogMissing()).isFalse();
        assertThat(detail.groups()).hasSize(2);
        BiosSettingTemplateDetailResponse.Group tpm = detail.groups().get(0);
        assertThat(tpm.menuPath()).isEqualTo("./Advanced/Trusted Computing");
        BiosSettingTemplateDetailResponse.Entry entry = tpm.entries().get(0);
        // 라벨 = trimmed DisplayName, 값 표시 = valueDisplayName(원시값은 raw 로 보존).
        assertThat(entry.displayName()).isEqualTo("TPM State");
        assertThat(entry.defaultDisplay()).isEqualTo("비활성");
        assertThat(entry.storedDisplay()).isEqualTo("활성");
        assertThat(entry.storedRaw()).isEqualTo("Enable");
        assertThat(entry.resetRequired()).isTrue();
        // resetRequired OR — TPM 이 true 이므로 전체 true.
        assertThat(detail.redfish().resetRequired()).isTrue();
    }

    @Test
    @DisplayName("findDetail — Redfish body 는 저장 flat 의 무변환 투영(타입 보존 + Attributes 래핑)")
    void findDetail_redfishBodyMirrorsStoredValues() {
        Map<BiosAttributeName, BiosAttributeValue> values = new LinkedHashMap<>();
        values.put(BiosAttributeName.of("IIO0012"), BiosAttributeValue.ofLong(500L));
        BiosSettingTemplate fixture = template(values);
        given(repository.findById(1L)).willReturn(Optional.of(fixture));
        given(loader.load(BOARD)).willReturn(menu(intAttr("IIO0012", "./Chipset/IIO")));

        BiosSettingTemplateDetailResponse.RedfishPreview redfish = service.findDetail(1L).redfish();

        assertThat(redfish.method()).isEqualTo("PATCH");
        assertThat(redfish.target()).isEqualTo("/redfish/v1/Systems/Self/Bios/SD");
        // 타입 보존: 정수는 따옴표 없는 숫자.
        assertThat(redfish.bodyJson()).contains("\"Attributes\"").contains("\"IIO0012\" : 500");
        assertThat(redfish.resetRequired()).isFalse();
    }

    @Test
    @DisplayName("findDetail — registry 에 없어진 저장 속성은 stale 로 분리(경고), 나머지는 정상 그룹")
    void findDetail_staleSeparated() {
        Map<BiosAttributeName, BiosAttributeValue> values = new LinkedHashMap<>();
        values.put(BiosAttributeName.of("TCG003"), BiosAttributeValue.ofString("Enable"));
        values.put(BiosAttributeName.of("OLD0001_Removed"), BiosAttributeValue.ofString("Legacy"));
        BiosSettingTemplate fixture = template(values);
        given(repository.findById(1L)).willReturn(Optional.of(fixture));
        given(loader.load(BOARD)).willReturn(menu(enumAttr("TCG003", "./Advanced/TC", false)));

        BiosSettingTemplateDetailResponse detail = service.findDetail(1L);

        assertThat(detail.groups()).hasSize(1);
        assertThat(detail.stale()).singleElement()
                .satisfies(s -> {
                    assertThat(s.attributeName()).isEqualTo("OLD0001_Removed");
                    assertThat(s.storedRaw()).isEqualTo("Legacy");
                });
        // stale 도 Redfish body 에는 포함(silent drop 금지 — 실행 시 전송될 저장분 그대로).
        assertThat(detail.redfish().bodyJson()).contains("OLD0001_Removed");
    }

    @Test
    @DisplayName("findDetail — 보드가 카탈로그 목록에서 사라짐 → catalogMissing + 전건 stale(raw) degraded")
    void findDetail_catalogMissing_degraded() {
        Map<BiosAttributeName, BiosAttributeValue> values = new LinkedHashMap<>();
        values.put(BiosAttributeName.of("TCG003"), BiosAttributeValue.ofString("Enable"));
        BiosSettingTemplate fixture = template(values);
        given(repository.findById(1L)).willReturn(Optional.of(fixture));
        given(loader.load(BOARD)).willThrow(new BiosBoardNotFoundException(BOARD));

        BiosSettingTemplateDetailResponse detail = service.findDetail(1L);

        assertThat(detail.catalogMissing()).isTrue();
        assertThat(detail.groups()).isEmpty();
        assertThat(detail.stale()).hasSize(1);
        // degraded 여도 Redfish body 는 저장 flat 만으로 조립 가능.
        assertThat(detail.redfish().bodyJson()).contains("TCG003");
    }

    @Test
    @DisplayName("editorViewFor — storedValue 는 위젯 선택값으로만 주입되고 diff 기준선(defaultValue)은 불변")
    void editorViewFor_overlaysStoredValueOnly() {
        Map<BiosAttributeName, BiosAttributeValue> values = new LinkedHashMap<>();
        values.put(BiosAttributeName.of("TCG003"), BiosAttributeValue.ofString("Enable"));
        BiosSettingTemplate saved = template(values);
        given(repository.findById(1L)).willReturn(Optional.of(saved));
        given(loader.load(BOARD)).willReturn(menu(
                enumAttr("TCG003", "./Advanced/TC", false),
                intAttr("IIO0012", "./Chipset/IIO")));

        BiosSettingTemplateEditViewResponse editView = service.editorViewFor(1L);
        BiosSetupPageResponse bios = editView.bios();

        BiosWidgetRowResponse tpm = (BiosWidgetRowResponse) bios.pages().get(0).rows().get(0);
        BiosWidgetRowResponse timeout = (BiosWidgetRowResponse) bios.pages().get(0).rows().get(1);
        // 저장된 속성만 storedValue 보유, 기준선은 registry 기본값 그대로.
        assertThat(tpm.storedValue()).isEqualTo("Enable");
        assertThat(tpm.defaultValue()).isEqualTo("Disable");
        assertThat(timeout.storedValue()).isNull();
        assertThat(timeout.defaultValue()).isEqualTo("1000");
        assertThat(editView.name()).isEqualTo("Rocky9 표준");
    }

    @Test
    @DisplayName("findDetail — 없는 id → BiosSettingTemplateNotFoundException")
    void findDetail_notFound() {
        given(repository.findById(99L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> service.findDetail(99L))
                .isInstanceOf(BiosSettingTemplateNotFoundException.class);
    }
}
