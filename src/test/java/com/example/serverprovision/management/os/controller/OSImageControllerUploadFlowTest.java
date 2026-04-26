package com.example.serverprovision.management.os.controller;

import com.example.serverprovision.global.job.dto.response.JobStartResponse;
import com.example.serverprovision.global.job.service.BackgroundJobService;
import com.example.serverprovision.management.common.filesystem.dto.DirectoryListingResponse;
import com.example.serverprovision.management.common.filesystem.exception.InvalidBrowsePathException;
import com.example.serverprovision.management.common.filesystem.service.DirectoryBrowseService;
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
import com.example.serverprovision.management.os.exception.OSImageNotFoundException;
import com.example.serverprovision.management.os.service.CompsExtractionLauncher;
import com.example.serverprovision.management.os.service.IsoRegistrationLauncher;
import com.example.serverprovision.management.os.service.IsoUploadIntentService;
import com.example.serverprovision.management.os.service.IsoVerificationLauncher;
import com.example.serverprovision.management.os.service.OSImageService;
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
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
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
@WebMvcTest(controllers = OSImageController.class)
class OSImageControllerUploadFlowTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper om;

    @MockitoBean OSImageService osImageService;
    @MockitoBean CompsExtractionLauncher compsExtractionLauncher;
    @MockitoBean IsoUploadIntentService isoUploadIntentService;
    @MockitoBean IsoVerificationLauncher isoVerificationLauncher;
    @MockitoBean IsoRegistrationLauncher isoRegistrationLauncher;
    @MockitoBean DirectoryBrowseService directoryBrowseService;
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
                    .willReturn(new IsoUploadIntentResponse("token-abc", java.util.List.of()));

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
        @DisplayName("존재하지 않는 OSImage → 404 Not Found")
        void unknownOs_returns404() throws Exception {
            var req = new IsoUploadIntentRequest("/mnt/iso/dvd.iso", "dvd.iso", 1L, false);
            willThrow(new OSImageNotFoundException(999L))
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
            given(osImageService.prepareIsoRegistration(eq(1L), any(), any()))
                    .willReturn(new OSImageService.PreparedIsoRegistration(
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
                    .when(osImageService).prepareIsoRegistration(eq(1L), any(), any());

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
                    .when(osImageService).prepareIsoRegistration(eq(1L), any(), any());

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
                    .when(osImageService).prepareIsoRegistration(eq(1L), any(), any());

            mvc.perform(multipart("/management/os/1/iso/upload")
                            .file(buildFile())
                            .param("isoPath", "/mnt/iso/dvd.iso")
                            .param("allowCreateDirectory", "false")
                            .header("X-Upload-Token", "t"))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.message").value(containsString("이미 같은 이름의 파일")));
        }

        @Test
        @DisplayName("OSImage not found → 404")
        void unknownOs_returns404() throws Exception {
            doThrow(new OSImageNotFoundException(999L))
                    .when(osImageService).prepareIsoRegistration(eq(999L), any(), any());

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
}
