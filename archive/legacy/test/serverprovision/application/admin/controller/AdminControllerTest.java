package com.example.serverprovision.application.admin.controller;

import com.example.serverprovision.application.setting.service.SettingService;
import com.example.serverprovision.domain.node.service.ServerNodeService;
import com.example.serverprovision.domain.os.service.OSMetadataService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.data.jpa.mapping.JpaMetamodelMappingContext;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.BDDMockito.willDoNothing;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * {@link AdminController#assignSetting} MockMvc 슬라이스 테스트.
 *
 * <p>{@code @WebMvcTest}로 Controller 레이어만 로드하며,
 * Service 의존성은 {@code @MockitoBean}으로 대체한다.</p>
 */
@WebMvcTest(AdminController.class)
class AdminControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ServerNodeService serverNodeService;

    @MockitoBean
    private OSMetadataService osMetadataService;

    @MockitoBean
    private SettingService settingService;

    /**
     * Spring Boot 4.x + JPA 슬라이스 테스트에서 JPA Metamodel 빈이 누락되는 문제를 방지한다.
     * 기존 PXEBootRestControllerTest 패턴을 따른다.
     */
    @MockitoBean
    private JpaMetamodelMappingContext jpaMetamodelMappingContext;

    // =========================================================================
    // POST /pxe/v1/admin/nodes/{nodeId}/assign-setting
    // =========================================================================

    @Nested
    @DisplayName("POST /pxe/v1/admin/nodes/{nodeId}/assign-setting")
    class AssignSettingEndpoint {

        @Test
        @DisplayName("정상 할당 시 대시보드로 리다이렉트하고 successMessage 플래시 속성이 포함된다")
        void assignSetting_success_redirectsWithSuccessMessage() throws Exception {
            // given
            willDoNothing().given(serverNodeService).assignSetting(1L, 100L);

            // when & then
            mockMvc.perform(post("/pxe/v1/admin/nodes/1/assign-setting")
                            .param("settingId", "100"))
                    .andExpect(status().is3xxRedirection())
                    .andExpect(redirectedUrl("/pxe/v1/admin/dashboard"))
                    .andExpect(flash().attribute("successMessage", "세팅 할당이 완료되었습니다."));

            verify(serverNodeService).assignSetting(1L, 100L);
        }

        @Test
        @DisplayName("서비스에서 IllegalArgumentException 발생 시 대시보드로 리다이렉트하고 errorMessage 플래시 속성이 포함된다")
        void assignSetting_serviceThrows_redirectsWithErrorMessage() throws Exception {
            // given
            willThrow(new IllegalArgumentException("서버 노드를 찾을 수 없습니다. ID: 999"))
                    .given(serverNodeService).assignSetting(999L, 100L);

            // when & then
            mockMvc.perform(post("/pxe/v1/admin/nodes/999/assign-setting")
                            .param("settingId", "100"))
                    .andExpect(status().is3xxRedirection())
                    .andExpect(redirectedUrl("/pxe/v1/admin/dashboard"))
                    .andExpect(flash().attribute("errorMessage", "서버 노드를 찾을 수 없습니다. ID: 999"));
        }

        @Test
        @DisplayName("세팅 미존재 시 errorMessage 에 세팅 관련 메시지가 포함된다")
        void assignSetting_settingNotFound_redirectsWithSettingError() throws Exception {
            // given
            willThrow(new IllegalArgumentException("세팅 주문서를 찾을 수 없습니다. ID: 999"))
                    .given(serverNodeService).assignSetting(1L, 999L);

            // when & then
            mockMvc.perform(post("/pxe/v1/admin/nodes/1/assign-setting")
                            .param("settingId", "999"))
                    .andExpect(status().is3xxRedirection())
                    .andExpect(redirectedUrl("/pxe/v1/admin/dashboard"))
                    .andExpect(flash().attribute("errorMessage", "세팅 주문서를 찾을 수 없습니다. ID: 999"));
        }

        @Test
        @DisplayName("settingId 파라미터 누락 시 400 응답")
        void assignSetting_missingSettingId_returns400() throws Exception {
            mockMvc.perform(post("/pxe/v1/admin/nodes/1/assign-setting"))
                    .andExpect(status().isBadRequest());
        }
    }
}
