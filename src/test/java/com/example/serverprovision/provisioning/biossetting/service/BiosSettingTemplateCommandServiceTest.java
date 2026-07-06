package com.example.serverprovision.provisioning.biossetting.service;

import com.example.serverprovision.provisioning.biossetting.dto.request.BiosSettingTemplateCreateRequest;
import com.example.serverprovision.provisioning.biossetting.dto.request.BiosSettingTemplateUpdateRequest;
import com.example.serverprovision.provisioning.biossetting.exception.BiosSettingTemplateNotFoundException;
import com.example.serverprovision.provisioning.biossetting.dto.response.BiosSettingTemplateSummaryResponse;
import com.example.serverprovision.provisioning.biossetting.entity.BiosSettingTemplate;
import com.example.serverprovision.provisioning.biossetting.exception.DuplicateBiosSettingTemplateNameException;
import com.example.serverprovision.provisioning.biossetting.repository.BiosSettingTemplateRepository;
import com.example.serverprovision.management.board.entity.BoardModel;
import com.example.serverprovision.management.board.exception.BoardModelNotFoundException;
import com.example.serverprovision.management.board.repository.BoardModelRepository;
import com.example.serverprovision.provisioning.biossetting.vo.BiosSettingValues;
import com.example.serverprovision.provisioning.domain.BiosAttribute;
import com.example.serverprovision.provisioning.domain.BiosSetupMenu;
import com.example.serverprovision.provisioning.domain.enums.BiosAttributeType;
import com.example.serverprovision.provisioning.domain.vo.BiosAttributeName;
import com.example.serverprovision.provisioning.domain.vo.BiosEnumOption;
import com.example.serverprovision.provisioning.domain.vo.IntegerBounds;
import com.example.serverprovision.provisioning.domain.vo.PasswordLength;
import com.example.serverprovision.provisioning.exception.InvalidBiosValueException;
import com.example.serverprovision.provisioning.exception.UnknownBiosAttributeException;
import com.example.serverprovision.provisioning.service.BiosSetupLoader;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

/**
 * U2-2-1 CP4 — 생성 검증·coerce 루프 단위 테스트 (구 PoC save 에서 이동한 로직의 회귀 방어).
 * 실제 {@link BiosAttributeType} 다형 검증이 실행되도록 registry 는 실 도메인 record 로 조립한다.
 */
@ExtendWith(MockitoExtension.class)
class BiosSettingTemplateCommandServiceTest {

    @Mock BiosSettingTemplateRepository repository;
    @Mock com.example.serverprovision.provisioning.biossetting.BiosSettingTemplateUsageChecker usageChecker;
    @Mock BoardModelRepository boardModelRepository;
    @Mock BiosSetupLoader loader;
    @InjectMocks BiosSettingTemplateCommandService service;

    private static final String BOARD = "MS74-HB0";
    private static final Long BOARD_ID = 2L;

    // FK 전환 — BoardModel 실엔티티는 생성 경로가 무거워 mock 으로 대체(getId/getModelName 만 소비).
    private BoardModel boardMock() {
        // 주의: given(...) 인자 내부에서 호출하면 중첩 스터빙 — 반드시 지역변수로 먼저 만든 뒤 스터빙에 넘긴다.
        BoardModel board = org.mockito.Mockito.mock(BoardModel.class);
        org.mockito.Mockito.lenient().when(board.getModelName()).thenReturn(BOARD);
        org.mockito.Mockito.lenient().when(board.getId()).thenReturn(BOARD_ID);
        return board;
    }

    private static BiosAttribute enumAttr(String name) {
        return new BiosAttribute(BiosAttributeName.of(name), BiosAttributeType.ENUMERATION,
                "Power Performance Tuning", null, null, false, false, "BIOS Controls EPB",
                List.of(new BiosEnumOption("BIOS Controls EPB", "BIOS"), new BiosEnumOption("OS Controls EPB", "OS")),
                null, null);
    }

    private static BiosAttribute intAttr(String name) {
        return new BiosAttribute(BiosAttributeName.of(name), BiosAttributeType.INTEGER,
                "Link Training Timeout", null, null, false, false, "1000",
                List.of(), new IntegerBounds(10, 10000, 10), null);
    }

    private static BiosAttribute passwordAttr(String name) {
        return new BiosAttribute(BiosAttributeName.of(name), BiosAttributeType.PASSWORD,
                "Administrator Password", null, null, false, false, null,
                List.of(), null, new PasswordLength(8, 20));
    }

    private static BiosAttribute readOnlyAttr(String name) {
        return new BiosAttribute(BiosAttributeName.of(name), BiosAttributeType.ENUMERATION,
                "Firmware Version", null, null, true, false, "v1",
                List.of(new BiosEnumOption("v1", "v1")), null, null);
    }

