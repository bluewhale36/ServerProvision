package com.example.serverprovision.management.os.controller;

import com.example.serverprovision.management.common.filesystem.dto.DirectoryListingResponse;
import com.example.serverprovision.management.common.filesystem.exception.InvalidBrowsePathException;
import com.example.serverprovision.management.common.filesystem.service.DirectoryBrowseService;
import com.example.serverprovision.management.bios.vo.IntegrityStatus;
import com.example.serverprovision.management.common.dto.response.IntegrityStatusResponse;
import com.example.serverprovision.management.os.dto.request.IsoUploadIntentRequest;
import com.example.serverprovision.management.os.dto.response.IsoUploadIntentResponse;
import com.example.serverprovision.management.os.exception.AlreadyExtractedException;
import com.example.serverprovision.management.os.exception.DirectoryMissingException;
import com.example.serverprovision.management.os.exception.DuplicateISOContentException;
import com.example.serverprovision.management.os.exception.ISONotFoundException;
import com.example.serverprovision.management.os.exception.InvalidIsoPathException;
import com.example.serverprovision.management.os.exception.InvalidUploadTokenException;
import com.example.serverprovision.management.os.exception.DuplicateFilenameException;
import com.example.serverprovision.management.os.exception.IsoUploadIntentConflictException;
import com.example.serverprovision.management.os.exception.OSMetadataNotFoundException;
import com.example.serverprovision.management.os.service.OSNudgeService;
import com.example.serverprovision.management.os.service.comps.CompsExtractionLauncher;
import com.example.serverprovision.management.os.service.iso.IsoRegistrationLauncher;
import com.example.serverprovision.management.os.service.iso.IsoUploadIntentService;
import com.example.serverprovision.management.os.service.iso.IsoVerificationLauncher;
import com.example.serverprovision.management.os.service.metadata.OSMetadataService;
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

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 사용자가 웹 화면에서 수행하는 대표 시나리오들을 HTTP 계층에서 검증한다.
 * <p>목적 : Service 단 mock 만으로는 "예외 → HTTP status" 매핑 사고 (500 으로 새는 경우 등) 가 드러나지 않는다.
 * 컨트롤러의 try/catch 분기 + @ControllerAdvice 의 매핑을 함께 실행해 실제 응답 status · body 를 확인한다.</p>
 */
@WebMvcTest(controllers = { IsoUploadController.class, IsoJobController.class, OSBrowseController.class, IsoNudgeController.class })
class OSMetadataControllerUploadFlowTest {
    @org.springframework.test.context.bean.override.mockito.MockitoBean com.example.serverprovision.global.trash.service.TypedNameVerifier typedNameVerifier;

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper om;

    @MockitoBean OSMetadataService osMetadataService;
    @MockitoBean com.example.serverprovision.management.os.service.metadata.OSMetadataLifecycleService osMetadataLifecycleService;
    @MockitoBean CompsExtractionLauncher compsExtractionLauncher;
    @MockitoBean IsoUploadIntentService isoUploadIntentService;
    @MockitoBean IsoVerificationLauncher isoVerificationLauncher;
    @MockitoBean IsoRegistrationLauncher isoRegistrationLauncher;
    @MockitoBean DirectoryBrowseService directoryBrowseService;
    @MockitoBean OSNudgeService osNudgeService;
    @MockitoBean com.example.serverprovision.management.os.service.metadata.OSMetadataNudgeService osMetadataNudgeService;
    @MockitoBean com.example.serverprovision.global.lifecycle.DeleteIntentRegistry deleteIntentRegistry;
    // @EnableJpaAuditing 이 main class 에 있어 WebMvcTest 부팅 시 jpaMappingContext 를 요구한다.
    // 실제 JPA metamodel 은 slice 에서 필요 없으므로 mock 으로 대체.
    @MockitoBean JpaMetamodelMappingContext jpaMetamodelMappingContext;

