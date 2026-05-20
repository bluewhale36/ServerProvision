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

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.willDoNothing;
import static org.mockito.BDDMockito.willThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * S5-2-2 — BMC 영구 삭제 (purge) 의 typed-name 검증 흐름 통합 테스트. 2 시나리오.
 */
@WebMvcTest(controllers = BmcController.class)
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
}
