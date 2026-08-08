package com.example.serverprovision.provisioning.setting.controller;

import com.example.serverprovision.global.exception.TypedNameMismatchException;
import com.example.serverprovision.provisioning.setting.exception.RestoreNameConflictException;
import com.example.serverprovision.provisioning.setting.exception.SettingNotFoundException;
import com.example.serverprovision.provisioning.setting.service.SettingCommandService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.data.jpa.mapping.JpaMetamodelMappingContext;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * U3-2-b CP4 — {@link SettingLifecycleController} SSR lifecycle(삭제 + 활성 · 사용 중단 권고) 통합 테스트(4 범주).
 * Mocking 은 {@link SettingCommandService} 까지만 — 컨트롤러 redirect + advice(400/404/409) 매핑은 실제 실행된다.
 * 삭제는 차단이 아니라 경고(DEC-C)라 <b>정상 삭제 경로에는 예외가 0</b>이고, 4xx 는 stale/입력오류/direct POST 안전망이다.
 */
@WebMvcTest(controllers = SettingLifecycleController.class)
class SettingLifecycleControllerFlowTest {

    @Autowired MockMvc mvc;

    @MockitoBean SettingCommandService commandService;
    @MockitoBean JpaMetamodelMappingContext jpaMetamodelMappingContext;

    // ==== 성공 2xx (302 redirect) ====================================

    @Test
    @DisplayName("POST /{id}/delete — soft-delete 후 상세로 302 (경고 count 는 상세 응답이 노출)")
    void delete_redirectsToDetail() throws Exception {
        mvc.perform(post("/provisioning/setting/{id}/delete", 1L))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/provisioning/setting/1"));

        verify(commandService).softDelete(1L);
    }

    @Test
    @DisplayName("POST /{id}/restore — 복원 후 상세로 302")
    void restore_redirectsToDetail() throws Exception {
        mvc.perform(post("/provisioning/setting/{id}/restore", 1L))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/provisioning/setting/1"));

        verify(commandService).restore(1L);
    }

    @Test
    @DisplayName("POST /{id}/purge — typedName 전달 + 휴지통 목록으로 302")
    void purge_redirectsToTrashList() throws Exception {
        mvc.perform(post("/provisioning/setting/{id}/purge", 1L).param("typedName", "표준 세팅"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/provisioning/setting?includeDeleted=true"));

        verify(commandService).purge(1L, "표준 세팅");
    }

    @Test
    @DisplayName("POST /{id}/toggle — 활성 반전 후 상세로 302 (U3-2-b DEC-G)")
    void toggleEnabled_redirectsToDetail() throws Exception {
        mvc.perform(post("/provisioning/setting/{id}/toggle", 1L))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/provisioning/setting/1"));

        verify(commandService).toggleEnabled(1L);
    }

    @Test
    @DisplayName("POST /{id}/deprecate — 사용 중단 권고 후 상세로 302")
    void deprecate_redirectsToDetail() throws Exception {
        mvc.perform(post("/provisioning/setting/{id}/deprecate", 1L))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/provisioning/setting/1"));

        verify(commandService).deprecate(1L);
    }

    @Test
    @DisplayName("POST /{id}/undeprecate — 권고 해제 후 상세로 302")
    void undeprecate_redirectsToDetail() throws Exception {
        mvc.perform(post("/provisioning/setting/{id}/undeprecate", 1L))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/provisioning/setting/1"));

        verify(commandService).undeprecate(1L);
    }

    // ==== 400 — purge typed-name 불일치 ==============================

    @Test
    @DisplayName("POST /{id}/purge — typedName 불일치 → TypedNameMismatchException 400 (advice)")
    void purge_typedNameMismatch_returns400() throws Exception {
        willThrow(new TypedNameMismatchException("표준 세팅", "틀린 이름"))
                .given(commandService).purge(eq(1L), eq("틀린 이름"));

        mvc.perform(post("/provisioning/setting/{id}/purge", 1L)
                        .param("typedName", "틀린 이름").accept(MediaType.TEXT_HTML))
                .andExpect(status().isBadRequest());
    }

    // ==== 404 — 없는/부적절 정의서 (forging) =========================

    @Test
    @DisplayName("POST /{id}/delete — 없는 정의서 → SettingNotFound 404 (advice, forging)")
    void delete_notFound_returns404() throws Exception {
        willThrow(new SettingNotFoundException(99L)).given(commandService).softDelete(99L);

        mvc.perform(post("/provisioning/setting/{id}/delete", 99L).accept(MediaType.TEXT_HTML))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("POST /{id}/purge — soft-delete 미선행(활성/부재) → SettingNotFound 404 (DEC-E, forging)")
    void purge_notSoftDeleted_returns404() throws Exception {
        willThrow(new SettingNotFoundException(77L)).given(commandService).purge(eq(77L), any());

        mvc.perform(post("/provisioning/setting/{id}/purge", 77L)
                        .param("typedName", "무엇이든").accept(MediaType.TEXT_HTML))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("POST /{id}/toggle — soft-deleted/없는 정의서 → SettingNotFound 404 (활성 전용 대상, forging)")
    void toggleEnabled_notActive_returns404() throws Exception {
        willThrow(new SettingNotFoundException(88L)).given(commandService).toggleEnabled(88L);

        mvc.perform(post("/provisioning/setting/{id}/toggle", 88L).accept(MediaType.TEXT_HTML))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("POST /{id}/deprecate — soft-deleted/없는 정의서 → SettingNotFound 404 (활성 전용 대상, forging)")
    void deprecate_notActive_returns404() throws Exception {
        willThrow(new SettingNotFoundException(88L)).given(commandService).deprecate(88L);

        mvc.perform(post("/provisioning/setting/{id}/deprecate", 88L).accept(MediaType.TEXT_HTML))
                .andExpect(status().isNotFound());
    }

    // ==== 409 — 복원 name 충돌 =======================================

    @Test
    @DisplayName("POST /{id}/restore — 같은 이름의 활성 정의서 존재 → RestoreNameConflict 409 (advice 실트리거)")
    void restore_nameConflict_returns409() throws Exception {
        willThrow(new RestoreNameConflictException("표준 세팅")).given(commandService).restore(1L);

        mvc.perform(post("/provisioning/setting/{id}/restore", 1L).accept(MediaType.TEXT_HTML))
                .andExpect(status().isConflict());
    }
}
