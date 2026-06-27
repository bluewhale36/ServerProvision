package com.example.serverprovision.management.subprogram.controller;

import com.example.serverprovision.global.security.exception.EntrypointInvalidException;
import com.example.serverprovision.global.security.exception.PathOutsideAllowedRootsException;
import com.example.serverprovision.management.board.service.metadata.BoardModelMetadataService;
import com.example.serverprovision.management.common.filesystem.service.DirectoryBrowseService;
import com.example.serverprovision.management.subprogram.dto.request.SubprogramUploadIntentRequest;
import com.example.serverprovision.management.subprogram.dto.response.SubprogramResponse;
import com.example.serverprovision.management.subprogram.enums.SubprogramKind;
import com.example.serverprovision.management.subprogram.enums.SubprogramUploadMode;
import com.example.serverprovision.management.subprogram.service.SubprogramService;
import com.example.serverprovision.management.subprogram.service.SubprogramUploadIntentService;
import com.example.serverprovision.management.subprogram.service.SubprogramVerificationLauncher;
import com.example.serverprovision.management.subprogram.vo.BoardScope;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.data.jpa.mapping.JpaMetamodelMappingContext;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * S3.3 (P1-4) Subprogram 컨트롤러 보안 통합 — 실 컨트롤러 + ApiExceptionHandler 매핑 회귀.
 *
 * <p>{@link com.example.serverprovision.global.security.integration.SecurityIntegrationTest} 의 standaloneSetup
 * + probe controller 패턴은 advice 매핑은 검증하지만 feature 컨트롤러의 try/catch 가 보안 예외를 흡수해 500 으로
 * 새는 사고를 잡지 못한다. 본 테스트는 {@code @WebMvcTest} 로 실 컨트롤러를 띄워 사용자 시나리오로 회귀를 막는다.</p>
 *
 * <p>참고 : 본래 권장안에는 {@code POST /management/subprogram/upload-intent} 가 적혀 있으나, 실 라우트는
 * {@code /{kind}/{boardScope}/upload-intent} 로 path variable 을 포함한다. 보안 의미는 동일하므로 실제 라우트로 검증.</p>
 */
@WebMvcTest(controllers = {SubprogramController.class, SubprogramUploadController.class, SubprogramBrowseController.class})
class SubprogramControllerSecurityFlowTest {
    @org.springframework.test.context.bean.override.mockito.MockitoBean com.example.serverprovision.global.trash.service.TypedNameVerifier typedNameVerifier;

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper om;

    @MockitoBean SubprogramService subprogramService;
    @MockitoBean SubprogramUploadIntentService subprogramUploadIntentService;
    @MockitoBean com.example.serverprovision.management.subprogram.service.SubprogramNudgeService subprogramNudgeService;
    @MockitoBean SubprogramVerificationLauncher subprogramVerificationLauncher;
    @MockitoBean BoardModelMetadataService boardModelService;
    @MockitoBean DirectoryBrowseService directoryBrowseService;
    @MockitoBean com.example.serverprovision.global.lifecycle.DeleteIntentRegistry deleteIntentRegistry;
    @MockitoBean JpaMetamodelMappingContext jpaMetamodelMappingContext;

    @Test
    @DisplayName("uploadIntent : targetDirectory=/etc/passwd → 403 PathOutsideAllowedRoots")
    void uploadIntent_targetEtcPasswd_403() throws Exception {
        var req = new SubprogramUploadIntentRequest(
                "/etc/passwd", SubprogramUploadMode.FOLDER, 1, 1024L, "1.0", false);
        // Service 가 PathPolicyService.assertWritablePath 에서 PathOutsideAllowedRootsException 을 던짐을 흉내.
        willThrow(new PathOutsideAllowedRootsException())
                .given(subprogramUploadIntentService).issue(eq(SubprogramKind.DRIVER), any(BoardScope.class), any());

        mvc.perform(post("/management/subprogram/driver/common/upload-intent")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(req)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    @DisplayName("browse : path=/etc → 403 PathOutsideAllowedRoots (DirectoryBrowseService 이 던짐)")
    void browse_etcRoot_403() throws Exception {
        willThrow(new PathOutsideAllowedRootsException())
                .given(directoryBrowseService).browse(any());

        mvc.perform(get("/management/subprogram/browse").param("path", "/etc"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    @DisplayName("edit : entrypointRelativePath=../etc/passwd → 400 EntrypointInvalid")
    void edit_entrypointTraversal_400() throws Exception {
        // findSubprogram 은 binding 실패 분기에서만 호출되므로 잡지 않아도 무방하지만, 안전하게 stub 한다.
        given(subprogramService.findSubprogram(anyLong())).willReturn(stubResponse());
        willThrow(new EntrypointInvalidException(".. 시그먼트 금지"))
                .given(subprogramService).update(eq(7L), any());

        // SubprogramUpdateRequest : name, version, description, entrypointRelativePath
        mvc.perform(post("/management/subprogram/7/edit")
                        .param("name", "MyDriver")
                        .param("version", "1.2.3")
                        .param("description", "")
                        .param("entrypointRelativePath", "../etc/passwd"))
                .andExpect(status().isBadRequest());
    }

    private static SubprogramResponse stubResponse() {
        return new SubprogramResponse(
                7L,
                SubprogramKind.DRIVER,
                SubprogramKind.DRIVER.getDisplayName(),
                null,
                "MyDriver",
                "1.2.3",
                "/opt/subprogram/x",
                null,
                "manifest",
                1,
                1024L,
                "",
                com.example.serverprovision.global.marker.IntegrityStatus.NOT_VERIFIED,
                true,
                false,
                false,
                com.example.serverprovision.global.lifecycle.LifecycleStage.ACTIVE,
                false,
                false,
                false
        );
    }
}
