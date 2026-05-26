package com.example.serverprovision.management.os.controller;

import com.example.serverprovision.global.exception.TypedNameMismatchException;
import com.example.serverprovision.management.common.filesystem.service.DirectoryBrowseService;
import com.example.serverprovision.management.os.service.OSNudgeService;
import com.example.serverprovision.management.os.service.comps.CompsExtractionLauncher;
import com.example.serverprovision.management.os.service.iso.IsoRegistrationLauncher;
import com.example.serverprovision.management.os.service.iso.IsoUploadIntentService;
import com.example.serverprovision.management.os.service.iso.IsoVerificationLauncher;
import com.example.serverprovision.management.os.service.metadata.OSMetadataService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.data.jpa.mapping.JpaMetamodelMappingContext;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.willDoNothing;
import static org.mockito.BDDMockito.willThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * S5-2-2 — OS / ISO 영구 삭제 (purge) 의 typed-name 검증 흐름 통합 테스트.
 *
 * <p>4 시나리오 — OS 일치 / 불일치, ISO 일치 / 불일치.</p>
 */
@WebMvcTest(controllers = { OSMetadataController.class, IsoLifecycleController.class })
class OSMetadataControllerPurgeFlowTest {
    @org.springframework.test.context.bean.override.mockito.MockitoBean com.example.serverprovision.global.trash.service.TypedNameVerifier typedNameVerifier;

    @Autowired MockMvc mvc;

    @MockitoBean OSMetadataService osMetadataService;
    @MockitoBean com.example.serverprovision.management.os.service.metadata.OSMetadataLifecycleService osMetadataLifecycleService;
    @MockitoBean OSNudgeService osNudgeService;
    @MockitoBean com.example.serverprovision.management.os.service.metadata.OSMetadataNudgeService osMetadataNudgeService;
    @MockitoBean CompsExtractionLauncher compsExtractionLauncher;
    @MockitoBean IsoUploadIntentService isoUploadIntentService;
    @MockitoBean IsoVerificationLauncher isoVerificationLauncher;
    @MockitoBean IsoRegistrationLauncher isoRegistrationLauncher;
    @MockitoBean DirectoryBrowseService directoryBrowseService;
    @MockitoBean com.example.serverprovision.global.lifecycle.DeleteIntentRegistry deleteIntentRegistry;
    @MockitoBean JpaMetamodelMappingContext jpaMetamodelMappingContext;

    @Test
    @DisplayName("OS purge — typedName 일치 → 302 redirect, includeDeleted 보존")
    void purgeOs_typedNameMatches_returns302() throws Exception {
        willDoNothing().given(osMetadataLifecycleService)
                .purgeWithTypedNameCheck(eq(1L), eq("Rocky Linux 9.6"));

        mvc.perform(post("/management/os/1/purge").param("typedName", "Rocky Linux 9.6"))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location", "/management/os?includeDeleted=true"));
    }

    @Test
    @DisplayName("OS purge — typedName 불일치 → 400 (TypedNameMismatchException)")
    void purgeOs_typedNameMismatch_returns400() throws Exception {
        willThrow(new TypedNameMismatchException("Rocky Linux 9.6", "wrong"))
                .given(osMetadataLifecycleService)
                .purgeWithTypedNameCheck(eq(1L), eq("wrong"));

        mvc.perform(post("/management/os/1/purge").param("typedName", "wrong"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("ISO purge — typedName 일치 → 302 redirect, selectId 보존")
    void purgeIso_typedNameMatches_returns302() throws Exception {
        willDoNothing().given(osMetadataService)
                .purgeIsoWithTypedNameCheck(eq(1L), eq(7L), eq("Rocky Linux 9.6 dvd.iso"));

        mvc.perform(post("/management/os/1/iso/7/purge")
                        .param("typedName", "Rocky Linux 9.6 dvd.iso"))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location", "/management/os?selectId=1"));
    }

    @Test
    @DisplayName("ISO purge — typedName 불일치 → 400")
    void purgeIso_typedNameMismatch_returns400() throws Exception {
        willThrow(new TypedNameMismatchException("Rocky Linux 9.6 dvd.iso", "x"))
                .given(osMetadataService)
                .purgeIsoWithTypedNameCheck(eq(1L), eq(7L), eq("x"));

        mvc.perform(post("/management/os/1/iso/7/purge").param("typedName", "x"))
                .andExpect(status().isBadRequest());
    }
}
