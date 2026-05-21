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
import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * S3.4 (Silent-500-A) OSImage 컨트롤러 보안 통합 — 실 컨트롤러 + ApiExceptionHandler 매핑 회귀.
 *
 * <p>{@code catch (DomainException) → 500} 이 보안 예외를 흡수해 silent 500 으로 새는 회귀 차단.</p>
 */
@WebMvcTest(controllers = OSImageController.class)
class OSImageControllerSecurityFlowTest {
    @org.springframework.test.context.bean.override.mockito.MockitoBean com.example.serverprovision.global.trash.service.TypedNameVerifier typedNameVerifier;

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

    // S5-4 — '삭제된 항목 포함' 체크박스의 마커 검증 + inline onchange 부재 회귀.
    @Test
    @DisplayName("GET /management/os — '삭제된 항목 포함' 체크박스에 data-include-deleted-toggle 마커 + inline onchange 부재")
    void renders_includeDeletedToggle_with_dataMarker() throws Exception {
        given(osImageService.findAllGrouped(false)).willReturn(List.of());

        mvc.perform(get("/management/os"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("data-include-deleted-toggle")))
                .andExpect(content().string(not(containsString("onchange=\"window.location"))));
    }

    // S5-5 — miller-empty 가 data-empty-before / data-empty-after 2 상태 보유 + 외부 + 신규 OS 버전 등록 버튼.
    @Test
    @DisplayName("GET /management/os — miller-empty 2 상태 (data-empty-before/after) + 외부 '+ 신규 OS 버전 등록' 버튼")
    void miller_empty_has_two_state_data_attrs() throws Exception {
        // 빈 목록이면 miller 자체가 렌더 안 되므로 더미 1 그룹 주입.
        var osGroup = com.example.serverprovision.management.os.dto.response.OSGroupResponse.of(
                com.example.serverprovision.management.os.enums.OSName.ROCKY_LINUX,
                java.util.List.of()
        );
        given(osImageService.findAllGrouped(false)).willReturn(java.util.List.of(osGroup));

        mvc.perform(get("/management/os"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("data-empty-before")))
                .andExpect(content().string(containsString("data-empty-after")))
                .andExpect(content().string(containsString("+ 신규 OS 버전 등록")));
    }
}
