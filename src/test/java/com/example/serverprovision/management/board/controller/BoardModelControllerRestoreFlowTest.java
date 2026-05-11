package com.example.serverprovision.management.board.controller;

import com.example.serverprovision.management.board.enums.Vendor;
import com.example.serverprovision.management.board.exception.DuplicateBoardModelException;
import com.example.serverprovision.management.board.service.BoardModelNudgeService;
import com.example.serverprovision.management.board.service.BoardModelService;
import com.example.serverprovision.management.common.dto.response.RestoreResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.data.jpa.mapping.JpaMetamodelMappingContext;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * S5-2-3 — Board restore cascade 흐름 통합 테스트. 4 시나리오.
 */
@WebMvcTest(controllers = BoardModelController.class)
class BoardModelControllerRestoreFlowTest {

    @Autowired MockMvc mvc;

    @MockitoBean BoardModelService boardModelService;
    @MockitoBean BoardModelNudgeService boardModelNudgeService;
    @MockitoBean JpaMetamodelMappingContext jpaMetamodelMappingContext;

    @Test
    @DisplayName("Board restore cascade=true — 하위 BIOS 1 + BMC 1 복구 → 302 redirect")
    void restore_cascadeTrue_returns302() throws Exception {
        given(boardModelService.restore(eq(3L), eq(true))).willReturn(new RestoreResponse(2));

        mvc.perform(post("/management/board/3/restore").param("cascade", "true"))
                .andExpect(status().is3xxRedirection());
    }

    @Test
    @DisplayName("Board restore cascade=false — 부모만 복구 → 302 redirect")
    void restore_cascadeFalse_returns302() throws Exception {
        given(boardModelService.restore(eq(3L), eq(false))).willReturn(RestoreResponse.none());

        mvc.perform(post("/management/board/3/restore").param("cascade", "false"))
                .andExpect(status().is3xxRedirection());
    }

    @Test
    @DisplayName("Board restore — cascade 파라미터 누락 시 default false")
    void restore_noCascadeParam_defaultsFalse() throws Exception {
        given(boardModelService.restore(eq(3L), eq(false))).willReturn(RestoreResponse.none());

        mvc.perform(post("/management/board/3/restore"))
                .andExpect(status().is3xxRedirection());
    }

    @Test
    @DisplayName("Board restore cascade=true — 활성 (vendor, modelName) 충돌 → 409")
    void restore_cascade_duplicate_returns409() throws Exception {
        willThrow(new DuplicateBoardModelException(Vendor.ASUS, "P13R-E"))
                .given(boardModelService).restore(eq(3L), eq(true));

        mvc.perform(post("/management/board/3/restore").param("cascade", "true"))
                .andExpect(status().isConflict());
    }
}
