package com.example.serverprovision.management.bmc.controller;

import com.example.serverprovision.management.common.filesystem.exception.TargetDirectoryNotEmptyException;
import com.example.serverprovision.management.bios.vo.IntegrityStatus;
import com.example.serverprovision.management.common.dto.response.IntegrityStatusResponse;
import com.example.serverprovision.management.common.filesystem.dto.DirectoryListingResponse;
import com.example.serverprovision.management.common.filesystem.exception.BrowseTargetNotDirectoryException;
import com.example.serverprovision.management.common.filesystem.exception.BrowseTargetNotFoundException;
import com.example.serverprovision.management.common.filesystem.exception.DirectoryBrowseIoException;
import com.example.serverprovision.management.common.filesystem.service.DirectoryBrowseService;
import com.example.serverprovision.management.bmc.dto.request.BmcUploadIntentRequest;
import com.example.serverprovision.management.bmc.dto.response.BmcResponse;
import com.example.serverprovision.management.bmc.dto.response.BmcUploadIntentResponse;
import com.example.serverprovision.management.bmc.enums.BmcUploadMode;
import com.example.serverprovision.management.bmc.exception.BmcNotFoundException;
import com.example.serverprovision.management.bmc.exception.DuplicateBmcVersionException;
import com.example.serverprovision.management.bmc.service.BmcService;
import com.example.serverprovision.management.bmc.service.BmcUploadIntentService;
import com.example.serverprovision.management.bmc.service.BmcVerificationLauncher;
import com.example.serverprovision.management.board.dto.response.BoardModelResponse;
import com.example.serverprovision.management.board.enums.Vendor;
import com.example.serverprovision.management.board.exception.BoardModelNotFoundException;
import com.example.serverprovision.management.board.service.BoardModelService;
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

