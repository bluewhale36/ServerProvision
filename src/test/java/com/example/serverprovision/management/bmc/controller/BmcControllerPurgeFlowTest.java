package com.example.serverprovision.management.bmc.controller;

import com.example.serverprovision.global.exception.TypedNameMismatchException;
import com.example.serverprovision.management.bmc.service.BmcService;
import com.example.serverprovision.management.bmc.service.BmcUploadIntentService;
import com.example.serverprovision.management.bmc.service.BmcVerificationLauncher;
import com.example.serverprovision.management.board.service.BoardModelService;
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
 * S5-2-2 — BMC 영구 삭제 (purge) 의 typed-name 검증 흐름 통합 테스트. 2 시나리오.
 */
@WebMvcTest(controllers = {BmcLifecycleController.class, BmcMetadataController.class, BmcUploadController.class})
class BmcControllerPurgeFlowTest {
    @org.springframework.test.context.bean.override.mockito.MockitoBean com.example.serverprovision.global.trash.service.TypedNameVerifier typedNameVerifier;

    @Autowired MockMvc mvc;

    @MockitoBean BmcService bmcService;
    @MockitoBean BmcUploadIntentService bmcUploadIntentService;
    @MockitoBean com.example.serverprovision.management.bmc.service.BmcNudgeService bmcNudgeService;
    @MockitoBean BoardModelService boardModelService;
    @MockitoBean BmcVerificationLauncher bmcVerificationLauncher;
    @MockitoBean DirectoryBrowseService directoryBrowseService;
    @MockitoBean com.example.serverprovision.global.lifecycle.DeleteIntentRegistry deleteIntentRegistry;
    @MockitoBean JpaMetamodelMappingContext jpaMetamodelMappingContext;

    @Test
    @DisplayName("BMC purge — typedName 일치 → 302 redirect")
    void purge_typedNameMatches_returns302() throws Exception {
        willDoNothing().given(bmcService)
                .purgeWithTypedNameCheck(eq(2L), eq(5L), eq("GIGABYTE BMC Firmware 13.06.25"));

        mvc.perform(post("/management/bmc/2/bmc/5/purge")
                        .param("typedName", "GIGABYTE BMC Firmware 13.06.25"))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location",
                        "/management/bmc?selectBoardId=2&includeDeleted=true"));
    }

    @Test
    @DisplayName("BMC purge — typedName 불일치 → 400")
    void purge_typedNameMismatch_returns400() throws Exception {
        willThrow(new TypedNameMismatchException("GIGABYTE BMC Firmware 13.06.25", "x"))
                .given(bmcService)
                .purgeWithTypedNameCheck(eq(2L), eq(5L), eq("x"));

        mvc.perform(post("/management/bmc/2/bmc/5/purge").param("typedName", "x"))
                .andExpect(status().isBadRequest());
    }

    // S5-4 — '삭제된 항목 포함' 체크박스의 마커 검증 + inline onchange 부재 회귀.
    @Test
    @DisplayName("GET /management/bmc — '삭제된 항목 포함' 체크박스에 data-include-deleted-toggle 마커 + inline onchange 부재")
    void renders_includeDeletedToggle_with_dataMarker() throws Exception {
        given(bmcService.findAllGrouped(false)).willReturn(List.of());

        mvc.perform(get("/management/bmc"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("data-include-deleted-toggle")))
                .andExpect(content().string(not(containsString("onchange=\"window.location"))));
    }

    // S5-5 — 외부 우상단 + 신규 BMC 등록 버튼 노출.
    @Test
    @DisplayName("GET /management/bmc — 외부 우상단 '+ 신규 BMC 등록' 버튼 노출")
    void renders_external_register_button() throws Exception {
        given(bmcService.findAllGrouped(false)).willReturn(List.of());

        mvc.perform(get("/management/bmc"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("+ 신규 BMC 등록")));
    }

    // S5-6-1 — purge form 에 typed-name expected value 부재 + 자원 식별 마커만 존재.
    @Test
    @DisplayName("GET /management/bmc — purge form 에 data-typed-name 부재 + data-resource-type/id 존재")
    void form_no_longer_exposes_typed_name() throws Exception {
        // purge form 은 isDeleted=true 분기에서만 렌더 (휴지통 모드).
        var bmc = new com.example.serverprovision.management.bmc.dto.response.BmcResponse(
                5L, 3L,
                "GIGABYTE_BMC_13.06.25", "13.06.25",
                "/opt/firmware/bmc/gigabyte/ms73/13.06.25", null,
                "hash-stub",
                0, 0L,
                null,
                com.example.serverprovision.management.bios.vo.IntegrityStatus.NOT_VERIFIED,
                false, true, false, false, false, false);
        var boardWithBmc = new com.example.serverprovision.management.bmc.dto.response.BoardWithBmcListResponse(
                3L, com.example.serverprovision.management.board.enums.Vendor.GIGABYTE,
                "Gigabyte", "MS73-HB1", false,
                java.util.List.of(bmc));
        given(bmcService.findAllGrouped(false)).willReturn(java.util.List.of(boardWithBmc));

        mvc.perform(get("/management/bmc"))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("data-typed-name"))))
                .andExpect(content().string(containsString("data-resource-type=\"BMC_FIRMWARE\"")))
                .andExpect(content().string(containsString("data-resource-id=\"5\"")));
    }

    // S5-5 — boardId 없는 /new 진입점이 메인보드 선택 dropdown 을 노출.
    @Test
    @DisplayName("GET /management/bmc/new — boardId 없는 진입 → 메인보드 선택 dropdown")
    void renders_new_without_boardId() throws Exception {
        given(boardModelService.findAllGrouped(false)).willReturn(List.of());

        mvc.perform(get("/management/bmc/new"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("data-bmc-board-redirect")));
    }
}
