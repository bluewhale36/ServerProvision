package com.example.serverprovision.management.bios.controller;

import com.example.serverprovision.management.bios.exception.BiosNotFoundException;
import com.example.serverprovision.management.bios.service.BiosService;
import com.example.serverprovision.management.board.exception.InvalidVersionRankRequestException;
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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * E2-1-a — 버전 순위 재정렬 PATCH 의 HTTP 계약. Mocking 은 Service 까지 —
 * {@link InvalidVersionRankRequestException}(신설 400) 의 advice 매핑(fieldErrors)과
 * forging 404 가 실제로 실행되는 경로를 검증한다(new-exception.md 규율: 신설 예외 = 통합 시나리오 동반).
 */
@WebMvcTest(controllers = BiosMetadataController.class)
class BiosControllerRankFlowTest {

    @Autowired MockMvc mvc;

    @MockitoBean BiosService biosService;
    @MockitoBean BoardModelMetadataService boardModelService;
    @MockitoBean JpaMetamodelMappingContext jpaMetamodelMappingContext;

    private static final String URL = "/management/bios/10/rank";

    @Test
    @DisplayName("PATCH rank — 204 + Service 위임 (성공은 조용히 — 바뀐 순서가 곧 화면)")
    void reorder_returns204() throws Exception {
        mvc.perform(patch(URL).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"orderedIds\":[2,1,3]}"))
                .andExpect(status().isNoContent());
        verify(biosService).reorderVersionRanks(eq(10L), eq(List.of(2L, 1L, 3L)));
    }

    @Test
    @DisplayName("PATCH rank — 중복 id → 400 + fieldErrors[orderedIds] (InvalidVersionRankRequest 실매핑)")
    void reorder_duplicated_returns400WithFieldError() throws Exception {
        willThrow(InvalidVersionRankRequestException.duplicated())
                .given(biosService).reorderVersionRanks(eq(10L), eq(List.of(1L, 1L)));

        mvc.perform(patch(URL).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"orderedIds\":[1,1]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("orderedIds"));
    }

    @Test
    @DisplayName("PATCH rank — 누락 목록 → 400 (직접 PATCH · stale 화면 안전망)")
    void reorder_incomplete_returns400() throws Exception {
        willThrow(InvalidVersionRankRequestException.incomplete())
                .given(biosService).reorderVersionRanks(eq(10L), eq(List.of(1L)));

        mvc.perform(patch(URL).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"orderedIds\":[1]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("orderedIds"));
    }

    @Test
    @DisplayName("PATCH rank — 빈 목록 → 400 (@NotEmpty Bean Validation)")
    void reorder_empty_returns400() throws Exception {
        mvc.perform(patch(URL).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"orderedIds\":[]}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("PATCH rank — 타 보드 · 미존재 id → 404 (forging 관례)")
    void reorder_foreignId_returns404() throws Exception {
        willThrow(new BiosNotFoundException(10L, 77L))
                .given(biosService).reorderVersionRanks(eq(10L), eq(List.of(77L)));

        mvc.perform(patch(URL).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"orderedIds\":[77]}"))
                .andExpect(status().isNotFound());
    }
}
