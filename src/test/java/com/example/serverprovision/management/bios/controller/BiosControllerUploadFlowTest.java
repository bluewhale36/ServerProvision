package com.example.serverprovision.management.bios.controller;

import com.example.serverprovision.management.bios.dto.request.BiosUploadIntentRequest;
import com.example.serverprovision.management.bios.dto.response.BiosUploadIntentResponse;
import com.example.serverprovision.management.bios.enums.BiosUploadMode;
import com.example.serverprovision.global.marker.IntegrityStatus;
import com.example.serverprovision.management.common.dto.response.IntegrityStatusResponse;
import com.example.serverprovision.management.common.filesystem.dto.DirectoryListingResponse;
import com.example.serverprovision.management.common.filesystem.service.DirectoryBrowseService;
import com.example.serverprovision.management.bios.exception.BiosNotFoundException;
import com.example.serverprovision.management.common.filesystem.exception.BundleExtractionException;
import com.example.serverprovision.management.bios.exception.DuplicateBiosVersionException;
import com.example.serverprovision.management.common.filesystem.exception.EmptyBundleException;
import com.example.serverprovision.management.bios.exception.EntrypointAmbiguousException;
import com.example.serverprovision.management.bios.exception.EntrypointNotFoundException;
import com.example.serverprovision.management.bios.exception.IllegalBiosStateException;
import com.example.serverprovision.management.common.filesystem.exception.MarkerConflictException;
import com.example.serverprovision.management.common.filesystem.exception.TargetDirectoryNotEmptyException;
import com.example.serverprovision.management.bios.service.BiosService;
import com.example.serverprovision.management.bios.service.BiosUploadIntentService;
import com.example.serverprovision.management.bios.service.BiosVerificationLauncher;
import com.example.serverprovision.management.board.exception.BoardModelNotFoundException;
import com.example.serverprovision.management.board.service.metadata.BoardModelMetadataService;
import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.data.jpa.mapping.JpaMetamodelMappingContext;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * A3 v3 BIOS 번들 컨트롤러 통합 테스트. plan v3 §6 커버리지 — Intent 6 / Upload 7 / Verify+CRUD 4 = 17 시나리오.
 */
@WebMvcTest(controllers = {
        BiosUploadController.class,
        BiosIntegrityController.class,
        BiosLifecycleController.class,
        BiosNudgeController.class,
        BiosBrowseController.class
})
class BiosControllerUploadFlowTest {
    @org.springframework.test.context.bean.override.mockito.MockitoBean com.example.serverprovision.global.trash.service.TypedNameVerifier typedNameVerifier;

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper om;

    @MockitoBean BiosService biosService;
    @MockitoBean com.example.serverprovision.management.bios.service.BiosLifecycleService biosLifecycleService;
    @MockitoBean com.example.serverprovision.management.bios.service.BiosRegistrationService biosRegistrationService;
    @MockitoBean com.example.serverprovision.management.bios.service.BiosIntegrityService biosIntegrityService;
    @MockitoBean BiosUploadIntentService biosUploadIntentService;
    @MockitoBean com.example.serverprovision.management.bios.service.BiosNudgeService biosNudgeService;
    @MockitoBean BoardModelMetadataService boardModelService;
    @MockitoBean BiosVerificationLauncher biosVerificationLauncher;
    @MockitoBean DirectoryBrowseService directoryBrowseService;
    @MockitoBean com.example.serverprovision.global.lifecycle.DeleteIntentRegistry deleteIntentRegistry;
    @MockitoBean JpaMetamodelMappingContext jpaMetamodelMappingContext;

