package com.example.serverprovision.management.bmc.controller;

import com.example.serverprovision.global.security.exception.ExecutableContentRejectedException;
import com.example.serverprovision.global.security.exception.PathOutsideAllowedRootsException;
import com.example.serverprovision.global.security.exception.UploadLimitExceededException;
import com.example.serverprovision.management.bmc.exception.BmcFileStorageException;
import com.example.serverprovision.management.bmc.exception.BmcNotFoundException;
import com.example.serverprovision.management.bmc.exception.BmcPathConflictException;
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

import static org.hamcrest.Matchers.containsString;
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
 * <p>보안 예외가 도메인 예외 처리에 무심코 흡수돼 분류된 status code(415 · 403 · 413)가 silent 500 으로
 * 새는 회귀를 차단한다.</p>
 *
 * <p><b>R12-2 — 이 파일이 multi-catch 승급(D5)의 회귀망이다.</b> 컨트롤러에서
 * {@code catch (NotFoundException | FieldBoundConflictException | ConflictException | DomainException)}
 * 4단을 걷어내고 {@code ApiExceptionHandler} 로 넘겼으므로, 종전에 각 catch 가 만들던 응답이
 * advice 경로에서도 <b>같은 상태코드 · 같은 바디</b>로 나오는지 확인해야 한다. 특히
 * {@code FieldBoundConflictException} 은 {@code fieldErrors} 배열을 동봉하던 분기라 관문이다.
 * zip 업로드 폐지로 zip bomb 계열 시나리오는 단일 파일 경로에서 실제 발생 가능한 예외로 교체했다.</p>
 */
@WebMvcTest(controllers = BmcUploadController.class)
class BmcControllerSecurityFlowTest {
    @org.springframework.test.context.bean.override.mockito.MockitoBean com.example.serverprovision.global.trash.service.TypedNameVerifier typedNameVerifier;

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper om;

    @MockitoBean BmcService bmcService;
    @MockitoBean com.example.serverprovision.management.bmc.service.BmcRegistrationService bmcRegistrationService;
    @MockitoBean com.example.serverprovision.management.bmc.service.BmcFirmwareFilePolicy bmcFirmwareFilePolicy;
    @MockitoBean BmcUploadIntentService bmcUploadIntentService;
    @MockitoBean com.example.serverprovision.management.bmc.service.BmcNudgeService bmcNudgeService;
    @MockitoBean BoardModelMetadataService boardModelService;
    @MockitoBean BmcVerificationLauncher bmcVerificationLauncher;
    @MockitoBean DirectoryBrowseService directoryBrowseService;
    @MockitoBean com.example.serverprovision.global.lifecycle.DeleteIntentRegistry deleteIntentRegistry;
    @MockitoBean JpaMetamodelMappingContext jpaMetamodelMappingContext;

    private static BmcUploadIntentService.Intent issuedIntent() {
        return new BmcUploadIntentService.Intent(1L, "/opt/bmc/x/", "bmc.ima_enc", 100L, "1.0", Instant.now());
    }

    private static org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder uploadRequest() {
        return multipart("/management/bmc/1/upload");
    }

