package com.example.serverprovision.management.bios.controller;

import com.example.serverprovision.global.security.exception.ExecutableContentRejectedException;
import com.example.serverprovision.management.bios.service.BiosService;
import com.example.serverprovision.management.bios.service.BiosUploadIntentService;
import com.example.serverprovision.management.bios.service.BiosVerificationLauncher;
import com.example.serverprovision.management.board.service.metadata.BoardModelMetadataService;
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
 * <p>보안 예외는 {@code DomainException} 계층 밖({@code SecurityException} 상속)이므로, 컨트롤러가
 * 도메인 예외를 잡는 코드로 무심코 흡수하면 415 가 아닌 500 으로 응답이 새는 사고가 발생한다.
 * 본 테스트는 실 컨트롤러를 통과해 415 가 응답되는지 검증한다.</p>
 *
 * <p>R12-1 — zip 업로드 폐지로 시나리오를 {@code ZipBombSuspectedException} 에서 단일 펌웨어 파일
 * 경로에서 실제 발생 가능한 {@code ExecutableContentRejectedException}(ContentGuard 실행 binary
 * DENY 정책)으로 교체했다. 매핑 회귀 검증이라는 목적은 동일하다.</p>
 */
@WebMvcTest(controllers = BiosUploadController.class)
class BiosControllerSecurityFlowTest {
    @org.springframework.test.context.bean.override.mockito.MockitoBean com.example.serverprovision.global.trash.service.TypedNameVerifier typedNameVerifier;

    @Autowired MockMvc mvc;

    @MockitoBean BiosService biosService;
    @MockitoBean com.example.serverprovision.management.bios.service.BiosRegistrationService biosRegistrationService;
    @MockitoBean BiosUploadIntentService biosUploadIntentService;
    @MockitoBean com.example.serverprovision.management.bios.service.BiosNudgeService biosNudgeService;
    @MockitoBean BoardModelMetadataService boardModelService;
    @MockitoBean BiosVerificationLauncher biosVerificationLauncher;
    @MockitoBean DirectoryBrowseService directoryBrowseService;
    @MockitoBean com.example.serverprovision.global.lifecycle.DeleteIntentRegistry deleteIntentRegistry;
    @MockitoBean JpaMetamodelMappingContext jpaMetamodelMappingContext;

    @Test
    @DisplayName("executableContent_415_via_realController : 등록 흐름에서 ExecutableContentRejectedException → 415 (실 컨트롤러 통과)")
    void executableContent_415_via_realController() throws Exception {
        // intent consume 은 정상 통과시키고, 본체 등록 단계에서 실행 binary DENY 정책이 발동한 상황을 흉내낸다.
        given(biosUploadIntentService.consume(eq(1L), anyString()))
                .willReturn(new BiosUploadIntentService.Intent(
                        1L, "/opt/bios/x/", "evil.RBU", 100L, "1.0", Instant.now()));
        willThrow(new ExecutableContentRejectedException())
                .given(biosRegistrationService).addBios(eq(1L), any(), any());

        mvc.perform(multipart("/management/bios/1/upload")
                        .file(new MockMultipartFile("firmwareFile", "evil.RBU", "application/octet-stream", "ELF".getBytes()))
                        .param("name", "evil")
                        .param("version", "1.0")
                        .param("firmwarePath", "/opt/bios/x/")
                        .param("description", "")
                        .param("allowCreateDirectory", "true")
                        .header("X-Upload-Token", "tok"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.message").exists());
    }
}
