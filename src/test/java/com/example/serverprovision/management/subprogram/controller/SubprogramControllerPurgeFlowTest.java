package com.example.serverprovision.management.subprogram.controller;

import com.example.serverprovision.global.exception.TypedNameMismatchException;
import com.example.serverprovision.management.board.service.BoardModelService;
import com.example.serverprovision.management.common.filesystem.service.DirectoryBrowseService;
import com.example.serverprovision.management.subprogram.service.SubprogramService;
import com.example.serverprovision.management.subprogram.service.SubprogramUploadIntentService;
import com.example.serverprovision.management.subprogram.service.SubprogramVerificationLauncher;
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
 * S5-2-2 — Subprogram 영구 삭제 (purge) 의 typed-name 검증 흐름 통합 테스트. 2 시나리오.
 */
@WebMvcTest(controllers = SubprogramController.class)
class SubprogramControllerPurgeFlowTest {

    @Autowired MockMvc mvc;

    @MockitoBean SubprogramService subprogramService;
    @MockitoBean SubprogramUploadIntentService subprogramUploadIntentService;
    @MockitoBean com.example.serverprovision.management.subprogram.service.SubprogramNudgeService subprogramNudgeService;
    @MockitoBean SubprogramVerificationLauncher subprogramVerificationLauncher;
    @MockitoBean BoardModelService boardModelService;
    @MockitoBean DirectoryBrowseService directoryBrowseService;
    @MockitoBean com.example.serverprovision.global.lifecycle.DeleteIntentRegistry deleteIntentRegistry;
    @MockitoBean JpaMetamodelMappingContext jpaMetamodelMappingContext;

    @Test
    @DisplayName("Subprogram purge — typedName 일치 → 302 redirect")
    void purge_typedNameMatches_returns302() throws Exception {
        willDoNothing().given(subprogramService)
                .purgeWithTypedNameCheck(eq(7L), eq("ASPEED_1-15-03_MS03-CE0"));

        mvc.perform(post("/management/subprogram/7/purge")
                        .param("typedName", "ASPEED_1-15-03_MS03-CE0"))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location",
                        "/management/subprogram?includeDeleted=true"));
    }

    @Test
    @DisplayName("Subprogram purge — typedName 불일치 → 400")
    void purge_typedNameMismatch_returns400() throws Exception {
        willThrow(new TypedNameMismatchException("ASPEED_1-15-03_MS03-CE0", "wrong"))
                .given(subprogramService)
                .purgeWithTypedNameCheck(eq(7L), eq("wrong"));

        mvc.perform(post("/management/subprogram/7/purge").param("typedName", "wrong"))
                .andExpect(status().isBadRequest());
    }
}
