package com.example.serverprovision.management.subprogram.controller;

import com.example.serverprovision.management.bios.vo.IntegrityStatus;
import com.example.serverprovision.management.board.service.metadata.BoardModelMetadataService;
import com.example.serverprovision.management.common.dto.response.IntegrityStatusResponse;
import com.example.serverprovision.management.common.filesystem.service.DirectoryBrowseService;
import com.example.serverprovision.management.subprogram.dto.request.SubprogramUploadIntentRequest;
import com.example.serverprovision.management.subprogram.dto.response.SubprogramResponse;
import com.example.serverprovision.management.subprogram.dto.response.SubprogramUploadIntentResponse;
import com.example.serverprovision.management.subprogram.enums.SubprogramKind;
import com.example.serverprovision.management.subprogram.enums.SubprogramUploadMode;
import com.example.serverprovision.management.subprogram.exception.DuplicateSubprogramVersionException;
import com.example.serverprovision.management.subprogram.exception.SubprogramNotFoundException;
import com.example.serverprovision.management.subprogram.service.SubprogramService;
import com.example.serverprovision.management.subprogram.service.SubprogramUploadIntentService;
import com.example.serverprovision.management.subprogram.service.SubprogramVerificationLauncher;
import com.example.serverprovision.management.subprogram.vo.BoardScope;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.data.jpa.mapping.JpaMetamodelMappingContext;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@WebMvcTest(controllers = {SubprogramController.class, SubprogramUploadController.class, SubprogramNudgeController.class})
class SubprogramControllerTest {
    @org.springframework.test.context.bean.override.mockito.MockitoBean com.example.serverprovision.global.trash.service.TypedNameVerifier typedNameVerifier;

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper om;

    @MockitoBean SubprogramService subprogramService;
    @MockitoBean SubprogramUploadIntentService subprogramUploadIntentService;
    @MockitoBean com.example.serverprovision.management.subprogram.service.SubprogramNudgeService subprogramNudgeService;
    @MockitoBean SubprogramVerificationLauncher subprogramVerificationLauncher;
    @MockitoBean BoardModelMetadataService boardModelService;
    @MockitoBean DirectoryBrowseService directoryBrowseService;
    @MockitoBean com.example.serverprovision.global.lifecycle.DeleteIntentRegistry deleteIntentRegistry;
    @MockitoBean JpaMetamodelMappingContext jpaMetamodelMappingContext;

    @Test
    @DisplayName("GET /management/subprogram : 페이지 200 + 두 Miller 모델 주입")
    void list_page() throws Exception {
        given(subprogramService.findAllGrouped(SubprogramKind.DRIVER, false)).willReturn(List.of());
        given(subprogramService.findAllGrouped(SubprogramKind.UTILITY, false)).willReturn(List.of());

        mvc.perform(get("/management/subprogram"))
                .andExpect(status().isOk())
                .andExpect(view().name("management/subprogram/list"));
    }

    // S5-5 — 미러 헤더가 통일 텍스트 사용 + 기존 '드라이버 분류' 어휘 부재.
    @Test
    @DisplayName("GET /management/subprogram — 미러 헤더가 '메인보드 모델' / '드라이버' / '유틸리티' 로 통일 + '드라이버 분류' 부재")
    void miller_headers_use_unified_text() throws Exception {
        given(subprogramService.findAllGrouped(SubprogramKind.DRIVER, false)).willReturn(List.of());
        given(subprogramService.findAllGrouped(SubprogramKind.UTILITY, false)).willReturn(List.of());

        mvc.perform(get("/management/subprogram"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("메인보드 모델")))
                .andExpect(content().string(containsString("드라이버")))
                .andExpect(content().string(containsString("유틸리티")))
                .andExpect(content().string(not(containsString("드라이버 분류"))))
                .andExpect(content().string(not(containsString("유틸리티 분류"))));
    }