    private static BiosSetupMenu menu(BiosAttribute... attrs) {
        Map<BiosAttributeName, BiosAttribute> registry = new java.util.LinkedHashMap<>();
        for (BiosAttribute a : attrs) registry.put(a.name(), a);
        return new BiosSetupMenu(BOARD, List.of(), Map.of(), registry, List.of());
    }

    private static BiosSettingTemplateCreateRequest request(Map<String, String> attributes) {
        return new BiosSettingTemplateCreateRequest("Rocky9 표준", "설명", BOARD_ID, attributes);
    }

    @Test
    @DisplayName("happy — 검증·coerce 통과, 타입 보존(enum=String/int=Long)으로 저장 + readOnly 는 스킵")
    void create_validatesAndCoerces() {
        given(repository.existsByName("Rocky9 표준")).willReturn(false);
        BoardModel board = boardMock();
        given(boardModelRepository.findByIdAndIsDeletedFalse(BOARD_ID)).willReturn(java.util.Optional.of(board));
        given(loader.load(BOARD)).willReturn(menu(enumAttr("E1"), intAttr("I1"), readOnlyAttr("RO1")));
        given(repository.save(any())).willAnswer(inv -> inv.getArgument(0));

        BiosSettingTemplateSummaryResponse response = service.create(request(Map.of(
                "E1", "OS Controls EPB", "I1", "500", "RO1", "v1")));

        ArgumentCaptor<BiosSettingTemplate> captor = ArgumentCaptor.forClass(BiosSettingTemplate.class);
        verify(repository).save(captor.capture());
        BiosSettingValues values = captor.getValue().getValues();
        // readOnly 스킵 → 2건만. coerce 타입 보존: ENUM=String, INTEGER=Long.
        assertThat(values.size()).isEqualTo(2);
        assertThat(values.entries().get(BiosAttributeName.of("E1")).jsonValue()).isEqualTo("OS Controls EPB");
        assertThat(values.entries().get(BiosAttributeName.of("I1")).jsonValue()).isEqualTo(500L);
        assertThat(captor.getValue().getBoardModel().getModelName()).isEqualTo(BOARD);
        assertThat(response.attributeCount()).isEqualTo(2);
    }

    @Test
    @DisplayName("name 전역 중복 → DuplicateBiosSettingTemplateNameException(409)")
    void create_duplicateName_throws409() {
        given(repository.existsByName("Rocky9 표준")).willReturn(true);

        assertThatThrownBy(() -> service.create(request(Map.of("E1", "OS Controls EPB"))))
                .isInstanceOf(DuplicateBiosSettingTemplateNameException.class);
    }

    @Test
    @DisplayName("registry 에 없는 속성 → UnknownBiosAttributeException(404)")
    void create_unknownAttribute_throws() {
        given(repository.existsByName(any())).willReturn(false);
        BoardModel board = boardMock();
        given(boardModelRepository.findByIdAndIsDeletedFalse(BOARD_ID)).willReturn(java.util.Optional.of(board));
        given(loader.load(BOARD)).willReturn(menu(enumAttr("E1")));

        assertThatThrownBy(() -> service.create(request(Map.of("GHOST", "x"))))
                .isInstanceOf(UnknownBiosAttributeException.class);
    }

    @Test
    @DisplayName("타입 검증 위반(enum 비허용 값) → InvalidBiosValueException(400)")
    void create_invalidValue_throws() {
        given(repository.existsByName(any())).willReturn(false);
        BoardModel board = boardMock();
        given(boardModelRepository.findByIdAndIsDeletedFalse(BOARD_ID)).willReturn(java.util.Optional.of(board));
        given(loader.load(BOARD)).willReturn(menu(enumAttr("E1")));

        assertThatThrownBy(() -> service.create(request(Map.of("E1", "허용되지 않은 값"))))
                .isInstanceOf(InvalidBiosValueException.class);
    }

    @Test
    @DisplayName("PASSWORD 속성 포함 → templatable() SSOT 로 400 거절 (UI 는 위젯 미출력 — direct POST 안전망)")
    void create_passwordAttribute_rejected() {
        given(repository.existsByName(any())).willReturn(false);
        BoardModel board = boardMock();
        given(boardModelRepository.findByIdAndIsDeletedFalse(BOARD_ID)).willReturn(java.util.Optional.of(board));
        given(loader.load(BOARD)).willReturn(menu(passwordAttr("PW1")));

        assertThatThrownBy(() -> service.create(request(Map.of("PW1", "secret1234"))))
                .isInstanceOf(InvalidBiosValueException.class)
                .hasMessageContaining("템플릿에 담을 수 없는");
    }

