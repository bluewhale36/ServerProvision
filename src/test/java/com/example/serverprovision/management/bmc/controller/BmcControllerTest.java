package com.example.serverprovision.management.bmc.controller;

import com.example.serverprovision.management.common.filesystem.exception.TargetDirectoryNotEmptyException;
import com.example.serverprovision.global.marker.IntegrityStatus;
import com.example.serverprovision.management.common.dto.response.IntegrityStatusResponse;
import com.example.serverprovision.management.common.filesystem.dto.DirectoryListingResponse;
import com.example.serverprovision.management.common.filesystem.exception.BrowseTargetNotDirectoryException;
import com.example.serverprovision.management.common.filesystem.exception.BrowseTargetNotFoundException;
import com.example.serverprovision.management.common.filesystem.exception.DirectoryBrowseIoException;
import com.example.serverprovision.management.common.filesystem.service.DirectoryBrowseService;
import com.example.serverprovision.management.bmc.dto.request.BmcUploadIntentRequest;
import com.example.serverprovision.management.bmc.dto.response.BmcResponse;
import com.example.serverprovision.management.bmc.dto.response.BmcUploadIntentResponse;
import com.example.serverprovision.management.bmc.exception.BmcNotFoundException;
import com.example.serverprovision.management.bmc.exception.DuplicateBmcVersionException;
import com.example.serverprovision.management.bmc.service.BmcService;
import com.example.serverprovision.management.bmc.service.BmcUploadIntentService;
import com.example.serverprovision.management.bmc.service.BmcVerificationLauncher;
import com.example.serverprovision.management.board.dto.response.BoardModelResponse;
import com.example.serverprovision.management.board.enums.Vendor;
import com.example.serverprovision.management.board.exception.BoardModelNotFoundException;
import com.example.serverprovision.management.board.service.metadata.BoardModelMetadataService;
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
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@WebMvcTest(controllers = {
        BmcMetadataController.class,
        BmcUploadController.class,
        BmcNudgeController.class,
        BmcJobController.class,
        com.example.serverprovision.management.common.filesystem.controller.DirectoryBrowseController.class
})
class BmcControllerTest {
    @org.springframework.test.context.bean.override.mockito.MockitoBean com.example.serverprovision.global.trash.service.TypedNameVerifier typedNameVerifier;

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper om;

    @MockitoBean BmcService bmcService;
    @MockitoBean com.example.serverprovision.management.bmc.service.BmcFirmwareFilePolicy bmcFirmwareFilePolicy;
    @MockitoBean com.example.serverprovision.management.bmc.service.BmcRegistrationService bmcRegistrationService;
    @MockitoBean com.example.serverprovision.management.bmc.service.BmcIntegrityService bmcIntegrityService;
    @MockitoBean BmcUploadIntentService bmcUploadIntentService;
    @MockitoBean com.example.serverprovision.management.bmc.service.BmcNudgeService bmcNudgeService;
    @MockitoBean BoardModelMetadataService boardModelService;
    @MockitoBean BmcVerificationLauncher bmcVerificationLauncher;
    @MockitoBean DirectoryBrowseService directoryBrowseService;
    @MockitoBean JpaMetamodelMappingContext jpaMetamodelMappingContext;

    @Test
    @DisplayName("GET /{boardId}/new : 신규 폼 렌더")
    void newForm() throws Exception {
        given(boardModelService.findById(1L))
                .willReturn(new BoardModelResponse(1L, Vendor.GIGABYTE, "MS03", "", 0, 0, 0, true, false, false, com.example.serverprovision.global.lifecycle.LifecycleStage.ACTIVE, null));

        mvc.perform(get("/management/bmc/1/new"))
                .andExpect(status().isOk())
                .andExpect(view().name("management/bmc/bmc-new"));
    }

    @Nested
    @DisplayName("POST /{boardId}/upload-intent")
    class Intent {