    // S5-4 — Driver / Utility 두 섹션 모두 data-include-deleted-toggle 마커 + inline onchange 부재.
    @Test
    @DisplayName("GET /management/subprogram — 2 체크박스 (Driver / Utility) 모두 data-include-deleted-toggle 마커 + inline onchange 부재")
    void renders_two_includeDeletedToggles() throws Exception {
        given(subprogramService.findAllGrouped(SubprogramKind.DRIVER, false)).willReturn(List.of());
        given(subprogramService.findAllGrouped(SubprogramKind.UTILITY, false)).willReturn(List.of());

        String body = mvc.perform(get("/management/subprogram"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("data-include-deleted-toggle")))
                .andExpect(content().string(not(containsString("onchange=\"window.location"))))
                .andReturn().getResponse().getContentAsString();

        int occurrences = body.split("data-include-deleted-toggle", -1).length - 1;
        if (occurrences != 2) {
            throw new AssertionError(
                    "data-include-deleted-toggle 마커가 정확히 2 개여야 한다 (Driver / Utility 섹션). 실제: " + occurrences);
        }
    }

    @Test
    @DisplayName("GET /new?kind=DRIVER : 신규 폼 렌더")
    void new_form() throws Exception {
        given(boardModelService.findAllGrouped(false)).willReturn(List.of());
        mvc.perform(get("/management/subprogram/new").param("kind", "DRIVER"))
                .andExpect(status().isOk())
                .andExpect(view().name("management/subprogram/subprogram-new"));
    }