    @Test
    @DisplayName("upload : ExecutableContentRejectedException → 415 (silent-500 차단)")
    void upload_executableContent_returns415() throws Exception {
        given(bmcUploadIntentService.consume(eq(1L), anyString())).willReturn(issuedIntent());
        willThrow(new ExecutableContentRejectedException())
                .given(bmcRegistrationService).addBmc(eq(1L), any(), any());

        mvc.perform(uploadRequest()
                        .file(new MockMultipartFile("firmwareFile", "bmc.ima_enc", "application/octet-stream", "ELF".getBytes()))
                        .param("name", "bmc-fw")
                        .param("version", "1.0")
                        .param("firmwarePath", "/opt/bmc/x/")
                        .param("description", "")
                        .param("allowCreateDirectory", "true")
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
                                {"firmwarePath":"/etc/passwd","fileName":"bmc.ima_enc",
                                 "fileSize":100,"version":"1.0","allowCreateDirectory":false}
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    @DisplayName("upload : UploadLimitExceededException → 413 (silent-500 차단)")
    void upload_uploadLimitExceeded_returns413() throws Exception {
        given(bmcUploadIntentService.consume(eq(1L), anyString())).willReturn(issuedIntent());
        willThrow(new UploadLimitExceededException("file size > limit"))
                .given(bmcRegistrationService).addBmc(eq(1L), any(), any());

        mvc.perform(uploadRequest()
                        .file(new MockMultipartFile("firmwareFile", "huge.ima_enc", "application/octet-stream", "x".getBytes()))
                        .param("name", "bmc-fw")
                        .param("version", "1.0")
                        .param("firmwarePath", "/opt/bmc/x/")
                        .param("description", "")
                        .param("allowCreateDirectory", "true")
                        .header("X-Upload-Token", "tok"))
                .andExpect(status().isPayloadTooLarge())
                .andExpect(jsonPath("$.message").exists());
    }

    // ==== R12-2 D5 — multi-catch 승급 회귀 (advice 가 종전 분기와 같은 응답을 내는가) ====

    @Test
    @DisplayName("D5 회귀 : NotFoundException → 404 (구 catch(NotFoundException) 분기와 동일)")
    void upload_notFound_returns404() throws Exception {
        given(bmcUploadIntentService.consume(eq(1L), anyString())).willReturn(issuedIntent());
        willThrow(new BmcNotFoundException(1L, 99L))
                .given(bmcRegistrationService).addBmc(eq(1L), any(), any());

        mvc.perform(uploadRequest()
                        .file(new MockMultipartFile("firmwareFile", "bmc.ima_enc", null, "x".getBytes()))
                        .param("name", "x").param("version", "1.0")
                        .param("firmwarePath", "/opt/bmc/x/")
                        .param("description", "").param("allowCreateDirectory", "true")
                        .header("X-Upload-Token", "tok"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    @DisplayName("D5 회귀 : FieldBoundConflictException → 409 + fieldErrors 동봉 (구 ofFieldBound 분기와 동일)")
    void upload_fieldBoundConflict_returns409WithFieldErrors() throws Exception {
        given(bmcUploadIntentService.consume(eq(1L), anyString())).willReturn(issuedIntent());
        willThrow(new BmcPathConflictException("/opt/bmc/x"))
                .given(bmcRegistrationService).addBmc(eq(1L), any(), any());

        mvc.perform(uploadRequest()
                        .file(new MockMultipartFile("firmwareFile", "bmc.ima_enc", null, "x".getBytes()))
                        .param("name", "x").param("version", "1.0")
                        .param("firmwarePath", "/opt/bmc/x/")
                        .param("description", "").param("allowCreateDirectory", "true")
                        .header("X-Upload-Token", "tok"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(containsString("점유")))
                // 구 컨트롤러가 ApiErrorResponse.ofFieldBound 로 만들던 바디를 advice 가 그대로 낸다.
                .andExpect(jsonPath("$.fieldErrors[0].field").value("targetDirectory"));
    }

    @Test
    @DisplayName("D5 회귀 : 저장 IO 실패(DomainException) → 500 (구 catch(DomainException) 분기와 동일)")
    void upload_storageFailure_returns500() throws Exception {
        given(bmcUploadIntentService.consume(eq(1L), anyString())).willReturn(issuedIntent());
        willThrow(new BmcFileStorageException("디스크 가득", new RuntimeException("io")))
                .given(bmcRegistrationService).addBmc(eq(1L), any(), any());

        mvc.perform(uploadRequest()
                        .file(new MockMultipartFile("firmwareFile", "bmc.ima_enc", null, "x".getBytes()))
                        .param("name", "x").param("version", "1.0")
                        .param("firmwarePath", "/opt/bmc/x/")
                        .param("description", "").param("allowCreateDirectory", "true")
                        .header("X-Upload-Token", "tok"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.message").exists());
    }
}
