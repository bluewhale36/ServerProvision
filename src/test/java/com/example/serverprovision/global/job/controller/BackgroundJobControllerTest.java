package com.example.serverprovision.global.job.controller;

import com.example.serverprovision.global.job.BackgroundJob;
import com.example.serverprovision.global.job.enums.JobType;
import com.example.serverprovision.global.job.service.BackgroundJobService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.data.jpa.mapping.JpaMetamodelMappingContext;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * R9-1 — 알림 센터 폴링 응답의 metadata 병합 계약 검증.
 * <p>완료 시점 결과 수치(driftCount 등)가 등록 metadata 와 한 map 으로 병합되어 내려와야
 * 프론트({@code background-jobs.js})가 기존 {@code metadata} 필드 하나로 소비할 수 있다.</p>
 */
@WebMvcTest(controllers = BackgroundJobController.class)
class BackgroundJobControllerTest {

    @Autowired MockMvc mvc;

    @MockitoBean BackgroundJobService backgroundJobService;
    @MockitoBean JpaMetamodelMappingContext jpaMetamodelMappingContext;

    private BackgroundJob jobWith(Map<String, String> registrationMetadata) {
        return new BackgroundJob(
                "j1", JobType.PATH_RECONCILIATION, "경로 점검", "sub",
                List.of("작업"), registrationMetadata
        );
    }

    @Test
    @DisplayName("GET /jobs : 완료 결과 metadata 가 등록 metadata 와 병합되어 내려온다")
    void list_mergesResultMetadataIntoMetadata() throws Exception {
        BackgroundJob job = jobWith(Map.of("osId", "42"));
        job.startStage(0);
        job.complete(Map.of("driftCount", "3"));
        given(backgroundJobService.snapshot()).willReturn(List.of(job));

        mvc.perform(get("/jobs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.jobs[0].status").value("COMPLETED"))
                .andExpect(jsonPath("$.jobs[0].metadata.osId").value("42"))
                .andExpect(jsonPath("$.jobs[0].metadata.driftCount").value("3"));
    }

    @Test
    @DisplayName("GET /jobs : 결과 metadata 없는 완료 Job 은 등록 metadata 만 — 기존 계약 보존")
    void list_withoutResultMetadata_preservesLegacyShape() throws Exception {
        BackgroundJob job = jobWith(Map.of("osId", "42"));
        job.startStage(0);
        job.complete();
        given(backgroundJobService.snapshot()).willReturn(List.of(job));

        mvc.perform(get("/jobs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.jobs[0].metadata.osId").value("42"))
                .andExpect(jsonPath("$.jobs[0].metadata.driftCount").doesNotExist());
    }
}