    @Test
    @DisplayName("GET /browse : 정상 경로면 200 JSON")
    void browse_success() throws Exception {
        given(directoryBrowseService.browse(any()))
                .willReturn(new DirectoryListingResponse(
                        "/opt/bios", "/opt",
                        List.of(DirectoryListingResponse.Entry.directory("MS03-CE0"))));

        mvc.perform(get("/management/bios/browse").param("path", "/opt/bios"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.path").value("/opt/bios"))
                .andExpect(jsonPath("$.entries[0].type").value("DIR"))
                .andExpect(jsonPath("$.entries[0].name").value("MS03-CE0"));
    }

    // =========== Intent 6 시나리오 ===========

    @Nested
    @DisplayName("POST /{boardId}/upload-intent")
    class Intent {

        @Test
        @DisplayName("1. 정상 — 200 + uploadToken")
        void success() throws Exception {
            var req = new BiosUploadIntentRequest("/mnt/bios/x", BiosUploadMode.FOLDER, 5, 1024, "2.03", false, "");
            given(biosUploadIntentService.issue(eq(1L), any()))
                    .willReturn(new BiosUploadIntentResponse("token-abc", List.of(), null));

            mvc.perform(post("/management/bios/1/upload-intent")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(om.writeValueAsString(req)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.uploadToken").value("token-abc"));
        }

        @Test
        @DisplayName("2. targetDirectory 공란 → 400")
        void blankTargetDirectory() throws Exception {
            String body = "{\"targetDirectory\":\"\",\"uploadMode\":\"FOLDER\",\"fileCount\":5,\"totalBytes\":1024,\"version\":\"2.03\",\"allowCreateDirectory\":false,\"entrypointRelativePath\":\"\"}";
            mvc.perform(post("/management/bios/1/upload-intent")
                            .contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value(containsString("targetDirectory")));
        }

        @Test
        @DisplayName("3. 존재하지 않는 boardId → 404")
        void boardNotFound() throws Exception {
            var req = new BiosUploadIntentRequest("/mnt/x", BiosUploadMode.FOLDER, 5, 1024, "2.03", false, "");
            willThrow(new BoardModelNotFoundException(999L))
                    .given(biosUploadIntentService).issue(eq(999L), any());

            mvc.perform(post("/management/bios/999/upload-intent")
                            .contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsString(req)))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.message").value(containsString("999")));
        }

        @Test
        @DisplayName("4. 활성 (board, version) 중복 → 409")
        void duplicateVersion() throws Exception {
            var req = new BiosUploadIntentRequest("/mnt/x", BiosUploadMode.FOLDER, 5, 1024, "2.03", false, "");
            willThrow(new DuplicateBiosVersionException(1L, "2.03"))
                    .given(biosUploadIntentService).issue(eq(1L), any());

            mvc.perform(post("/management/bios/1/upload-intent")
                            .contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsString(req)))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.message").value(containsString("2.03")))
                    // S4 — DuplicateBiosVersionException 이 FieldBoundConflictException 상속 →
                    // 응답에 fieldErrors[0].field=version 동봉. 회귀 시 base 가 ConflictException 으로 되돌려진 것.
                    .andExpect(jsonPath("$.fieldErrors[0].field").value("version"));
        }

        @Test
        @DisplayName("5. targetDirectory 비어있지 않음 + marker 없음 → 409")
        void targetNotEmpty() throws Exception {
            var req = new BiosUploadIntentRequest("/mnt/occupied", BiosUploadMode.FOLDER, 5, 1024, "2.03", false, "");
            willThrow(new TargetDirectoryNotEmptyException("/mnt/occupied"))
                    .given(biosUploadIntentService).issue(eq(1L), any());

            mvc.perform(post("/management/bios/1/upload-intent")
                            .contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsString(req)))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.message").value(containsString("비어있지 않습니다")));
        }

        @Test
        @DisplayName("6. targetDirectory 에 기존 marker 존재 → 409 MarkerConflict")
        void markerConflict() throws Exception {
            var req = new BiosUploadIntentRequest("/mnt/claimed", BiosUploadMode.FOLDER, 5, 1024, "2.03", false, "");
            willThrow(new MarkerConflictException("/mnt/claimed"))
                    .given(biosUploadIntentService).issue(eq(1L), any());

            mvc.perform(post("/management/bios/1/upload-intent")
                            .contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsString(req)))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.message").value(containsString("marker")));
        }
    }

    // =========== Upload 7 시나리오 ===========

    @Nested
    @DisplayName("POST /{boardId}/upload")
    class Upload {

