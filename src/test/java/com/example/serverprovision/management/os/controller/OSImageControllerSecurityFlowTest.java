package com.example.serverprovision.management.os.controller;

import com.example.serverprovision.global.security.exception.PathOutsideAllowedRootsException;
import com.example.serverprovision.global.security.exception.UploadLimitExceededException;
import com.example.serverprovision.global.security.exception.ZipBombInspectionFailedException;
import com.example.serverprovision.global.security.exception.ZipBombSuspectedException;
import com.example.serverprovision.management.common.filesystem.service.DirectoryBrowseService;
import com.example.serverprovision.management.os.service.CompsExtractionLauncher;
import com.example.serverprovision.management.os.service.IsoRegistrationLauncher;
import com.example.serverprovision.management.os.service.IsoUploadIntentService;
import com.example.serverprovision.management.os.service.IsoVerificationLauncher;
import com.example.serverprovision.management.os.service.OSImageService;
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
 * S3.4 (Silent-500-A) OSImage 컨트롤러 보안 통합 — 실 컨트롤러 + ApiExceptionHandler 매핑 회귀.
 *
 * <p>{@code catch (DomainException) → 500} 이 보안 예외를 흡수해 silent 500 으로 새는 회귀 차단.</p>
 */
@WebMvcTest(controllers = OSImageController.class)
class OSImageControllerSecurityFlowTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper om;

    @MockitoBean OSImageService osImageService;
    @MockitoBean com.example.serverprovision.management.os.service.OsNudgeService osNudgeService;
    @MockitoBean com.example.serverprovision.management.os.service.OSImageNudgeService osImageNudgeService;
    @MockitoBean CompsExtractionLauncher compsExtractionLauncher;
    @MockitoBean IsoUploadIntentService isoUploadIntentService;
    @MockitoBean IsoVerificationLauncher isoVerificationLauncher;
    @MockitoBean IsoRegistrationLauncher isoRegistrationLauncher;
    @MockitoBean DirectoryBrowseService directoryBrowseService;
    @MockitoBean com.example.serverprovision.global.lifecycle.DeleteIntentRegistry deleteIntentRegistry;
    @MockitoBean JpaMetamodelMappingContext jpaMetamodelMappingContext;

    @Test
    @DisplayName("upload-intent : PathOutsideAllowedRootsException → 403 (silent-500 차단)")
    void uploadIntent_pathOutside_returns403() throws Exception {
        willThrow(new PathOutsideAllowedRootsException())
                .given(isoUploadIntentService).issue(eq(1L), any());

        mvc.perform(post("/management/os/1/iso/upload-intent")
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.APPLICATION_JSON)
                        .content("""
                                {"isoPath":"/etc/iso/x.iso","filename":"x.iso","size":1024,"allowCreateDirectory":false}
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    @DisplayName("upload : ZipBombSuspectedException → 415 (corrupted/iso 위장 시나리오)")
    void upload_zipBombSuspected_returns415() throws Exception {
        given(isoUploadIntentService.consume(eq(1L), anyString()))
                .willReturn(new IsoUploadIntentService.Intent(
                        1L, "/opt/iso/x.iso", "x.iso", 1024L, null, Instant.now()));
        willThrow(new ZipBombSuspectedException("iso 내부 zip 손상 또는 ratio 초과"))
                .given(osImageService).prepareIsoRegistration(eq(1L), any(), any(), any());

        mvc.perform(multipart("/management/os/1/iso/upload")
                        .file(new MockMultipartFile("file", "x.iso", "application/octet-stream", "data".getBytes()))
                        .param("isoPath", "/opt/iso/x.iso")
                        .param("description", "")
                        .param("allowCreateDirectory", "false")
                        .header("X-Upload-Token", "tok")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    @DisplayName("upload : UploadLimitExceededException → 413")
    void upload_uploadLimitExceeded_returns413() throws Exception {
        given(isoUploadIntentService.consume(eq(1L), anyString()))
                .willReturn(new IsoUploadIntentService.Intent(
                        1L, "/opt/iso/x.iso", "x.iso", 1024L, null, Instant.now()));
        willThrow(new UploadLimitExceededException("size > limit"))
                .given(osImageService).prepareIsoRegistration(eq(1L), any(), any(), any());

        mvc.perform(multipart("/management/os/1/iso/upload")
                        .file(new MockMultipartFile("file", "x.iso", "application/octet-stream", "data".getBytes()))
                        .param("isoPath", "/opt/iso/x.iso")
                        .param("description", "")
                        .param("allowCreateDirectory", "false")
                        .header("X-Upload-Token", "tok")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isPayloadTooLarge())
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    @DisplayName("upload : ZipBombInspectionFailedException → 500 (운영 IO 분류)")
    void upload_zipInspectionFailed_returns500() throws Exception {
        given(isoUploadIntentService.consume(eq(1L), anyString()))
                .willReturn(new IsoUploadIntentService.Intent(
                        1L, "/opt/iso/x.iso", "x.iso", 1024L, null, Instant.now()));
        willThrow(new ZipBombInspectionFailedException("disk full", new RuntimeException("io")))
                .given(osImageService).prepareIsoRegistration(eq(1L), any(), any(), any());

        mvc.perform(multipart("/management/os/1/iso/upload")
                        .file(new MockMultipartFile("file", "x.iso", "application/octet-stream", "data".getBytes()))
                        .param("isoPath", "/opt/iso/x.iso")
                        .param("description", "")
                        .param("allowCreateDirectory", "false")
                        .header("X-Upload-Token", "tok")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.message").exists());
    }
}