@WebMvcTest(controllers = BmcController.class)
class BmcControllerTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper om;

    @MockitoBean BmcService bmcService;
    @MockitoBean BmcUploadIntentService bmcUploadIntentService;
    @MockitoBean com.example.serverprovision.management.bmc.service.BmcNudgeService bmcNudgeService;
    @MockitoBean BoardModelService boardModelService;
    @MockitoBean BmcVerificationLauncher bmcVerificationLauncher;
    @MockitoBean DirectoryBrowseService directoryBrowseService;
    @MockitoBean JpaMetamodelMappingContext jpaMetamodelMappingContext;

    @Test
    @DisplayName("GET /{boardId}/new : 신규 폼 렌더")
    void newForm() throws Exception {
        given(boardModelService.findById(1L))
                .willReturn(new BoardModelResponse(1L, Vendor.GIGABYTE, "MS03", "", 0, 0, true, false));

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
            var req = new BmcUploadIntentRequest("/mnt/bmc/x", BmcUploadMode.FOLDER, 5, 1024, "13.06.25", false, "");
            given(bmcUploadIntentService.issue(eq(1L), any()))
                    .willReturn(new BmcUploadIntentResponse("token-abc", List.of(), null));

            mvc.perform(post("/management/bmc/1/upload-intent")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(om.writeValueAsString(req)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.uploadToken").value("token-abc"));
        }

        @Test
        @DisplayName("대상 디렉토리 공란 → 400")
        void blankTargetDirectory() throws Exception {
            String body = "{\"targetDirectory\":\"\",\"uploadMode\":\"FOLDER\",\"fileCount\":5,\"totalBytes\":1024,\"version\":\"13.06.25\",\"allowCreateDirectory\":false,\"entrypointRelativePath\":\"\"}";
            mvc.perform(post("/management/bmc/1/upload-intent")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value(containsString("targetDirectory")));
        }
    }

    @Nested
    @DisplayName("POST /{boardId}/upload")
    class Upload {

        @Test
        @DisplayName("폴더 업로드 성공 — 200 + id/redirect")
        void success_folder() throws Exception {
            given(bmcUploadIntentService.consume(eq(1L), eq("token-abc")))
                    .willReturn(new BmcUploadIntentService.Intent(
                            1L, "/mnt/bmc/x", BmcUploadMode.FOLDER, 2, 1024L, "13.06.25", "", Instant.now()));
            given(bmcService.addBmc(eq(1L), any(), eq(BmcUploadMode.FOLDER), any(), any(), any()))
                    .willReturn(42L);

            mvc.perform(multipart("/management/bmc/1/upload")
                            .file(new MockMultipartFile("folderFiles", "BmcPkg/flash.nsh", null, "x".getBytes()))
                            .param("uploadMode", "FOLDER")
                            .param("name", "GIGABYTE BMC")
                            .param("version", "13.06.25")
                            .param("targetDirectory", "/mnt/bmc/x")
                            .param("description", "")
                            .param("allowCreateDirectory", "true")
                            .param("entrypointRelativePath", "")
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
                            1L, "/mnt/bmc/x", BmcUploadMode.SINGLE_FILE, 1, 10L, "1.0", "", Instant.now()));
            willThrow(new TargetDirectoryNotEmptyException("/mnt/bmc/x"))
                    .given(bmcService).addBmc(eq(1L), any(), any(), any(), any(), any());

            mvc.perform(multipart("/management/bmc/1/upload")
                            .file(new MockMultipartFile("singleFile", "firmware.bin", null, "x".getBytes()))
                            .param("uploadMode", "SINGLE_FILE")
                            .param("name", "x")
                            .param("version", "1.0")
                            .param("targetDirectory", "/mnt/bmc/x")
                            .param("description", "")
                            .param("allowCreateDirectory", "true")
                            .param("entrypointRelativePath", "")
                            .header("X-Upload-Token", "t"))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.message").value(containsString("비어있지 않습니다")));
        }
    }

    @Test
    @DisplayName("GET /{boardId}/bmc/{bmcId}/edit : 수정 폼 렌더")
    void editForm() throws Exception {
        given(boardModelService.findById(1L))
                .willReturn(new BoardModelResponse(1L, Vendor.GIGABYTE, "MS03", "", 0, 0, true, false));
        given(bmcService.findBmc(1L, 2L))
                .willReturn(new BmcResponse(2L, 1L, "AST2600", "12.61", "/opt/fw/bmc", "flash.nsh", "hash", 3, 2048L,
                        "", IntegrityStatus.NOT_VERIFIED, true, false, false));

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
        given(bmcService.findIntegrityStatus(1L, 2L))
                .willReturn(IntegrityStatusResponse.of(2L, IntegrityStatus.TAMPERED, null));

        mvc.perform(get("/management/bmc/1/bmc/2/integrity-status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resourceId").value(2))
                .andExpect(jsonPath("$.integrityStatus").value("TAMPERED"))
                .andExpect(jsonPath("$.badgeClass").value("n-badge-red"));
    }

    @Test
    @DisplayName("GET /browse : 정상 경로면 200 JSON")
    void browse_success() throws Exception {
        given(directoryBrowseService.browse(any()))
                .willReturn(new DirectoryListingResponse(
                        "/opt/bmc", "/opt",
                        List.of(DirectoryListingResponse.Entry.directory("GIGABYTE"))));

        mvc.perform(get("/management/bmc/browse").param("path", "/opt/bmc"))
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

        mvc.perform(get("/management/bmc/browse").param("path", "/no/such/path"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value(containsString("경로를 찾을 수 없습니다")));
    }

    @Test
    @DisplayName("GET /browse : 파일 경로면 409 JSON")
    void browse_notDirectory() throws Exception {
        willThrow(new BrowseTargetNotDirectoryException("/opt/bmc/fw.bin"))
                .given(directoryBrowseService).browse(any());

        mvc.perform(get("/management/bmc/browse").param("path", "/opt/bmc/fw.bin"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(containsString("디렉토리가 아닙니다")));
    }

    @Test
    @DisplayName("GET /browse : IO 예외면 500 JSON")
    void browse_ioError() throws Exception {
        willThrow(new DirectoryBrowseIoException("io fail", new RuntimeException("x")))
                .given(directoryBrowseService).browse(any());

        mvc.perform(get("/management/bmc/browse").param("path", "/opt/bmc"))
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
}