        @Test
        @DisplayName("7. 폴더 업로드 성공 — 200 + id/redirect")
        void success_folder() throws Exception {
            given(biosUploadIntentService.consume(eq(1L), eq("token-abc")))
                    .willReturn(new BiosUploadIntentService.Intent(
                            1L, "/mnt/x", BiosUploadMode.FOLDER, 2, 1024L, "2.03", "", Instant.now()));
            given(biosRegistrationService.addBios(eq(1L), any(), eq(BiosUploadMode.FOLDER), any(), any(), any()))
                    .willReturn(42L);

            mvc.perform(multipart("/management/bios/1/upload")
                            .file(new MockMultipartFile("folderFiles", "BiosPkg/f.nsh", null, "x".getBytes()))
                            .param("uploadMode", "FOLDER")
                            .param("name", "MS03 BIOS")
                            .param("version", "2.03")
                            .param("targetDirectory", "/mnt/x")
                            .param("description", "")
                            .param("allowCreateDirectory", "true")
                            .param("entrypointRelativePath", "")
                            .header("X-Upload-Token", "token-abc"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(42))
                    .andExpect(jsonPath("$.redirect").value("/management/bios?selectId=42"));
        }

        @Test
        @DisplayName("8. zip 업로드 성공")
        void success_zip() throws Exception {
            given(biosUploadIntentService.consume(eq(1L), anyString()))
                    .willReturn(new BiosUploadIntentService.Intent(
                            1L, "/mnt/y", BiosUploadMode.ZIP, 1, 2000L, "2.04", "", Instant.now()));
            given(biosRegistrationService.addBios(eq(1L), any(), eq(BiosUploadMode.ZIP), any(), any(), any()))
                    .willReturn(43L);

            mvc.perform(multipart("/management/bios/1/upload")
                            .file(new MockMultipartFile("zipFile", "pkg.zip", null, "zipbytes".getBytes()))
                            .param("uploadMode", "ZIP")
                            .param("name", "y").param("version", "2.04")
                            .param("targetDirectory", "/mnt/y")
                            .param("description", "").param("allowCreateDirectory", "true")
                            .param("entrypointRelativePath", "")
                            .header("X-Upload-Token", "t"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(43));
        }

        @Test
        @DisplayName("9. 토큰 없음 → 409")
        void missingToken() throws Exception {
            willThrow(new com.example.serverprovision.management.os.exception.InvalidUploadTokenException("업로드 토큰이 없습니다."))
                    .given(biosUploadIntentService).consume(eq(1L), anyString());

            mvc.perform(multipart("/management/bios/1/upload")
                            .file(new MockMultipartFile("folderFiles", "BiosPkg/f.nsh", null, "x".getBytes()))
                            .param("uploadMode", "FOLDER")
                            .param("name", "x").param("version", "1.0")
                            .param("targetDirectory", "/mnt/x")
                            .param("description", "").param("allowCreateDirectory", "false")
                            .param("entrypointRelativePath", "")
                            .header("X-Upload-Token", "bogus"))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.message").value(containsString("토큰")));
        }

        @Test
        @DisplayName("10. 진입점 없음 → 409 EntrypointNotFound")
        void entrypointNotFound() throws Exception {
            given(biosUploadIntentService.consume(eq(1L), anyString()))
                    .willReturn(new BiosUploadIntentService.Intent(
                            1L, "/mnt/x", BiosUploadMode.FOLDER, 2, 10L, "1.0", "", Instant.now()));
            willThrow(new EntrypointNotFoundException("번들에 .nsh 없음"))
                    .given(biosRegistrationService).addBios(eq(1L), any(), any(), any(), any(), any());

            mvc.perform(multipart("/management/bios/1/upload")
                            .file(new MockMultipartFile("folderFiles", "Pkg/a.bin", null, "x".getBytes()))
                            .param("uploadMode", "FOLDER")
                            .param("name", "x").param("version", "1.0")
                            .param("targetDirectory", "/mnt/x")
                            .param("description", "").param("allowCreateDirectory", "true")
                            .param("entrypointRelativePath", "")
                            .header("X-Upload-Token", "t"))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.message").value(containsString(".nsh")));
        }

        @Test
        @DisplayName("11. 진입점 2개+ → 409 EntrypointAmbiguous")
        void entrypointAmbiguous() throws Exception {
            given(biosUploadIntentService.consume(eq(1L), anyString()))
                    .willReturn(new BiosUploadIntentService.Intent(
                            1L, "/mnt/x", BiosUploadMode.FOLDER, 2, 10L, "1.0", "", Instant.now()));
            willThrow(new EntrypointAmbiguousException(List.of("a.nsh", "b.nsh")))
                    .given(biosRegistrationService).addBios(eq(1L), any(), any(), any(), any(), any());

            mvc.perform(multipart("/management/bios/1/upload")
                            .file(new MockMultipartFile("folderFiles", "Pkg/a.nsh", null, "x".getBytes()))
                            .param("uploadMode", "FOLDER")
                            .param("name", "x").param("version", "1.0")
                            .param("targetDirectory", "/mnt/x")
                            .param("description", "").param("allowCreateDirectory", "true")
                            .param("entrypointRelativePath", "")
                            .header("X-Upload-Token", "t"))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.message").value(containsString("여러 개")));
        }

        @Test
        @DisplayName("12. 빈 업로드 → 409 EmptyBundle")
        void emptyBundle() throws Exception {
            given(biosUploadIntentService.consume(eq(1L), anyString()))
                    .willReturn(new BiosUploadIntentService.Intent(
                            1L, "/mnt/x", BiosUploadMode.FOLDER, 0, 0L, "1.0", "", Instant.now()));
            willThrow(new EmptyBundleException())
                    .given(biosRegistrationService).addBios(eq(1L), any(), any(), any(), any(), any());

            mvc.perform(multipart("/management/bios/1/upload")
                            .param("uploadMode", "FOLDER")
                            .param("name", "x").param("version", "1.0")
                            .param("targetDirectory", "/mnt/x")
                            .param("description", "").param("allowCreateDirectory", "true")
                            .param("entrypointRelativePath", "")
                            .header("X-Upload-Token", "t"))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.message").value(containsString("비어")));
        }

        @Test
        @DisplayName("13. I/O 실패 → 500 BundleExtraction")
        void ioFailure() throws Exception {
            given(biosUploadIntentService.consume(eq(1L), anyString()))
                    .willReturn(new BiosUploadIntentService.Intent(
                            1L, "/mnt/x", BiosUploadMode.ZIP, 1, 100L, "1.0", "", Instant.now()));
            willThrow(new BundleExtractionException("디스크 가득"))
                    .given(biosRegistrationService).addBios(eq(1L), any(), any(), any(), any(), any());

            mvc.perform(multipart("/management/bios/1/upload")
                            .file(new MockMultipartFile("zipFile", "p.zip", null, "x".getBytes()))
                            .param("uploadMode", "ZIP")
                            .param("name", "x").param("version", "1.0")
                            .param("targetDirectory", "/mnt/x")
                            .param("description", "").param("allowCreateDirectory", "true")
                            .param("entrypointRelativePath", "")
                            .header("X-Upload-Token", "t"))
                    .andExpect(status().isInternalServerError())
                    .andExpect(jsonPath("$.message").value(containsString("디스크")));
        }
    }

    // =========== Verify + CRUD 4 시나리오 ===========

    @Nested
    @DisplayName("Verify / CRUD")
    class VerifyAndCrud {

        @Test
        @DisplayName("14. verify — 200 + jobId (실제 검증은 BackgroundJob 으로 비동기 위임)")
        void verify_returnsJobId() throws Exception {
            given(biosVerificationLauncher.startVerification(1L, 5L)).willReturn("job-bios-5");
            mvc.perform(post("/management/bios/1/bios/5/verify"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.jobId").value("job-bios-5"));
        }

        @Test
        @DisplayName("15. integrity-status — 200 + status/badgeClass")
        void integrityStatus_returnsBody() throws Exception {
            given(biosIntegrityService.findIntegrityStatus(1L, 5L))
                    .willReturn(IntegrityStatusResponse.of(5L, IntegrityStatus.ORIGINAL, null));

            mvc.perform(get("/management/bios/1/bios/5/integrity-status"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.resourceId").value(5))
                    .andExpect(jsonPath("$.integrityStatus").value("ORIGINAL"))
                    .andExpect(jsonPath("$.badgeClass").value("n-badge-green"));
        }

        @Test
        @DisplayName("16. toggle — 삭제된 BIOS → 409 IllegalBiosState (HTML 에러 뷰)")
        void toggle_onDeleted() throws Exception {
            willThrow(new IllegalBiosStateException("삭제된 BIOS"))
                    .given(biosLifecycleService).toggleEnabled(anyLong());

            mvc.perform(post("/management/bios/1/bios/5/toggle"))
                    .andExpect(status().isConflict());
        }

        @Test
        @DisplayName("17. delete — 없는 BIOS → 404 BiosNotFound (assertBelongsToBoard 가 차단)")
        void delete_notFound() throws Exception {
            // R4-3 — controller 가 lifecycle 호출 직전 assertBelongsToBoard 로 entity 부재/부모 mismatch 차단.
            willThrow(new BiosNotFoundException(1L, 999L))
                    .given(biosLifecycleService).assertBelongsToBoard(eq(999L), eq(1L));

            mvc.perform(post("/management/bios/1/bios/999/delete"))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("17b. toggle — URL forging (다른 board 의 BIOS) → 404 via assertBelongsToBoard")
        void toggle_forgedBoardId_returns404() throws Exception {
            // 사용자가 boardId=99 로 URL 을 forging 했지만 biosId=5 의 실제 부모는 다른 board.
            willThrow(new BiosNotFoundException(99L, 5L))
                    .given(biosLifecycleService).assertBelongsToBoard(eq(5L), eq(99L));

            mvc.perform(post("/management/bios/99/bios/5/toggle"))
                    .andExpect(status().isNotFound());
        }
    }

    // =========== MK2 WAVE 2 — Intent (단계 A) Nudge 4 시나리오 ===========

    @Nested
    @DisplayName("MK2 WAVE 2 — Intent Nudge")
    class IntentNudge {

        @Test
        @DisplayName("18. intent : 메타 충돌 → 409 NUDGE_REQUIRED + nudgeId/conflicts 동봉")
        void intentMetaNudge() throws Exception {
            var req = new BiosUploadIntentRequest("/mnt/x", BiosUploadMode.FOLDER, 5, 1024, "2.03", false, "");
            java.util.UUID nudgeId = java.util.UUID.randomUUID();
            var session = new com.example.serverprovision.management.common.nudge.NudgeSession(
                    nudgeId,
                    com.example.serverprovision.management.common.nudge.NudgeResourceType.BIOS,
                    1L,
                    List.of(42L),
                    new com.example.serverprovision.management.common.nudge.IntentMetaNudgePayload(java.util.Map.of()),
                    Instant.now(), Instant.now().plusSeconds(300));
            var conflicts = List.of(new com.example.serverprovision.management.common.nudge.dto.NudgeConflictEntry(
                    42L,
                    com.example.serverprovision.global.lifecycle.LifecycleStage.SOFT_DELETED,
                    "abc", "BIOS-A", "2.03", Instant.now()));
            willThrow(new com.example.serverprovision.management.bios.exception.BiosNudgeRequiredException(
                    "동일 메타", session, conflicts))
                    .given(biosUploadIntentService).issue(eq(1L), any());

            mvc.perform(post("/management/bios/1/upload-intent")
                            .contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsString(req)))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("NUDGE_REQUIRED"))
                    .andExpect(jsonPath("$.nudgeId").value(nudgeId.toString()))
                    .andExpect(jsonPath("$.conflicts[0].id").value(42));
        }

        @Test
        @DisplayName("19. intent-nudge proceed : 200 + 새 uploadToken")
        void intentNudgeProceed() throws Exception {
            java.util.UUID nudgeId = java.util.UUID.randomUUID();
            given(biosNudgeService.proceedIntent(eq(nudgeId)))
                    .willReturn(new BiosUploadIntentResponse("token-new", List.of(), null));

            mvc.perform(post("/management/bios/intent-nudge/" + nudgeId + "/proceed"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.uploadToken").value("token-new"));
        }

        @Test
        @DisplayName("20. intent-nudge replace : 200 + 새 uploadToken (targetId purge 후)")
        void intentNudgeReplace() throws Exception {
            java.util.UUID nudgeId = java.util.UUID.randomUUID();
            given(biosNudgeService.replaceIntent(eq(nudgeId), eq(42L)))
                    .willReturn(new BiosUploadIntentResponse("token-after-replace", List.of(), null));

            mvc.perform(post("/management/bios/intent-nudge/" + nudgeId + "/replace?targetId=42"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.uploadToken").value("token-after-replace"));
        }

        @Test
        @DisplayName("21. intent-nudge cancel : 204 NoContent")
        void intentNudgeCancel() throws Exception {
            java.util.UUID nudgeId = java.util.UUID.randomUUID();

            mvc.perform(post("/management/bios/intent-nudge/" + nudgeId + "/cancel"))
                    .andExpect(status().isNoContent());
        }
    }
}
