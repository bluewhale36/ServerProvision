package com.example.serverprovision.provisioning.biossetting.controller;

import com.example.serverprovision.provisioning.biossetting.dto.response.BiosSettingBoardCardResponse;
import com.example.serverprovision.provisioning.biossetting.dto.response.BiosSettingTemplateDetailResponse;
import com.example.serverprovision.provisioning.biossetting.dto.response.BiosSettingTemplateEditViewResponse;
import com.example.serverprovision.provisioning.biossetting.dto.response.BiosSettingTemplateSummaryResponse;
import com.example.serverprovision.provisioning.biossetting.exception.BiosSettingTemplateNotFoundException;
import com.example.serverprovision.management.board.exception.BoardModelNotFoundException;
import com.example.serverprovision.provisioning.biossetting.service.BiosSettingTemplateQueryService;
import com.example.serverprovision.provisioning.dto.response.BiosSetupPageResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.data.jpa.mapping.JpaMetamodelMappingContext;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

/**
 * U2-2-1 CP4 — {@link BiosSettingTemplateController} SSR 통합 테스트 (목록 / 보드선택 / 작성 편집기).
 */
@WebMvcTest(controllers = BiosSettingTemplateController.class)
class BiosSettingTemplateControllerTest {

    @Autowired MockMvc mvc;

    @MockitoBean BiosSettingTemplateQueryService queryService;
    @MockitoBean JpaMetamodelMappingContext jpaMetamodelMappingContext;

    @Test
    @DisplayName("GET /provisioning/bios-setting — 목록 200 + templates")
    void list_returns200() throws Exception {
        given(queryService.findAll()).willReturn(List.of(new BiosSettingTemplateSummaryResponse(
                1L, "Rocky9 표준", null, 2L, "MS74-HB0", 3, false, LocalDateTime.now())));

        mvc.perform(get("/provisioning/bios-setting"))
                .andExpect(status().isOk())
                .andExpect(view().name("provisioning/bios-setting-list"))
                .andExpect(model().attributeExists("templates"));
    }

    @Test
    @DisplayName("GET /new — 보드 선택 랜딩 200 + boards")
    void boardSelect_returns200() throws Exception {
        given(queryService.findBoards()).willReturn(List.of(
                new BiosSettingBoardCardResponse(2L, "MS74-HB0", "GIGABYTE", true)));

        mvc.perform(get("/provisioning/bios-setting/new"))
                .andExpect(status().isOk())
                .andExpect(view().name("provisioning/bios-setting-board-select"))
                .andExpect(model().attributeExists("boards"));
    }

    @Test
    @DisplayName("GET /new/{boardModelId} — 편집기 200 + bios/boardModelId (FK 전환)")
    void editor_returns200() throws Exception {
        given(queryService.editorView(2L)).willReturn(
                new BiosSetupPageResponse("MS74-HB0", List.of(), List.of(), List.of()));

        mvc.perform(get("/provisioning/bios-setting/new/{boardModelId}", 2L))
                .andExpect(status().isOk())
                .andExpect(view().name("provisioning/bios-setting-editor"))
                .andExpect(model().attributeExists("bios", "boardModelId"));
    }

    @Test
    @DisplayName("GET /new/{boardModelId} — 없는/삭제된 보드 → BoardModelNotFound 404 (advice)")
    void editor_unknownBoard_returns404() throws Exception {
        willThrow(new BoardModelNotFoundException(99L)).given(queryService).editorView(99L);

        mvc.perform(get("/provisioning/bios-setting/new/{boardModelId}", 99L).accept(MediaType.TEXT_HTML))
                .andExpect(status().isNotFound());
    }

    /* ─────────────────────────── U2-2-2 — 상세 / 수정 편집기 ─────────────────────────── */

    private BiosSettingTemplateDetailResponse detail(long id, boolean catalogMissing) {
        return new BiosSettingTemplateDetailResponse(
                id, "Rocky9 표준", "설명", 6L, "MS73-HB1", false, LocalDateTime.now(), LocalDateTime.now(),
                catalogMissing,
                catalogMissing ? List.of() : List.of(new BiosSettingTemplateDetailResponse.Group(
                        "./Advanced/Trusted Computing",
                        List.of(new BiosSettingTemplateDetailResponse.Entry(
                                "TCG003", "TPM State", "select", "비활성", "Disable", "활성", "Enable", true)))),
                List.of(),
                new BiosSettingTemplateDetailResponse.RedfishPreview(
                        "PATCH", "/redfish/v1/Systems/Self/Bios/SD",
                        "{\n  \"Attributes\" : {\n    \"TCG003\" : \"Enable\"\n  }\n}",
                        true, "/redfish/v1/Systems/Self/Actions/ComputerSystem.Reset",
                        "{\"ResetType\": \"ForceRestart\"}"));
    }

    @Test
    @DisplayName("GET /{id} — 상세 200 + template (재조인 그룹 + Redfish 프리뷰)")
    void detail_returns200() throws Exception {
        given(queryService.findDetail(1L)).willReturn(detail(1L, false));

        mvc.perform(get("/provisioning/bios-setting/{id}", 1L))
                .andExpect(status().isOk())
                .andExpect(view().name("provisioning/bios-setting-detail"))
                .andExpect(model().attributeExists("template"));
    }

    @Test
    @DisplayName("GET /{id}/edit — 수정 편집기 200 + bios/boardKey/editTemplate (생성 화면 재사용)")
    void editForm_returns200() throws Exception {
        given(queryService.editorViewFor(1L)).willReturn(new BiosSettingTemplateEditViewResponse(
                1L, "Rocky9 표준", "설명", 6L, "MS73-HB1",
                new BiosSetupPageResponse("MS73-HB1", List.of(), List.of(), List.of())));

        mvc.perform(get("/provisioning/bios-setting/{id}/edit", 1L))
                .andExpect(status().isOk())
                .andExpect(view().name("provisioning/bios-setting-editor"))
                .andExpect(model().attributeExists("bios", "boardModelId", "editTemplate"));
    }

    @Test
    @DisplayName("GET /{id} · /{id}/edit — 없는 id → BiosSettingTemplateNotFound 404 (신규 예외 시나리오)")
    void detailAndEdit_notFound_returns404() throws Exception {
        willThrow(new BiosSettingTemplateNotFoundException(99L)).given(queryService).findDetail(99L);
        willThrow(new BiosSettingTemplateNotFoundException(99L)).given(queryService).editorViewFor(99L);

        mvc.perform(get("/provisioning/bios-setting/{id}", 99L).accept(MediaType.TEXT_HTML))
                .andExpect(status().isNotFound());
        mvc.perform(get("/provisioning/bios-setting/{id}/edit", 99L).accept(MediaType.TEXT_HTML))
                .andExpect(status().isNotFound());
    }
}
