package com.example.serverprovision.global.ui.controller;

import com.example.serverprovision.global.marker.ResourceType;
import com.example.serverprovision.global.trash.service.TypedNameVerifier;
import com.example.serverprovision.global.ui.exception.ModalContextNotFoundException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.data.jpa.mapping.JpaMetamodelMappingContext;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * S5-6-1 — modal lazy-load endpoint 검증. PURGE modalType 1 종 (Phase A 범위).
 *
 * <p>endpoint <code>/ui/confirm-modal/{modalType}?resourceType=...&amp;resourceId=...</code> 가 :
 * <ul>
 *   <li>정상 자원 — 200 + modal fragment HTML 응답 (expected value 포함)</li>
 *   <li>자원 부재 — {@link ModalContextNotFoundException} → 404</li>
 *   <li>잘못된 enum — Spring 의 자동 enum conversion fail → 400</li>
 * </ul>
 */
@WebMvcTest(controllers = ConfirmModalFragmentController.class)
class ConfirmModalFragmentControllerTest {

    @Autowired
    MockMvc mvc;

    @MockitoBean
    TypedNameVerifier typedNameVerifier;

    @MockitoBean
    JpaMetamodelMappingContext jpaMetamodelMappingContext;

    @Test
    @DisplayName("PURGE — 정상 자원 → 200 + expected value 포함 fragment")
    void renders_purge_modal_with_expected_value() throws Exception {
        given(typedNameVerifier.resolveExpectedName(eq(ResourceType.BIOS_BUNDLE), eq(5L)))
                .willReturn("R23_MS73-HB1_Uni");

        mvc.perform(get("/ui/confirm-modal/PURGE")
                        .param("resourceType", "BIOS_BUNDLE")
                        .param("resourceId", "5"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("R23_MS73-HB1_Uni")))
                .andExpect(content().string(containsString("data-modal-expected")))
                .andExpect(content().string(containsString("data-modal-typed-input")))
                .andExpect(content().string(containsString("data-modal-confirm")));
    }

    @Test
    @DisplayName("PURGE — 자원 부재 → 404 (ModalContextNotFoundException)")
    void returns_404_for_missing_resource() throws Exception {
        willThrow(new ModalContextNotFoundException(ResourceType.BIOS_BUNDLE, 999L))
                .given(typedNameVerifier).resolveExpectedName(eq(ResourceType.BIOS_BUNDLE), eq(999L));

        mvc.perform(get("/ui/confirm-modal/PURGE")
                        .param("resourceType", "BIOS_BUNDLE")
                        .param("resourceId", "999"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("잘못된 modalType — 400")
    void returns_400_for_unknown_modalType() throws Exception {
        mvc.perform(get("/ui/confirm-modal/UNKNOWN_TYPE")
                        .param("resourceType", "BIOS_BUNDLE")
                        .param("resourceId", "5"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("잘못된 resourceType — 400")
    void returns_400_for_unknown_resourceType() throws Exception {
        mvc.perform(get("/ui/confirm-modal/PURGE")
                        .param("resourceType", "NOT_A_TYPE")
                        .param("resourceId", "5"))
                .andExpect(status().isBadRequest());
    }

    // ==== S5-6-2 : typed-name 없는 modal 의 lazy-load (자원 lookup 없음) ====

    @Test
    @DisplayName("SOFT_DELETE — 자원 lookup 없이 fragment 응답")
    void renders_soft_delete_modal() throws Exception {
        mvc.perform(get("/ui/confirm-modal/SOFT_DELETE")
                        .param("resourceType", "BIOS_BUNDLE")
                        .param("resourceId", "5"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("data-modal-active")))
                .andExpect(content().string(containsString("data-modal-confirm")));
    }

    @Test
    @DisplayName("DEPRECATE — 자원 lookup 없이 fragment 응답")
    void renders_deprecate_modal() throws Exception {
        mvc.perform(get("/ui/confirm-modal/DEPRECATE")
                        .param("resourceType", "BIOS_BUNDLE")
                        .param("resourceId", "5"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Deprecated")))
                .andExpect(content().string(containsString("data-modal-active")));
    }

    @Test
    @DisplayName("RESTORE — cascade 라디오 slot 포함 fragment")
    void renders_restore_modal_with_cascade_slot() throws Exception {
        mvc.perform(get("/ui/confirm-modal/RESTORE")
                        .param("resourceType", "BIOS_BUNDLE")
                        .param("resourceId", "5"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("data-modal-cascade-wrap")))
                .andExpect(content().string(containsString("data-modal-cascade-true")));
    }

    // ==== R9-3 : reconciliation 드리프트 확인 modal (자원 lookup 없음) ====

    @Test
    @DisplayName("DRIFT_APPLY — 자원 lookup 없이 '드리프트 적용' fragment 응답")
    void renders_drift_apply_modal() throws Exception {
        mvc.perform(get("/ui/confirm-modal/DRIFT_APPLY")
                        .param("resourceType", "OS_ISO")
                        .param("resourceId", "42"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("드리프트 적용")))
                .andExpect(content().string(containsString("data-modal-active")))
                .andExpect(content().string(containsString("data-modal-confirm")));
    }

    @Test
    @DisplayName("DRIFT_DISMISS — 자원 lookup 없이 '보고 닫기' fragment 응답")
    void renders_drift_dismiss_modal() throws Exception {
        mvc.perform(get("/ui/confirm-modal/DRIFT_DISMISS")
                        .param("resourceType", "OS_ISO")
                        .param("resourceId", "42"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("보고 닫기")))
                .andExpect(content().string(containsString("data-modal-active")))
                .andExpect(content().string(containsString("data-modal-confirm")));
    }

    // ==== S5-6-3 : trash-result-modal 의 lazy-load (자원 lookup / param 무관) ====

    @Test
    @DisplayName("TRASH_RESULT — 자원 lookup 없이 결과 안내 fragment 응답")
    void renders_trash_result_modal() throws Exception {
        mvc.perform(get("/ui/confirm-modal/TRASH_RESULT")
                        .param("resourceType", "OS_IMAGE")
                        .param("resourceId", "0"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("data-modal-title")))
                .andExpect(content().string(containsString("data-modal-message")))
                .andExpect(content().string(containsString("data-modal-hint")));
    }
}
