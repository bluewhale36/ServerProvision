package com.example.serverprovision.management.bmc.controller;

import com.example.serverprovision.management.bmc.exception.BmcNotFoundException;
import com.example.serverprovision.management.bmc.service.BmcService;
import com.example.serverprovision.management.board.service.metadata.BoardModelMetadataService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.data.jpa.mapping.JpaMetamodelMappingContext;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** E2-1-a — BMC 순위 PATCH 의 대칭 계약(핵심 2경로 — 상세 시나리오는 BiosControllerRankFlowTest 가 대표). */
@WebMvcTest(controllers = BmcMetadataController.class)
class BmcControllerRankFlowTest {

    @Autowired MockMvc mvc;

    @MockitoBean BmcService bmcService;
    @MockitoBean BoardModelMetadataService boardModelService;
    @MockitoBean JpaMetamodelMappingContext jpaMetamodelMappingContext;

    @Test
    @DisplayName("PATCH rank — 204 + Service 위임 (대칭)")
    void reorder_returns204() throws Exception {
        mvc.perform(patch("/management/bmc/10/rank").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"orderedIds\":[2,1]}"))
                .andExpect(status().isNoContent());
        verify(bmcService).reorderVersionRanks(eq(10L), eq(List.of(2L, 1L)));
    }

    @Test
    @DisplayName("PATCH rank — forging 404 (대칭)")
    void reorder_foreignId_returns404() throws Exception {
        willThrow(new BmcNotFoundException(10L, 77L))
                .given(bmcService).reorderVersionRanks(eq(10L), eq(List.of(77L)));

        mvc.perform(patch("/management/bmc/10/rank").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"orderedIds\":[77]}"))
                .andExpect(status().isNotFound());
    }
}
