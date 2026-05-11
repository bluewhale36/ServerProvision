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

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.willDoNothing;
import static org.mockito.BDDMockito.willThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * S5-2-2 — BIOS 영구 삭제 (purge) 의 typed-name 검증 흐름 통합 테스트. 2 시나리오.
 */
@WebMvcTest(controllers = BiosController.class)
class BiosControllerPurgeFlowTest {

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
}