    @Test
    @DisplayName("유효 변경분 0건(빈 diff / readOnly 만) → emptyDiff 400")
    void create_emptyDiff_rejected() {
        given(repository.existsByName(any())).willReturn(false);
        BoardModel board = boardMock();
        given(boardModelRepository.findByIdAndIsDeletedFalse(BOARD_ID)).willReturn(java.util.Optional.of(board));
        given(loader.load(BOARD)).willReturn(menu(readOnlyAttr("RO1")));

        // readOnly 만 담긴 요청 — 스킵 후 잔여 0건.
        assertThatThrownBy(() -> service.create(request(Map.of("RO1", "v1"))))
                .isInstanceOf(InvalidBiosValueException.class)
                .hasMessageContaining("변경된 속성이 없습니다");
    }

    @Test
    @DisplayName("create — 없는/삭제된 boardModelId → BoardModelNotFoundException(404, FK 전환)")
    void create_unknownBoardModel_throws404() {
        given(repository.existsByName(any())).willReturn(false);
        given(boardModelRepository.findByIdAndIsDeletedFalse(99L)).willReturn(java.util.Optional.empty());

        assertThatThrownBy(() -> service.create(new BiosSettingTemplateCreateRequest(
                "Rocky9 표준", null, 99L, Map.of("E1", "OS Controls EPB"))))
                .isInstanceOf(BoardModelNotFoundException.class);
    }

    /* ─────────────────────────── U2-2-2 — update / delete ─────────────────────────── */

    private BiosSettingTemplate savedTemplate() {
        return BiosSettingTemplate.builder()
                .name("Rocky9 표준").description("설명").boardModel(boardMock())
                .values(new BiosSettingValues(Map.of(
                        BiosAttributeName.of("E1"),
                        com.example.serverprovision.provisioning.domain.vo.BiosAttributeValue.ofString("BIOS Controls EPB"))))
                .build();
    }

    @Test
    @DisplayName("update happy — 재검증·coerce 후 값 전체 교체, boardKey 불변")
    void update_replacesValues() {
        BiosSettingTemplate template = savedTemplate();
        given(repository.findById(1L)).willReturn(java.util.Optional.of(template));
        given(repository.existsByNameAndIdNot("Rocky9 표준 v2", 1L)).willReturn(false);
        given(loader.load(BOARD)).willReturn(menu(enumAttr("E1"), intAttr("I1")));

        service.update(1L, new BiosSettingTemplateUpdateRequest(
                "Rocky9 표준 v2", "개정", Map.of("I1", "500")));

        assertThat(template.getName()).isEqualTo("Rocky9 표준 v2");
        // 전체 교체 의미론 — 기존 E1 은 사라지고 I1 만 남는다(편집기가 전체 diff 를 재수집해 보냄).
        assertThat(template.getValues().size()).isEqualTo(1);
        assertThat(template.getValues().entries().get(BiosAttributeName.of("I1")).jsonValue()).isEqualTo(500L);
        assertThat(template.getBoardModel().getModelName()).isEqualTo(BOARD);
    }

    @Test
    @DisplayName("update — 타 행과 name 중복(자기 제외) → 409")
    void update_duplicateNameOfOther_throws409() {
        BiosSettingTemplate existing = savedTemplate();
        given(repository.findById(1L)).willReturn(java.util.Optional.of(existing));
        given(repository.existsByNameAndIdNot("이미 있는 이름", 1L)).willReturn(true);

        assertThatThrownBy(() -> service.update(1L, new BiosSettingTemplateUpdateRequest(
                "이미 있는 이름", null, Map.of("E1", "OS Controls EPB"))))
                .isInstanceOf(DuplicateBiosSettingTemplateNameException.class);
    }

    @Test
    @DisplayName("update/delete — 없는 id → BiosSettingTemplateNotFoundException")
    void updateAndDelete_notFound() {
        given(repository.findById(99L)).willReturn(java.util.Optional.empty());

        assertThatThrownBy(() -> service.update(99L, new BiosSettingTemplateUpdateRequest(
                "x", null, Map.of("E1", "OS Controls EPB"))))
                .isInstanceOf(BiosSettingTemplateNotFoundException.class);
        assertThatThrownBy(() -> service.delete(99L))
                .isInstanceOf(BiosSettingTemplateNotFoundException.class);
    }

    @Test
    @DisplayName("delete — 정의서에서 사용 중 → BiosSettingTemplateInUseException 409 (U2-2-3)")
    void delete_inUse_throws409() {
        given(usageChecker.isInUse(1L)).willReturn(true);
        assertThatThrownBy(() -> service.delete(1L))
                .isInstanceOf(com.example.serverprovision.provisioning.biossetting.exception.BiosSettingTemplateInUseException.class);
    }

    @Test
    @DisplayName("delete happy — 미사용 템플릿 hard delete")
    void delete_removes() {
        BiosSettingTemplate template = savedTemplate();
        given(repository.findById(1L)).willReturn(java.util.Optional.of(template));

        service.delete(1L);

        verify(repository).delete(template);
    }
}
