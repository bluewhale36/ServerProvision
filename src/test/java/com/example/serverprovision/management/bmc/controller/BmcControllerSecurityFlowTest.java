package com.example.serverprovision.management.bmc.controller;

import com.example.serverprovision.global.security.exception.PathOutsideAllowedRootsException;
import com.example.serverprovision.global.security.exception.UploadLimitExceededException;
import com.example.serverprovision.global.security.exception.ZipBombInspectionFailedException;
import com.example.serverprovision.global.security.exception.ZipBombSuspectedException;
import com.example.serverprovision.management.bmc.enums.BmcUploadMode;
import com.example.serverprovision.management.bmc.service.BmcService;
import com.example.serverprovision.management.bmc.service.BmcUploadIntentService;
import com.example.serverprovision.management.bmc.service.BmcVerificationLauncher;
import com.example.serverprovision.management.board.service.metadata.BoardModelMetadataService;
import com.example.serverprovision.management.common.filesystem.service.DirectoryBrowseService;
import org.junit.jupiter.api.DisplayName;
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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * S3.4 (Silent-500-A) BMC 컨트롤러 보안 통합 — 실 컨트롤러 + ApiExceptionHandler 매핑 회귀.
 *
 * <p>{@code catch (DomainException e) → 500} 이 보안 예외 (zip bomb / path policy 등) 를 무심코 흡수해
 * 분류된 status code (415/403/413) 가 silent 500 으로 새는 회귀를 차단.</p>
 */
@WebMvcTest(controllers = BmcUploadController.class)
class BmcControllerSecurityFlowTest {
    @org.springframework.test.context.bean.override.mockito.MockitoBean com.example.serverprovision.global.trash.service.TypedNameVerifier typedNameVerifier;

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper om;

    @MockitoBean BmcService bmcService;
    @MockitoBean com.example.serverprovision.management.bmc.service.BmcRegistrationService bmcRegistrationService;
    @MockitoBean BmcUploadIntentService bmcUploadIntentService;
    @MockitoBean com.example.serverprovision.management.bmc.service.BmcNudgeService bmcNudgeService;
    @MockitoBean BoardModelMetadataService boardModelService;
    @MockitoBean BmcVerificationLauncher bmcVerificationLauncher;
    @MockitoBean DirectoryBrowseService directoryBrowseService;
    @MockitoBean com.example.serverprovision.global.lifecycle.DeleteIntentRegistry deleteIntentRegistry;
    @MockitoBean JpaMetamodelMappingContext jpaMetamodelMappingContext;

    @Test
    @DisplayName("upload : ZipBombSuspectedException → 415 (silent-500 차단)")
    void upload_zipBombSuspected_returns415() throws Exception {
        given(bmcUploadIntentService.consume(eq(1L), anyString()))
                .willReturn(new BmcUploadIntentService.Intent(
                        1L, "/opt/bmc/x", BmcUploadMode.ZIP, 1, 100L, "1.0", "", Instant.now()));
        willThrow(new ZipBombSuspectedException("compression ratio > limit"))
                .given(bmcRegistrationService).addBmc(eq(1L), any(), any(), any(), any(), any());

        mvc.perform(multipart("/management/bmc/1/upload")
                        .file(new MockMultipartFile("zipFile", "bomb.zip", "application/zip", "PKbomb".getBytes()))
                        .param("uploadMode", "ZIP")
                        .param("name", "bmc-fw")
                        .param("version", "1.0")
                        .param("targetDirectory", "/opt/bmc/x")
                        .param("description", "")
                        .param("allowCreateDirectory", "true")
                        .param("entrypointRelativePath", "")
                        .header("X-Upload-Token", "tok"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    @DisplayName("upload-intent : PathOutsideAllowedRootsException → 403 (silent-500 차단)")
    void uploadIntent_pathOutside_returns403() throws Exception {
        willThrow(new PathOutsideAllowedRootsException())
                .given(bmcUploadIntentService).issue(eq(1L), any());

        mvc.perform(post("/management/bmc/1/upload-intent")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"targetDirectory":"/etc/passwd","uploadMode":"ZIP","fileCount":1,
                                 "totalBytes":100,"version":"1.0","allowCreateDirectory":false,
                                 "entrypointRelativePath":""}
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    @DisplayName("upload : UploadLimitExceededException → 413 (silent-500 차단)")
    void upload_uploadLimitExceeded_returns413() throws Exception {
        given(bmcUploadIntentService.consume(eq(1L), anyString()))
                .willReturn(new BmcUploadIntentService.Intent(
                        1L, "/opt/bmc/x", BmcUploadMode.ZIP, 1, 100L, "1.0", "", Instant.now()));
        willThrow(new UploadLimitExceededException("entry size > limit"))
                .given(bmcRegistrationService).addBmc(eq(1L), any(), any(), any(), any(), any());

        mvc.perform(multipart("/management/bmc/1/upload")
                        .file(new MockMultipartFile("zipFile", "huge.zip", "application/zip", "PKx".getBytes()))
                        .param("uploadMode", "ZIP")
                        .param("name", "bmc-fw")
                        .param("version", "1.0")
                        .param("targetDirectory", "/opt/bmc/x")
                        .param("description", "")
                        .param("allowCreateDirectory", "true")
                        .param("entrypointRelativePath", "")
                        .header("X-Upload-Token", "tok"))
                .andExpect(status().isPayloadTooLarge())
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    @DisplayName("upload : ZipBombInspectionFailedException → 500 (운영 IO 분류)")
    void upload_zipInspectionFailed_returns500() throws Exception {
        given(bmcUploadIntentService.consume(eq(1L), anyString()))
                .willReturn(new BmcUploadIntentService.Intent(
                        1L, "/opt/bmc/x", BmcUploadMode.ZIP, 1, 100L, "1.0", "", Instant.now()));
        willThrow(new ZipBombInspectionFailedException("disk full", new RuntimeException("io")))
                .given(bmcRegistrationService).addBmc(eq(1L), any(), any(), any(), any(), any());

        mvc.perform(multipart("/management/bmc/1/upload")
                        .file(new MockMultipartFile("zipFile", "x.zip", "application/zip", "PK".getBytes()))
                        .param("uploadMode", "ZIP")
                        .param("name", "bmc-fw")
                        .param("version", "1.0")
                        .param("targetDirectory", "/opt/bmc/x")
                        .param("description", "")
                        .param("allowCreateDirectory", "true")
                        .param("entrypointRelativePath", "")
                        .header("X-Upload-Token", "tok"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.message").exists());
    }
}
