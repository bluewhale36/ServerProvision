package com.example.serverprovision.management.bios.controller;

import com.example.serverprovision.global.exception.TypedNameMismatchException;
import com.example.serverprovision.management.board.service.BoardModelService;
import com.example.serverprovision.management.bios.service.BiosService;
import com.example.serverprovision.management.bios.service.BiosUploadIntentService;
import com.example.serverprovision.management.bios.service.BiosVerificationLauncher;
import com.example.serverprovision.management.common.filesystem.service.DirectoryBrowseService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.data.jpa.mapping.JpaMetamodelMappingContext;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willDoNothing;
import static org.mockito.BDDMockito.willThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * S5-2-2 — BIOS 영구 삭제 (purge) 의 typed-name 검증 흐름 통합 테스트. 2 시나리오.
 */
@WebMvcTest(controllers = BiosController.class)
class BiosControllerPurgeFlowTest {
    @org.springframework.test.context.bean.override.mockito.MockitoBean com.example.serverprovision.global.trash.service.TypedNameVerifier typedNameVerifier;

    @Autowired MockMvc mvc;

    @MockitoBean BiosService biosService;
    @MockitoBean BiosUploadIntentService biosUploadIntentService;
    @MockitoBean com.example.serverprovision.management.bios.service.BiosNudgeService biosNudgeService;
    @MockitoBean BoardModelService boardModelService;
    @MockitoBean BiosVerificationLauncher biosVerificationLauncher;
    @MockitoBean DirectoryBrowseService directoryBrowseService;
    @MockitoBean com.example.serverprovision.global.lifecycle.DeleteIntentRegistry deleteIntentRegistry;
    @MockitoBean JpaMetamodelMappingContext jpaMetamodelMappingContext;

    @Test
    @DisplayName("BIOS purge — typedName 일치 → 302 redirect")
    void purge_typedNameMatches_returns302() throws Exception {
        willDoNothing().given(biosService)
                .purgeWithTypedNameCheck(eq(2L), eq(5L), eq("R23_MS73-HB1_Uni"));

        mvc.perform(post("/management/bios/2/bios/5/purge")
                        .param("typedName", "R23_MS73-HB1_Uni"))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location",
                        "/management/bios?selectBoardId=2&includeDeleted=true"));
    }

    @Test
    @DisplayName("BIOS purge — typedName 불일치 → 400")
    void purge_typedNameMismatch_returns400() throws Exception {
        willThrow(new TypedNameMismatchException("R23_MS73-HB1_Uni", "wrong"))
                .given(biosService)
                .purgeWithTypedNameCheck(eq(2L), eq(5L), eq("wrong"));

        mvc.perform(post("/management/bios/2/bios/5/purge").param("typedName", "wrong"))
                .andExpect(status().isBadRequest());
    }

    // S5-4 — '삭제된 항목 포함' 체크박스의 마커 검증 + inline onchange 부재 회귀.
    @Test
    @DisplayName("GET /management/bios — '삭제된 항목 포함' 체크박스에 data-include-deleted-toggle 마커 + inline onchange 부재")
    void renders_includeDeletedToggle_with_dataMarker() throws Exception {
        given(biosService.findAllGrouped(false)).willReturn(List.of());

        mvc.perform(get("/management/bios"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("data-include-deleted-toggle")))
                .andExpect(content().string(not(containsString("onchange=\"window.location"))));
    }

    // S5-5 — 외부 우상단 + 신규 BIOS 등록 버튼이 보이는지 확인.
    @Test
    @DisplayName("GET /management/bios — 외부 우상단 '+ 신규 BIOS 등록' 버튼 노출")
    void renders_external_register_button() throws Exception {
        given(biosService.findAllGrouped(false)).willReturn(List.of());

        mvc.perform(get("/management/bios"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("+ 신규 BIOS 등록")));
    }

    // S5-5 — boardId 없는 /new 진입점이 메인보드 선택 dropdown 을 노출.
    @Test
    @DisplayName("GET /management/bios/new — boardId 없는 진입 → 메인보드 선택 dropdown")
    void renders_new_without_boardId() throws Exception {
        given(boardModelService.findAllGrouped(false)).willReturn(List.of());

        mvc.perform(get("/management/bios/new"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("data-bios-board-redirect")));
    }

    // S5-6-1 — purge form 에 typed-name expected value 부재 + 자원 식별 마커만 존재.
    @Test
    @DisplayName("GET /management/bios — purge form 에 data-typed-name 부재 + data-resource-type/id 존재")
    void form_no_longer_exposes_typed_name() throws Exception {
        // purge form 은 isDeleted=true 분기에서만 렌더 (휴지통 모드).
        var bios = new com.example.serverprovision.management.bios.dto.response.BiosResponse(
                7L, 3L,
                "R23_MS73-HB1_Uni", "R23",
                "/opt/bios/gigabyte/ms73-hb1/r23", "flash.nsh",
                "hash-stub",
                0, 0L,
                null,
                com.example.serverprovision.management.bios.vo.IntegrityStatus.NOT_VERIFIED,
                false, true, false, false, false, false);
        var boardWithBios = new com.example.serverprovision.management.bios.dto.response.BoardWithBiosListResponse(
                3L, com.example.serverprovision.management.board.enums.Vendor.GIGABYTE,
                "Gigabyte", "MS73-HB1", false,
                java.util.List.of(bios));
        given(biosService.findAllGrouped(false)).willReturn(java.util.List.of(boardWithBios));

        mvc.perform(get("/management/bios"))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("data-typed-name"))))
                .andExpect(content().string(containsString("data-resource-type=\"BIOS_BUNDLE\"")))
                .andExpect(content().string(containsString("data-resource-id=\"7\"")));
    }

    // S5-5 — /{boardId}/new 에 X-Requested-With 헤더 동봉 시 formCard fragment 만 반환 (nav / page-header 부재).
    @Test
    @DisplayName("GET /management/bios/{boardId}/new (XHR) — formCard fragment 만 반환, nav 부재")
    void renders_form_fragment_for_ajax_request() throws Exception {
        var board = new com.example.serverprovision.management.board.dto.response.BoardModelResponse(
                3L, com.example.serverprovision.management.board.enums.Vendor.GIGABYTE, "MS73-HB1",
                "desc", 0, 0, 0, true, false, false,
                com.example.serverprovision.global.lifecycle.LifecycleStage.ACTIVE);
        given(boardModelService.findById(3L)).willReturn(board);

        mvc.perform(get("/management/bios/3/new").header("X-Requested-With", "XMLHttpRequest"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("id=\"biosForm\"")))
                .andExpect(content().string(not(containsString("navbar"))));
    }
}
