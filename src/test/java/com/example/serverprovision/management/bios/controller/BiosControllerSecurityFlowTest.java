package com.example.serverprovision.management.bios.controller;

import com.example.serverprovision.global.security.exception.ZipBombSuspectedException;
import com.example.serverprovision.management.bios.enums.BiosUploadMode;
import com.example.serverprovision.management.bios.service.BiosService;
import com.example.serverprovision.management.bios.service.BiosUploadIntentService;
import com.example.serverprovision.management.bios.service.BiosVerificationLauncher;
import com.example.serverprovision.management.board.service.BoardModelService;
import com.example.serverprovision.management.common.filesystem.service.DirectoryBrowseService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.data.jpa.mapping.JpaMetamodelMappingContext;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * S3.3 (P1-4) BIOS 컨트롤러 보안 통합 — 실 컨트롤러 + ApiExceptionHandler 매핑 회귀.
 *
 * <p>{@code ZipBombSuspectedException} 은 {@code DomainException} 의 하위 타입이므로, 컨트롤러의
 * {@code catch (DomainException e)} 가 무심코 흡수하면 415 가 아닌 500 으로 응답이 새는 사고가 발생한다.
 * 본 테스트는 실 컨트롤러를 통과해 415 가 응답되는지 검증한다.</p>
 */
@WebMvcTest(controllers = BiosUploadController.class)
class BiosControllerSecurityFlowTest {
    @org.springframework.test.context.bean.override.mockito.MockitoBean com.example.serverprovision.global.trash.service.TypedNameVerifier typedNameVerifier;

    @Autowired MockMvc mvc;

    @MockitoBean BiosService biosService;
    @MockitoBean BiosUploadIntentService biosUploadIntentService;
    @MockitoBean com.example.serverprovision.management.bios.service.BiosNudgeService biosNudgeService;
    @MockitoBean BoardModelService boardModelService;
    @MockitoBean BiosVerificationLauncher biosVerificationLauncher;
    @MockitoBean DirectoryBrowseService directoryBrowseService;
    @MockitoBean com.example.serverprovision.global.lifecycle.DeleteIntentRegistry deleteIntentRegistry;
    @MockitoBean JpaMetamodelMappingContext jpaMetamodelMappingContext;

    @Test
    @DisplayName("zipBomb_415_via_realController : 업로드 흐름에서 ZipBombSuspectedException → 415 (실 컨트롤러 통과)")
    void zipBomb_415_via_realController() throws Exception {
        // intent consume 은 정상 통과시키고, 본체 등록 단계에서 zip bomb 탐지가 발생한 상황을 흉내낸다.
        given(biosUploadIntentService.consume(eq(1L), anyString()))
                .willReturn(new BiosUploadIntentService.Intent(
                        1L, "/opt/bios/x", BiosUploadMode.ZIP, 1, 100L, "1.0", "", Instant.now()));
        willThrow(new ZipBombSuspectedException("compression ratio 1:9000 (limit 1:1000)"))
                .given(biosService).addBios(eq(1L), any(), any(), any(), any(), any());

        mvc.perform(multipart("/management/bios/1/upload")
                        .file(new MockMultipartFile("zipFile", "bomb.zip", "application/zip", "PKbomb".getBytes()))
                        .param("uploadMode", "ZIP")
                        .param("name", "bomb")
                        .param("version", "1.0")
                        .param("targetDirectory", "/opt/bios/x")
                        .param("description", "")
                        .param("allowCreateDirectory", "true")
                        .param("entrypointRelativePath", "")
                        .header("X-Upload-Token", "tok"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.message").exists());
    }
}