        @Test
        @DisplayName("정상 — 200 + uploadToken")
        void success() throws Exception {
            var req = new BmcUploadIntentRequest("/mnt/bmc/x/", "bmc.ima_enc", 1024L, "13.06.25", false);
            given(bmcUploadIntentService.issue(eq(1L), any()))
                    .willReturn(new BmcUploadIntentResponse("token-abc", List.of(), null));

            mvc.perform(post("/management/bmc/1/upload-intent")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(om.writeValueAsString(req)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.uploadToken").value("token-abc"));
        }

        @Test
        @DisplayName("펌웨어 파일 경로 공란 → 400")
        void blankFirmwarePath() throws Exception {
            String body = "{\"firmwarePath\":\"\",\"fileName\":\"bmc.ima_enc\",\"fileSize\":1024,\"version\":\"13.06.25\",\"allowCreateDirectory\":false}";
            mvc.perform(post("/management/bmc/1/upload-intent")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value(containsString("펌웨어 파일 경로")));   // S22-4 — 필드 이름(firmwarePath)은 화면에 싣지 않는다
        }
    }

    @Nested
    @DisplayName("POST /{boardId}/upload")
    class Upload {

        @Test
        @DisplayName("업로드 성공 — 토큰 소비 + 200 + id/redirect")
        void success_upload() throws Exception {
            given(bmcUploadIntentService.consume(eq(1L), eq("token-abc")))
                    .willReturn(new BmcUploadIntentService.Intent(
                            1L, "/mnt/bmc/x/", "bmc.ima_enc", 1024L, "13.06.25", Instant.now()));
            given(bmcRegistrationService.addBmc(eq(1L), any(), any()))
                    .willReturn(42L);

            mvc.perform(multipart("/management/bmc/1/upload")
                            .file(new MockMultipartFile("firmwareFile", "bmc.ima_enc", null, "x".getBytes()))
                            .param("name", "GIGABYTE BMC")
                            .param("version", "13.06.25")
                            .param("firmwarePath", "/mnt/bmc/x/")
                            .param("description", "")
                            .param("allowCreateDirectory", "true")
                            .header("X-Upload-Token", "token-abc"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(42))
                    .andExpect(jsonPath("$.redirect").value("/management/bmc?selectId=42"));
        }

        @Test
        @DisplayName("대상 디렉토리 충돌 → 409")
        void targetConflict() throws Exception {
            given(bmcUploadIntentService.consume(eq(1L), anyString()))
                    .willReturn(new BmcUploadIntentService.Intent(
                            1L, "/mnt/bmc/x/", "bmc.ima_enc", 10L, "1.0", Instant.now()));
            willThrow(new TargetDirectoryNotEmptyException("/mnt/bmc/x"))
                    .given(bmcRegistrationService).addBmc(eq(1L), any(), any());

            mvc.perform(multipart("/management/bmc/1/upload")
                            .file(new MockMultipartFile("firmwareFile", "bmc.ima_enc", null, "x".getBytes()))
                            .param("name", "x")
                            .param("version", "1.0")
                            .param("firmwarePath", "/mnt/bmc/x/")
                            .param("description", "")
                            .param("allowCreateDirectory", "true")
                            .header("X-Upload-Token", "t"))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.message").value(containsString("비어있지 않습니다")));
        }
    }

    @Test
    @DisplayName("GET /{boardId}/bmc/{bmcId}/edit : 수정 폼 렌더")
    void editForm() throws Exception {
        given(boardModelService.findById(1L))
                .willReturn(new BoardModelResponse(1L, Vendor.GIGABYTE, "MS03", "", 0, 0, 0, true, false, false, com.example.serverprovision.global.lifecycle.LifecycleStage.ACTIVE, null));
        given(bmcService.findBmc(1L, 2L))
                .willReturn(new BmcResponse(2L, 1L, "AST2600", "12.61", "/opt/fw/bmc", "flash.nsh", "hash", 3, 2048L,
                        "", IntegrityStatus.NOT_VERIFIED, true, false, false, false, false, false));

        mvc.perform(get("/management/bmc/1/bmc/2/edit"))
                .andExpect(status().isOk())
                .andExpect(view().name("management/bmc/bmc-edit"));
    }

    @Test
    @DisplayName("POST /{boardId}/bmc/{bmcId}/verify : jobId 반환")
    void verify() throws Exception {
        given(bmcVerificationLauncher.startVerification(1L, 2L)).willReturn("job-bmc-1");

        mvc.perform(post("/management/bmc/1/bmc/2/verify"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.jobId").value("job-bmc-1"));
    }

    @Test
    @DisplayName("GET /{boardId}/bmc/{bmcId}/integrity-status : 200 + status/badgeClass")
    void integrityStatus() throws Exception {
        given(bmcIntegrityService.findIntegrityStatus(1L, 2L))
                .willReturn(IntegrityStatusResponse.of(2L, IntegrityStatus.TAMPERED, null));

        mvc.perform(get("/management/bmc/1/bmc/2/integrity-status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resourceId").value(2))
                .andExpect(jsonPath("$.integrityStatus").value("TAMPERED"))
                .andExpect(jsonPath("$.label").value("변조 감지(해시 불일치)"))
                .andExpect(jsonPath("$.badgeClass").value("n-badge-red"));
    }

    @Test
    @DisplayName("S5-1 — GET /browse : path 누락 시 200 + 첫 allowed-root listing (서비스 본체 위임)")
    void browse_returnsFirstRoot_when_pathOmitted() throws Exception {
        given(directoryBrowseService.browse(any()))
                .willReturn(new DirectoryListingResponse(
                        "/opt/bmc", null,
                        List.of(DirectoryListingResponse.Entry.directory("VENDOR_A"))));

        mvc.perform(get("/management/browse"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.path").value("/opt/bmc"))
                .andExpect(jsonPath("$.entries[0].name").value("VENDOR_A"));
    }

    @Test
    @DisplayName("GET /browse : 정상 경로면 200 JSON")
    void browse_success() throws Exception {
        given(directoryBrowseService.browse(any()))
                .willReturn(new DirectoryListingResponse(
                        "/opt/bmc", "/opt",
                        List.of(DirectoryListingResponse.Entry.directory("GIGABYTE"))));

        mvc.perform(get("/management/browse").param("path", "/opt/bmc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.path").value("/opt/bmc"))
                .andExpect(jsonPath("$.entries[0].type").value("DIR"))
                .andExpect(jsonPath("$.entries[0].name").value("GIGABYTE"));
    }

    @Test
    @DisplayName("GET /browse : 존재하지 않는 경로면 404 JSON")
    void browse_notFound() throws Exception {
        willThrow(new BrowseTargetNotFoundException("/no/such/path"))
                .given(directoryBrowseService).browse(any());

        mvc.perform(get("/management/browse").param("path", "/no/such/path"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value(containsString("경로를 찾을 수 없습니다")));
    }

    @Test
    @DisplayName("GET /browse : 파일 경로면 409 JSON")
    void browse_notDirectory() throws Exception {
        willThrow(new BrowseTargetNotDirectoryException("/opt/bmc/fw.bin"))
                .given(directoryBrowseService).browse(any());

        mvc.perform(get("/management/browse").param("path", "/opt/bmc/fw.bin"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(containsString("디렉토리가 아닙니다")));
    }

    @Test
    @DisplayName("GET /browse : IO 예외면 500 JSON")
    void browse_ioError() throws Exception {
        willThrow(new DirectoryBrowseIoException("io fail", new RuntimeException("x")))
                .given(directoryBrowseService).browse(any());

        mvc.perform(get("/management/browse").param("path", "/opt/bmc"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.message").value(containsString("io fail")));
    }

    @Test
    @DisplayName("GET /{boardId}/new : 없는 boardId 면 404")
    void newForm_boardNotFound() throws Exception {
        willThrow(new BoardModelNotFoundException(999L)).given(boardModelService).findById(999L);

        mvc.perform(get("/management/bmc/999/new"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("GET /{boardId}/bmc/{bmcId}/edit : 없는 bmcId 면 404")
    void editForm_bmcNotFound() throws Exception {
        willThrow(new BmcNotFoundException(1L, 99L)).given(bmcService).findBmc(1L, 99L);

        mvc.perform(get("/management/bmc/1/bmc/99/edit"))
                .andExpect(status().isNotFound());
    }

    // =========== MK2 WAVE 2 — Intent (단계 A) Nudge 4 시나리오 ===========

    @Nested
    @DisplayName("MK2 WAVE 2 — Intent Nudge")
    class IntentNudge {

        @Test
        @DisplayName("intent : 메타 충돌 → 409 NUDGE_REQUIRED")
        void intentMetaNudge() throws Exception {
            var req = new BmcUploadIntentRequest("/mnt/x/", "bmc.ima_enc", 1024L, "12.61", false);
            java.util.UUID nudgeId = java.util.UUID.randomUUID();
            var session = new com.example.serverprovision.management.common.nudge.NudgeSession(
                    nudgeId,
                    com.example.serverprovision.management.common.nudge.NudgeResourceType.BMC,
                    1L,
                    List.of(77L),
                    new com.example.serverprovision.management.common.nudge.IntentMetaNudgePayload(java.util.Map.of()),
                    Instant.now(), Instant.now().plusSeconds(300));
            var conflicts = List.of(new com.example.serverprovision.management.common.nudge.dto.NudgeConflictEntry(
                    77L,
                    com.example.serverprovision.global.lifecycle.LifecycleStage.SOFT_DELETED,
                    "hash", "BMC-A", "12.61", "BMC-A", "버전 12.61", Instant.now()));
            willThrow(new com.example.serverprovision.management.bmc.exception.BmcNudgeRequiredException(
                    "동일 메타", session, conflicts))
                    .given(bmcUploadIntentService).issue(eq(1L), any());

            mvc.perform(post("/management/bmc/1/upload-intent")
                            .contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsString(req)))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("NUDGE_REQUIRED"))
                    .andExpect(jsonPath("$.nudgeId").value(nudgeId.toString()))
                    .andExpect(jsonPath("$.conflicts[0].id").value(77));
        }

        @Test
        @DisplayName("intent-nudge proceed : 200 + 새 uploadToken")
        void intentNudgeProceed() throws Exception {
            java.util.UUID nudgeId = java.util.UUID.randomUUID();
            given(bmcNudgeService.proceedIntent(eq(nudgeId)))
                    .willReturn(new BmcUploadIntentResponse("token-new", List.of(), null));

            mvc.perform(post("/management/bmc/intent-nudge/" + nudgeId + "/proceed"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.uploadToken").value("token-new"));
        }

        @Test
        @DisplayName("intent-nudge replace : 200 + 새 uploadToken")
        void intentNudgeReplace() throws Exception {
            java.util.UUID nudgeId = java.util.UUID.randomUUID();
            given(bmcNudgeService.replaceIntent(eq(nudgeId), eq(77L), any()))
                    .willReturn(new BmcUploadIntentResponse("token-after-replace", List.of(), null));

            mvc.perform(post("/management/bmc/intent-nudge/" + nudgeId + "/replace?targetId=77"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.uploadToken").value("token-after-replace"));
        }

        @Test
        @DisplayName("intent-nudge cancel : 204 NoContent")
        void intentNudgeCancel() throws Exception {
            java.util.UUID nudgeId = java.util.UUID.randomUUID();

            mvc.perform(post("/management/bmc/intent-nudge/" + nudgeId + "/cancel"))
                    .andExpect(status().isNoContent());
        }
    }

    @org.junit.jupiter.api.Nested
    @DisplayName("HF30-2 — 상태별 409 안내 · [기존 영구 삭제 후 등록] 실패 응답")
    class Hf30_2 {

        @Test
        @DisplayName("upload-intent : 같은 정체가 사용 중단 권고 → 409 + 해제 안내 문구")
        void intent_deprecated_409() throws Exception {
            willThrow(com.example.serverprovision.management.bmc.exception.DuplicateBmcVersionException.againstRegistration(com.example.serverprovision.management.bmc.entity.BoardBMC.builder().name("fw").version("13.06.25").isDeprecated(true).isDeleted(false).build()))
                    .given(bmcUploadIntentService).issue(eq(1L), any());

            mvc.perform(post("/management/bmc/1/upload-intent")
                            .contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsString(new com.example.serverprovision.management.bmc.dto.request.BmcUploadIntentRequest("/mnt/bmc/x/", "bmc.ima_enc", 1024L, "13.06.25", false))))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.message").value("사용 중단 권고 상태인 같은 BMC 버전이 있습니다(fw 13.06.25). 목록에서 사용 중단 권고를 해제해야 합니다."))
                    .andExpect(jsonPath("$.fieldErrors[0].field").value("version"));
        }

        @Test
        @DisplayName("intent-nudge replace : 자원명을 서비스로 넘긴다(자원명 확인은 영구 삭제 경로가 한다)")
        void intentNudgeReplace_passesTypedName() throws Exception {
            java.util.UUID nudgeId = java.util.UUID.randomUUID();
            given(bmcNudgeService.replaceIntent(eq(nudgeId), eq(42L), eq("typed")))
                    .willReturn(new com.example.serverprovision.management.bmc.dto.response.BmcUploadIntentResponse("t", List.of(), null));

            mvc.perform(post("/management/bmc/intent-nudge/" + nudgeId + "/replace?targetId=42&typedName=typed"))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("intent-nudge replace : 후보가 이미 휴지통에 없음 → 409(PurgeTargetNotInTrash)")
        void intentNudgeReplace_notInTrash_409() throws Exception {
            java.util.UUID nudgeId = java.util.UUID.randomUUID();
            willThrow(new com.example.serverprovision.global.trash.exception.PurgeTargetNotInTrashException())
                    .given(bmcNudgeService).replaceIntent(eq(nudgeId), eq(42L), any());

            mvc.perform(post("/management/bmc/intent-nudge/" + nudgeId + "/replace?targetId=42"))
                    .andExpect(status().isConflict());
        }

        @Test
        @DisplayName("intent-nudge replace : 영구 삭제 뒤 등록 실패 → 409 + 영구 삭제 사실과 실패 사유")
        void intentNudgeReplace_registrationFailedAfterPurge_409() throws Exception {
            java.util.UUID nudgeId = java.util.UUID.randomUUID();
            willThrow(com.example.serverprovision.management.common.nudge.exception.NudgeReplaceRegistrationFailedException
                    .wrapIfUserFacing(new com.example.serverprovision.management.board.exception.BoardModelNotFoundException(1L)))
                    .given(bmcNudgeService).replaceIntent(eq(nudgeId), eq(42L), any());

            mvc.perform(post("/management/bmc/intent-nudge/" + nudgeId + "/replace?targetId=42"))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.startsWith("기존 자원은 영구 삭제했지만 새 자원 등록에 실패했습니다. ")));
        }
    }

    @org.junit.jupiter.api.Nested
    @DisplayName("HF30-3 — 업로드 뒤 창 성공 응답 · targetId · 자원명 · 코드 응답")
    class Hf30_3 {

        private final java.util.UUID nudgeId = java.util.UUID.randomUUID();

        @Test
        @DisplayName("nudge proceed : 일반 등록과 같은 {id, redirect}(D11, 종전 204)")
        void proceed_returnsUploadResponse() throws Exception {
            given(bmcNudgeService.proceed(nudgeId)).willReturn(31L);

            mvc.perform(post("/management/bmc/nudge/" + nudgeId + "/proceed"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(31))
                    .andExpect(jsonPath("$.redirect").value("/management/bmc?selectId=31"));
        }

        @Test
        @DisplayName("nudge replace : targetId 와 본문의 typedName 을 서비스로 넘긴다(F-1, 종전 replaceTargetId 요구로 항상 400)")
        void replace_acceptsTargetIdAndBodyTypedName() throws Exception {
            given(bmcNudgeService.replace(nudgeId, 42L, "fw")).willReturn(32L);

            mvc.perform(post("/management/bmc/nudge/" + nudgeId + "/replace")
                            .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                            .param("targetId", "42").param("typedName", "fw"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.redirect").value("/management/bmc?selectId=32"));
        }

        @Test
        @DisplayName("nudge replace : 자원명 불일치(빈 값 포함) → 400(D12)")
        void replace_typedNameMismatch_400() throws Exception {
            willThrow(new com.example.serverprovision.global.exception.TypedNameMismatchException("fw", null))
                    .given(bmcNudgeService).replace(eq(nudgeId), eq(42L), any());

            mvc.perform(post("/management/bmc/nudge/" + nudgeId + "/replace").param("targetId", "42"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value("입력한 자원명이 일치하지 않습니다."));
        }

        @Test
        @DisplayName("nudge replace : 영구 삭제 뒤 등록 실패 → 409 + code NUDGE_REPLACE_REGISTRATION_FAILED(D16)")
        void replace_registrationFailed_carriesCode() throws Exception {
            willThrow(com.example.serverprovision.management.common.nudge.exception.NudgeReplaceRegistrationFailedException
                    .wrapIfUserFacing(new com.example.serverprovision.management.board.exception.BoardModelNotFoundException(1L)))
                    .given(bmcNudgeService).replace(eq(nudgeId), eq(42L), any());

            mvc.perform(post("/management/bmc/nudge/" + nudgeId + "/replace").param("targetId", "42").param("typedName", "fw"))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("NUDGE_REPLACE_REGISTRATION_FAILED"))
                    .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.startsWith("기존 자원은 영구 삭제했지만 새 자원 등록에 실패했습니다. ")));
        }

        @Test
        @DisplayName("nudge proceed : 만료 응답은 공용 핸들러 뒤에도 같은 모양(409, code, message)")
        void proceed_expired_sameShape() throws Exception {
            willThrow(new com.example.serverprovision.management.common.nudge.exception.NudgeSessionExpiredException(nudgeId))
                    .given(bmcNudgeService).proceed(nudgeId);

            mvc.perform(post("/management/bmc/nudge/" + nudgeId + "/proceed"))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("NUDGE_SESSION_EXPIRED"))
                    .andExpect(jsonPath("$.message").value("중복 확인이 만료되었습니다. 다시 업로드해야 합니다."));
        }

        @Test
        @DisplayName("nudge cancel : 같은 창을 다른 요청이 처리 중 → 409(해시 계산 중 트리 보호)")
        void cancel_inProgress_409() throws Exception {
            willThrow(new com.example.serverprovision.management.common.nudge.exception.NudgeInProgressException())
                    .given(bmcNudgeService).cancel(nudgeId);

            mvc.perform(post("/management/bmc/nudge/" + nudgeId + "/cancel"))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.message").value("이 중복 확인을 다른 요청이 처리하고 있습니다. 잠시 뒤 목록에서 결과를 확인해야 합니다."));
        }
    }
}
