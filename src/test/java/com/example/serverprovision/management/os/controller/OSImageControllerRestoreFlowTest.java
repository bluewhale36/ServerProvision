package com.example.serverprovision.management.os.controller;

import com.example.serverprovision.management.common.dto.response.RestoreResponse;
import com.example.serverprovision.management.common.filesystem.service.DirectoryBrowseService;
import com.example.serverprovision.management.os.exception.DuplicateOSImageException;
import com.example.serverprovision.management.os.service.CompsExtractionLauncher;
import com.example.serverprovision.management.os.service.IsoRegistrationLauncher;
import com.example.serverprovision.management.os.service.IsoUploadIntentService;
import com.example.serverprovision.management.os.service.IsoVerificationLauncher;
import com.example.serverprovision.management.os.service.OSImageService;
import com.example.serverprovision.management.os.enums.OSName;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.data.jpa.mapping.JpaMetamodelMappingContext;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * S5-2-3 — OS restore cascade 흐름 통합 테스트. 4 시나리오.
 *
 * <p>cascade 본체 (자식 ISO 일괄 복구) 검증은 service 단위 테스트 영역. 본 컨트롤러 테스트는
 * 시그니처 / 파라미터 전달 / 예외 매핑 회귀 차단을 검증.</p>
 */
@WebMvcTest(controllers = OSImageController.class)
class OSImageControllerRestoreFlowTest {
    @org.springframework.test.context.bean.override.mockito.MockitoBean com.example.serverprovision.global.trash.service.TypedNameVerifier typedNameVerifier;

    @Autowired MockMvc mvc;

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
    @DisplayName("OS restore cascade=true — 하위 ISO 2건 복구 → 302 redirect + service 위임")
    void restore_cascadeTrue_returns302() throws Exception {
        given(osImageService.restore(eq(1L), eq(true))).willReturn(new RestoreResponse(2));

        mvc.perform(post("/management/os/1/restore").param("cascade", "true"))
                .andExpect(status().is3xxRedirection());
    }

    @Test
    @DisplayName("OS restore cascade=false — 부모만 복구 → 302 redirect")
    void restore_cascadeFalse_returns302() throws Exception {
        given(osImageService.restore(eq(1L), eq(false))).willReturn(RestoreResponse.none());

        mvc.perform(post("/management/os/1/restore").param("cascade", "false"))
                .andExpect(status().is3xxRedirection());
    }

    @Test
    @DisplayName("OS restore — cascade 파라미터 누락 시 default false 로 service 호출")
    void restore_noCascadeParam_defaultsFalse() throws Exception {
        given(osImageService.restore(eq(1L), eq(false))).willReturn(RestoreResponse.none());

        mvc.perform(post("/management/os/1/restore"))
                .andExpect(status().is3xxRedirection());
    }

    @Test
    @DisplayName("OS restore cascade=true — 활성 (osName, osVersion) 충돌 → 409")
    void restore_cascade_duplicateOsName_returns409() throws Exception {
        willThrow(new DuplicateOSImageException(OSName.ROCKY_LINUX, "9.6"))
                .given(osImageService).restore(eq(1L), eq(true));

        mvc.perform(post("/management/os/1/restore").param("cascade", "true"))
                .andExpect(status().isConflict());
    }
}