    @Test
    @DisplayName("GET /browse : includeFiles=true 정상 경로면 200 JSON")
    void browse_success_includeFiles() throws Exception {
        given(directoryBrowseService.browse(any()))
                .willReturn(new DirectoryListingResponse(
                        "/opt/iso", "/opt",
                        java.util.List.of(DirectoryListingResponse.Entry.file("dvd.iso", 1024L))));

        mvc.perform(get("/management/os/browse")
                        .param("path", "/opt/iso")
                        .param("includeFiles", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.path").value("/opt/iso"))
                .andExpect(jsonPath("$.entries[0].type").value("FILE"))
                .andExpect(jsonPath("$.entries[0].name").value("dvd.iso"))
                .andExpect(jsonPath("$.entries[0].size").value(1024));
    }

    @Test
    @DisplayName("GET /browse : invalid path 면 400 JSON")
    void browse_invalidPath() throws Exception {
        willThrow(new InvalidBrowsePathException("bad"))
                .given(directoryBrowseService).browse(any());

        mvc.perform(get("/management/os/browse").param("path", "bad"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("경로 형식")));
    }

    // ========= Intent 핸드셰이크 시나리오 =========

    @Nested
    @DisplayName("POST /{osId}/iso/upload-intent")
    class Intent {

        @Test
        @DisplayName("정상 경로 — 200 + uploadToken")
        void success() throws Exception {
            var req = new IsoUploadIntentRequest("/mnt/iso/dvd.iso", "dvd.iso", 1024L, false);
            given(isoUploadIntentService.issue(eq(1L), any()))
                    .willReturn(new IsoUploadIntentResponse.IntentTokenIssued("token-abc", java.util.List.of()));

            mvc.perform(post("/management/os/1/iso/upload-intent")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(om.writeValueAsString(req)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.uploadToken").value("token-abc"));
        }

        @Test
        @DisplayName("isoPath 비어있음 → 400 + 필드 메시지")
        void blankIsoPath_returns400() throws Exception {
            // @NotBlank 실패. BindingResult 로 처리되어 controller 가 ApiErrorResponse 반환.
            String body = "{\"isoPath\":\"\",\"filename\":\"dvd.iso\",\"size\":100,\"allowCreateDirectory\":false}";
            mvc.perform(post("/management/os/1/iso/upload-intent")
                            .contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value(containsString("isoPath")));
        }

        @Test
        @DisplayName("같은 경로에 이미 등록된 ISO → 409 Conflict")
        void existingPath_returns409() throws Exception {
            var req = new IsoUploadIntentRequest("/mnt/iso/dvd.iso", "dvd.iso", 1L, false);
            willThrow(new IsoUploadIntentConflictException("같은 경로에 이미 등록된 ISO 가 있습니다 : /mnt/iso/dvd.iso"))
                    .given(isoUploadIntentService).issue(eq(1L), any());

            mvc.perform(post("/management/os/1/iso/upload-intent")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(om.writeValueAsString(req)))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.message").value(containsString("이미 등록된")));
        }

        @Test
        @DisplayName("상위 디렉토리 없음 + 체크 해제 → 409 Conflict")
        void directoryMissing_returns409() throws Exception {
            var req = new IsoUploadIntentRequest("/nope/a/b/dvd.iso", "dvd.iso", 1L, false);
            willThrow(new DirectoryMissingException("/nope/a/b"))
                    .given(isoUploadIntentService).issue(eq(1L), any());

            mvc.perform(post("/management/os/1/iso/upload-intent")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(om.writeValueAsString(req)))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.message").value(containsString("디렉토리 생성 허용")));
        }

        @Test
        @DisplayName("파일시스템에 동일 이름 파일 실재 → 409 Conflict")
        void fileAlreadyExists_returns409() throws Exception {
            var req = new IsoUploadIntentRequest("/mnt/iso/dvd.iso", "dvd.iso", 1L, false);
            willThrow(new DuplicateFilenameException("/mnt/iso/dvd.iso"))
                    .given(isoUploadIntentService).issue(eq(1L), any());

            mvc.perform(post("/management/os/1/iso/upload-intent")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(om.writeValueAsString(req)))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.message").value(containsString("이미 같은 이름의 파일")));
        }

        @Test
        @DisplayName("존재하지 않는 OSMetadata → 404 Not Found")
        void unknownOs_returns404() throws Exception {
            var req = new IsoUploadIntentRequest("/mnt/iso/dvd.iso", "dvd.iso", 1L, false);
            willThrow(new OSMetadataNotFoundException(999L))
                    .given(isoUploadIntentService).issue(eq(999L), any());

            mvc.perform(post("/management/os/999/iso/upload-intent")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(om.writeValueAsString(req)))
                    .andExpect(status().isNotFound());
        }
    }

    // ========= Upload 실제 전송 시나리오 =========

    @Nested
    @DisplayName("POST /{osId}/iso/upload")
    class Upload {

        MockMultipartFile buildFile() {
            return new MockMultipartFile("file", "dvd.iso", "application/octet-stream", new byte[] {1, 2, 3});
        }

        @Test
        @DisplayName("정상 경로 — 200 + jobId/redirect 필드")
        void success() throws Exception {
            given(osMetadataService.prepareIsoRegistration(eq(1L), any(), any(), any()))
                    .willReturn(new OSMetadataService.PreparedIsoRegistration(
                            1L, "/mnt/iso/dvd.iso", "", "dvd.iso", true));
            given(isoRegistrationLauncher.startRegistration(any())).willReturn("job-iso-1");

            mvc.perform(multipart("/management/os/1/iso/upload")
                            .file(buildFile())
                            .param("isoPath", "/mnt/iso/dvd.iso")
                            .param("description", "")
                            .param("allowCreateDirectory", "false")
                            .header("X-Upload-Token", "valid-token"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.jobId").value("job-iso-1"))
                    .andExpect(jsonPath("$.redirect").value(containsString("selectId=1")));
        }

        @Test
        @DisplayName("토큰 없음 → 409 Conflict (InvalidUploadTokenException)")
        void missingToken_returns409() throws Exception {
            willThrow(new InvalidUploadTokenException("업로드 토큰이 없습니다. 페이지를 새로고침 후 다시 시도하세요."))
                    .given(isoUploadIntentService).consume(eq(1L), any());

            mvc.perform(multipart("/management/os/1/iso/upload")
                            .file(buildFile())
                            .param("isoPath", "/mnt/iso/dvd.iso")
                            .param("allowCreateDirectory", "false"))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.message").value(containsString("토큰")));
        }

        @Test
        @DisplayName("경로 형식 오류 (빈 문자열 우회 → InvalidPathException 흐름) → 409")
        void invalidPath_returns409() throws Exception {
            doThrow(new InvalidIsoPathException("ISO 경로 형식이 올바르지 않습니다 : bad"))
                    .when(osMetadataService).prepareIsoRegistration(eq(1L), any(), any(), any());

            mvc.perform(multipart("/management/os/1/iso/upload")
                            .file(buildFile())
                            .param("isoPath", "/mnt/iso/dvd.iso") // bean 검증 통과만 되면 됨
                            .param("allowCreateDirectory", "false")
                            .header("X-Upload-Token", "t"))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.message").value(containsString("경로 형식")));
        }

        @Test
        @DisplayName("체크섬 중복 → 409 DuplicateISOContentException")
        void duplicateContent_returns409() throws Exception {
            doThrow(new DuplicateISOContentException("/mnt/iso/existing.iso"))
                    .when(osMetadataService).prepareIsoRegistration(eq(1L), any(), any(), any());

            mvc.perform(multipart("/management/os/1/iso/upload")
                            .file(buildFile())
                            .param("isoPath", "/mnt/iso/dvd.iso")
                            .param("allowCreateDirectory", "false")
                            .header("X-Upload-Token", "t"))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.message").value(containsString("/mnt/iso/existing.iso")));
        }

        @Test
        @DisplayName("파일시스템 동일 이름 파일 → 409 DuplicateFilenameException")
        void fileAlreadyExists_returns409() throws Exception {
            doThrow(new DuplicateFilenameException("/mnt/iso/dvd.iso"))
                    .when(osMetadataService).prepareIsoRegistration(eq(1L), any(), any(), any());

            mvc.perform(multipart("/management/os/1/iso/upload")
                            .file(buildFile())
                            .param("isoPath", "/mnt/iso/dvd.iso")
                            .param("allowCreateDirectory", "false")
                            .header("X-Upload-Token", "t"))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.message").value(containsString("이미 같은 이름의 파일")));
        }

        @Test
        @DisplayName("OSMetadata not found → 404")
        void unknownOs_returns404() throws Exception {
            doThrow(new OSMetadataNotFoundException(999L))
                    .when(osMetadataService).prepareIsoRegistration(eq(999L), any(), any(), any());

            mvc.perform(multipart("/management/os/999/iso/upload")
                            .file(buildFile())
                            .param("isoPath", "/mnt/iso/dvd.iso")
                            .param("allowCreateDirectory", "false")
                            .header("X-Upload-Token", "t"))
                    .andExpect(status().isNotFound());
        }
    }

    // ========= Extract 시나리오 =========

    @Nested
    @DisplayName("POST /{osId}/iso/{isoId}/extract")
    class Extract {

        @Test
        @DisplayName("정상 — 200 + jobId")
        void success() throws Exception {
            given(compsExtractionLauncher.startExtraction(1L, 2L)).willReturn("job-xyz");

            mvc.perform(post("/management/os/1/iso/2/extract"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.jobId").value("job-xyz"));
        }

        @Test
        @DisplayName("이미 추출 완료된 ISO → 409 AlreadyExtractedException")
        void alreadyExtracted_returns409() throws Exception {
            given(compsExtractionLauncher.startExtraction(1L, 2L))
                    .willThrow(new AlreadyExtractedException(2L));

            mvc.perform(post("/management/os/1/iso/2/extract"))
                    .andExpect(status().isConflict());
        }

        @Test
        @DisplayName("ISO 없음 → 404")
        void unknownIso_returns404() throws Exception {
            given(compsExtractionLauncher.startExtraction(1L, 99L))
                    .willThrow(new ISONotFoundException(1L, 99L));

            mvc.perform(post("/management/os/1/iso/99/extract"))
                    .andExpect(status().isNotFound());
        }
    }

    @Test
    @DisplayName("GET /{osId}/iso/{isoId}/integrity-status : 200 + status/badgeClass")
    void integrityStatus() throws Exception {
        given(osMetadataService.findIntegrityStatus(1L, 2L))
                .willReturn(IntegrityStatusResponse.of(2L, IntegrityStatus.SIGNATURE_INVALID, null));

        mvc.perform(get("/management/os/1/iso/2/integrity-status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resourceId").value(2))
                .andExpect(jsonPath("$.integrityStatus").value("SIGNATURE_INVALID"))
                .andExpect(jsonPath("$.badgeClass").value("n-badge-orange"));
    }

    // =========== MK2 WAVE 2 — Intent (단계 A) ISO Path Nudge 4 시나리오 ===========

    @Nested
    @DisplayName("MK2 WAVE 2 — ISO Intent Nudge")
    class IsoIntentNudge {

        @Test
        @DisplayName("intent : 동일 path 의 soft-deleted/Deprecated 자원 → 409 NUDGE_REQUIRED")
        void intentPathNudge() throws Exception {
            var req = new IsoUploadIntentRequest("/opt/iso/rocky/9/dvd.iso", "dvd.iso", 1024L, false);
            java.util.UUID nudgeId = java.util.UUID.randomUUID();
            var payload = com.example.serverprovision.management.common.nudge.dto.NudgeRequiredResponse.of(
                    nudgeId,
                    java.util.List.of(new com.example.serverprovision.management.common.nudge.dto.NudgeConflictEntry(
                            55L,
                            com.example.serverprovision.global.lifecycle.LifecycleStage.SOFT_DELETED,
                            "hash", "9.4", "/opt/iso/rocky/9/dvd.iso", Instant.now())),
                    Instant.now().plusSeconds(300));
            willThrow(new com.example.serverprovision.management.os.exception.IsoNudgeRequiredException(
                    "동일 path", payload))
                    .given(isoUploadIntentService).issue(eq(1L), any());

            mvc.perform(post("/management/os/1/iso/upload-intent")
                            .contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsString(req)))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("NUDGE_REQUIRED"))
                    .andExpect(jsonPath("$.nudgeId").value(nudgeId.toString()))
                    .andExpect(jsonPath("$.conflicts[0].id").value(55));
        }

        @Test
        @DisplayName("intent-nudge proceed : 200 + 새 uploadToken")
        void intentNudgeProceed() throws Exception {
            java.util.UUID nudgeId = java.util.UUID.randomUUID();
            given(osNudgeService.proceedIntent(eq(nudgeId)))
                    .willReturn(new IsoUploadIntentResponse.IntentTokenIssued("token-new", java.util.List.of()));

            mvc.perform(post("/management/os/intent-nudge/" + nudgeId + "/proceed"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.uploadToken").value("token-new"));
        }

        @Test
        @DisplayName("intent-nudge replace : 200 + 새 uploadToken (targetId purge 후)")
        void intentNudgeReplace() throws Exception {
            java.util.UUID nudgeId = java.util.UUID.randomUUID();
            given(osNudgeService.replaceIntent(eq(nudgeId), eq(55L)))
                    .willReturn(new IsoUploadIntentResponse.IntentTokenIssued("token-after-replace", java.util.List.of()));

            mvc.perform(post("/management/os/intent-nudge/" + nudgeId + "/replace?targetId=55"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.uploadToken").value("token-after-replace"));
        }

        @Test
        @DisplayName("intent-nudge cancel : 204 NoContent")
        void intentNudgeCancel() throws Exception {
            java.util.UUID nudgeId = java.util.UUID.randomUUID();

            mvc.perform(post("/management/os/intent-nudge/" + nudgeId + "/cancel"))
                    .andExpect(status().isNoContent());
        }
    }

    // =========== MK2 WAVE 3 — Hash Precheck (Phase 1 / 2 / 3) 5 시나리오 ===========

    @Nested
    @DisplayName("MK2 WAVE 3 — Hash Precheck")
    class WaveThreeHashPrecheck {

        @Test
        @DisplayName("Phase 1 정상 (candidates 0건) → 200 + type=INTENT_TOKEN_ISSUED")
        void phase1_noCandidates_directToken() throws Exception {
            given(isoUploadIntentService.issue(eq(1L), any()))
                    .willReturn(new IsoUploadIntentResponse.IntentTokenIssued("token-direct", java.util.List.of()));

            mvc.perform(post("/management/os/1/iso/upload-intent")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"isoPath":"/opt/iso/x.iso","filename":"x.iso","size":1024,"allowCreateDirectory":false}
                                    """))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.type").value("INTENT_TOKEN_ISSUED"))
                    .andExpect(jsonPath("$.uploadToken").value("token-direct"));
        }

        @Test
        @DisplayName("Phase 1 candidates 1+ → 200 + type=HASH_CHECK_REQUIRED + candidates")
        void phase1_candidatesPresent_hashCheckRequired() throws Exception {
            var candidate = new com.example.serverprovision.management.common.nudge.dto.NudgeConflictEntry(
                    77L,
                    com.example.serverprovision.global.lifecycle.LifecycleStage.SOFT_DELETED,
                    "abc", "9.4", "/opt/iso/old.iso", Instant.now());
            given(isoUploadIntentService.issue(eq(1L), any()))
                    .willReturn(new IsoUploadIntentResponse.HashCheckRequired(
                            java.util.List.of(candidate), "SHA-256"));

            mvc.perform(post("/management/os/1/iso/upload-intent")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"isoPath":"/opt/iso/new.iso","filename":"new.iso","size":1024,"allowCreateDirectory":false}
                                    """))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.type").value("HASH_CHECK_REQUIRED"))
                    .andExpect(jsonPath("$.fingerprintAlgorithm").value("SHA-256"))
                    .andExpect(jsonPath("$.candidates[0].id").value(77));
        }

        @Test
        @DisplayName("Phase 2 hash 매칭 → 409 NUDGE_REQUIRED")
        void phase2_hashMatch_nudgeRequired() throws Exception {
            java.util.UUID nudgeId = java.util.UUID.randomUUID();
            var payload = com.example.serverprovision.management.common.nudge.dto.NudgeRequiredResponse.of(
                    nudgeId,
                    java.util.List.of(new com.example.serverprovision.management.common.nudge.dto.NudgeConflictEntry(
                            77L,
                            com.example.serverprovision.global.lifecycle.LifecycleStage.SOFT_DELETED,
                            "deadbeef", "9.4", "/opt/iso/old.iso", Instant.now())),
                    Instant.now().plusSeconds(300));
            willThrow(new com.example.serverprovision.management.os.exception.IsoNudgeRequiredException(
                    "동일 hash", payload))
                    .given(isoUploadIntentService).issue(eq(1L), any());

            mvc.perform(post("/management/os/1/iso/upload-intent")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"isoPath":"/opt/iso/new.iso","filename":"new.iso","size":1024,"allowCreateDirectory":false,"clientHash":"abcdef0123456789abcdef0123456789abcdef0123456789abcdef0123456789"}
                                    """))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("NUDGE_REQUIRED"))
                    .andExpect(jsonPath("$.nudgeId").value(nudgeId.toString()));
        }

        @Test
        @DisplayName("clientHash 형식 오류 (64자 hex 아님) → 400")
        void clientHashFormatInvalid_returns400() throws Exception {
            mvc.perform(post("/management/os/1/iso/upload-intent")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"isoPath":"/opt/iso/x.iso","filename":"x.iso","size":1024,"allowCreateDirectory":false,"clientHash":"not-a-hash"}
                                    """))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value(containsString("clientHash")));
        }

        @Test
        @DisplayName("Phase 3 client hash 와 server 계산 hash 불일치 → 400 IsoClientHashMismatchException")
        void phase3_hashMismatch_returns400() throws Exception {
            doThrow(new com.example.serverprovision.management.os.exception.IsoClientHashMismatchException(
                    "abcdef…", "fedcba…"))
                    .when(osMetadataService).prepareIsoRegistration(eq(1L), any(), any(), any());

            mvc.perform(multipart("/management/os/1/iso/upload")
                            .file(new MockMultipartFile("file", "dvd.iso", "application/octet-stream", new byte[]{1, 2, 3}))
                            .param("isoPath", "/opt/iso/dvd.iso")
                            .param("allowCreateDirectory", "false")
                            .header("X-Upload-Token", "tok"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value(containsString("fingerprint")));
        }
    }
}