    @Test
    @DisplayName("POST /{kind}/{boardScope}/upload-intent (정상) : 200 + uploadToken")
    void intent_success() throws Exception {
        var req = new SubprogramUploadIntentRequest("/mnt/x", SubprogramUploadMode.FOLDER, 5, 1024L, "1.0", false);
        given(subprogramUploadIntentService.issue(eq(SubprogramKind.DRIVER), any(BoardScope.class), any()))
                .willReturn(new SubprogramUploadIntentResponse("tok-1", List.of(), null));

        mvc.perform(post("/management/subprogram/driver/common/upload-intent")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.uploadToken").value("tok-1"));
    }

    @Test
    @DisplayName("POST /upload-intent (입력 검증 실패) : 400")
    void intent_validation_400() throws Exception {
        // version 필드를 비워 NotBlank 위반
        String body = """
                {
                  "targetDirectory": "/x",
                  "uploadMode": "FOLDER",
                  "fileCount": 1,
                  "totalBytes": 1,
                  "version": "",
                  "allowCreateDirectory": false
                }
                """;
        mvc.perform(post("/management/subprogram/driver/common/upload-intent")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /upload-intent (활성 동일 트리 점유) : 409")
    void intent_conflict_409() throws Exception {
        var req = new SubprogramUploadIntentRequest("/mnt/x", SubprogramUploadMode.FOLDER, 5, 1024L, "1.0", false);
        willThrow(new DuplicateSubprogramVersionException(SubprogramKind.DRIVER, BoardScope.COMMON, "n", "1.0"))
                .given(subprogramUploadIntentService).issue(any(), any(), any());

        mvc.perform(post("/management/subprogram/driver/common/upload-intent")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(req)))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("GET /{id} : 상세 200")
    void detail_200() throws Exception {
        given(subprogramService.findSubprogram(7L))
                .willReturn(new SubprogramResponse(
                        7L, SubprogramKind.UTILITY, "유틸리티",
                        null, "raid-cli", "1.0", "/x", null, "h",
                        1, 100L, "d", IntegrityStatus.ORIGINAL, true, false, false,
                        com.example.serverprovision.global.lifecycle.LifecycleStage.ACTIVE,
                        false, false, false));

        mvc.perform(get("/management/subprogram/7"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(7))
                .andExpect(jsonPath("$.kind").value("UTILITY"))
                .andExpect(jsonPath("$.boardId").doesNotExist());
    }

    @Test
    @DisplayName("GET /{id} (존재 X) : 404")
    void detail_404() throws Exception {
        willThrow(new SubprogramNotFoundException(99L))
                .given(subprogramService).findSubprogram(99L);

        mvc.perform(get("/management/subprogram/99"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("GET /{id}/integrity-status : 저장된 status 반환")
    void integrity_status_200() throws Exception {
        given(subprogramService.findIntegrityStatus(5L))
                .willReturn(IntegrityStatusResponse.of(5L, IntegrityStatus.ORIGINAL, Instant.parse("2026-04-28T00:00:00Z")));

        mvc.perform(get("/management/subprogram/5/integrity-status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.integrityStatus").value("ORIGINAL"));
    }

    @Test
    @DisplayName("POST /{id}/verify : JobStartResponse")
    void verify_200() throws Exception {
        given(subprogramVerificationLauncher.startVerification(5L)).willReturn("job-1");

        mvc.perform(post("/management/subprogram/5/verify"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.jobId").value("job-1"));
    }

    @Test
    @DisplayName("POST /{id}/toggle : 호출 후 redirect")
    void toggle_redirect() throws Exception {
        mvc.perform(post("/management/subprogram/3/toggle"))
                .andExpect(status().is3xxRedirection());
    }

    // =========== MK2 WAVE 2 — Intent (단계 A) Nudge 4 시나리오 ===========

    @org.junit.jupiter.api.Nested
    @DisplayName("MK2 WAVE 2 — Intent Nudge")
    class IntentNudge {

        @Test
        @DisplayName("intent : 메타 충돌 → 409 NUDGE_REQUIRED (Driver, common scope)")
        void intentMetaNudge() throws Exception {
            var req = new SubprogramUploadIntentRequest("/mnt/x", SubprogramUploadMode.FOLDER, 5, 1024, "1.0.0", false);
            java.util.UUID nudgeId = java.util.UUID.randomUUID();
            var session = new com.example.serverprovision.management.common.nudge.NudgeSession(
                    nudgeId,
                    com.example.serverprovision.management.common.nudge.NudgeResourceType.SUBPROGRAM,
                    null,
                    List.of(99L),
                    new com.example.serverprovision.management.common.nudge.IntentMetaNudgePayload(java.util.Map.of()),
                    Instant.now(), Instant.now().plusSeconds(300));
            var conflicts = List.of(new com.example.serverprovision.management.common.nudge.dto.NudgeConflictEntry(
                    99L,
                    com.example.serverprovision.global.lifecycle.LifecycleStage.DEPRECATED,
                    "hash", "Realtek-r8169", "1.0.0", Instant.now()));
            willThrow(new com.example.serverprovision.management.subprogram.exception.SubprogramNudgeRequiredException(
                    "동일 메타", session, conflicts))
                    .given(subprogramUploadIntentService).issue(eq(SubprogramKind.DRIVER), any(BoardScope.class), any());

            mvc.perform(post("/management/subprogram/DRIVER/common/upload-intent")
                            .contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsString(req)))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("NUDGE_REQUIRED"))
                    .andExpect(jsonPath("$.nudgeId").value(nudgeId.toString()))
                    .andExpect(jsonPath("$.conflicts[0].id").value(99));
        }

        @Test
        @DisplayName("intent-nudge proceed : 200 + 새 uploadToken")
        void intentNudgeProceed() throws Exception {
            java.util.UUID nudgeId = java.util.UUID.randomUUID();
            given(subprogramNudgeService.proceedIntent(eq(nudgeId)))
                    .willReturn(new SubprogramUploadIntentResponse("token-new", List.of(), null));

            mvc.perform(post("/management/subprogram/intent-nudge/" + nudgeId + "/proceed"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.uploadToken").value("token-new"));
        }

        @Test
        @DisplayName("intent-nudge replace : 200 + 새 uploadToken")
        void intentNudgeReplace() throws Exception {
            java.util.UUID nudgeId = java.util.UUID.randomUUID();
            given(subprogramNudgeService.replaceIntent(eq(nudgeId), eq(99L)))
                    .willReturn(new SubprogramUploadIntentResponse("token-after-replace", List.of(), null));

            mvc.perform(post("/management/subprogram/intent-nudge/" + nudgeId + "/replace?targetId=99"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.uploadToken").value("token-after-replace"));
        }

        @Test
        @DisplayName("intent-nudge cancel : 204 NoContent")
        void intentNudgeCancel() throws Exception {
            java.util.UUID nudgeId = java.util.UUID.randomUUID();

            mvc.perform(post("/management/subprogram/intent-nudge/" + nudgeId + "/cancel"))
                    .andExpect(status().isNoContent());
        }
    }
}
