package com.example.serverprovision.global.systemsetting.controller;

import com.example.serverprovision.global.security.springsecurity.domain.Role;
import com.example.serverprovision.global.systemsetting.dto.response.SystemSettingResponse;
import com.example.serverprovision.global.systemsetting.enums.AllowedCidrViolation;
import com.example.serverprovision.global.systemsetting.enums.SystemSettingKey;
import com.example.serverprovision.global.systemsetting.exception.SystemSettingAccessDeniedException;
import com.example.serverprovision.global.systemsetting.service.SystemSettingService;
import com.example.serverprovision.global.systemsetting.vo.ProvisioningAllowedCidr;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.data.jpa.mapping.JpaMetamodelMappingContext;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

/** U7 — 시스템 설정 화면 · 저장의 HTTP 계층. Mocking 은 서비스까지, 검증기 · advice 는 실제 실행. */
@WebMvcTest(controllers = SystemSettingController.class)
class SystemSettingControllerTest {

    @Autowired MockMvc mvc;
    @MockitoBean SystemSettingService systemSettingService;
    @MockitoBean JpaMetamodelMappingContext jpaMetamodelMappingContext;
    @MockitoBean com.example.serverprovision.global.systemsetting.spi.ProvisioningRangeFollower rangeFollower;

    private static final String SAVE = "/system/settings/provisioning-allowed-range";

    private static SystemSettingResponse card(String value) {
        return new SystemSettingResponse(SystemSettingKey.PROVISIONING_ALLOWED_RANGE, "프로비저닝 허용 대역", value, Role.SUPER_ADMIN,
                value == null ? null : LocalDateTime.of(2026, 11, 16, 21, 0));
    }

    @Test
    @WithMockUser(roles = "SUPER_ADMIN")
    @DisplayName("GET — SUPER_ADMIN: 카드 · 현재 값 · 접근 권한 · 입력칸에 현재 값")
    void page_admin() throws Exception {
        given(systemSettingService.accessibleSettings(Set.of(Role.SUPER_ADMIN))).willReturn(List.of(card("10.1.1.0/24")));
        mvc.perform(get("/system/settings"))
                .andExpect(status().isOk())
                .andExpect(view().name("system/settings"))
                .andExpect(content().string(containsString("프로비저닝 허용 대역")))
                .andExpect(content().string(containsString("value=\"10.1.1.0/24\"")))
                .andExpect(content().string(containsString("최고 관리자")))
                .andExpect(content().string(not(containsString("볼 수 있는 설정이 없습니다"))));
    }

    @Test
    @WithMockUser(roles = "SUPER_ADMIN")
    @DisplayName("GET — 값이 없으면 '설정되지 않음'")
    void page_unset() throws Exception {
        given(systemSettingService.accessibleSettings(Set.of(Role.SUPER_ADMIN))).willReturn(List.of(card(null)));
        mvc.perform(get("/system/settings"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("설정되지 않음")));
    }

    @Test
    @WithMockUser(roles = "USER")
    @DisplayName("GET — USER: 카드 없음 · 빈 안내")
    void page_user() throws Exception {
        given(systemSettingService.accessibleSettings(Set.of(Role.USER))).willReturn(List.of());
        mvc.perform(get("/system/settings"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("이 계정으로 볼 수 있는 설정이 없습니다.")))
                .andExpect(content().string(not(containsString(SAVE))));
    }

    @Test
    @WithMockUser(roles = "SUPER_ADMIN")
    @DisplayName("POST — 성공: 302 + flash · 서비스에 파싱된 대역")
    void save_ok() throws Exception {
        mvc.perform(post(SAVE).param("cidr", " 10.1.1.0/24 "))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/system/settings"))
                .andExpect(flash().attribute("flashMessage", "프로비저닝 허용 대역을 저장했습니다."))
                .andExpect(flash().attributeCount(1));
        verify(systemSettingService).saveProvisioningAllowedRange(eq(ProvisioningAllowedCidr.parse("10.1.1.0/24")), eq(Set.of(Role.SUPER_ADMIN)));
    }

    @Test
    @WithMockUser(roles = "SUPER_ADMIN")
    @DisplayName("GET — 주의 flash 는 노랑 알림으로, PXE 네트워크 구성 링크와 함께 그린다(HF35)")
    void page_rendersReapplyWarning() throws Exception {
        given(systemSettingService.accessibleSettings(Set.of(Role.SUPER_ADMIN))).willReturn(List.of());
        mvc.perform(get("/system/settings").flashAttr("flashWarning", "dnsmasq 에 반영하려면 저장 및 적용을 다시 해야 합니다."))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("n-alert n-alert-warning")))
                .andExpect(content().string(containsString("href=\"/system/pxe-infra/network\"")));
    }

    @Test
    @WithMockUser(roles = "SUPER_ADMIN")
    @DisplayName("POST — 대역을 따라가는 설정이 재적용을 알리면 성공 flash 와 나눠 주의 flash 로 싣는다(HF35)")
    void save_appendsReapplyNotice() throws Exception {
        given(rangeFollower.reapplyNotice(ProvisioningAllowedCidr.parse("10.1.2.0/24")))
                .willReturn(java.util.Optional.of("dnsmasq 에 반영하려면 저장 및 적용을 다시 해야 합니다."));

        mvc.perform(post(SAVE).param("cidr", "10.1.2.0/24"))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attribute("flashMessage", "프로비저닝 허용 대역을 저장했습니다."))
                .andExpect(flash().attribute("flashWarning", "dnsmasq 에 반영하려면 저장 및 적용을 다시 해야 합니다."));
    }

    @ParameterizedTest(name = "{0} → {1}")
    @CsvSource(delimiter = '|', value = {
            "''              | EMPTY",
            "8.8.8.0/24      | NOT_PRIVATE",
            "10.0.0.0/7      | WIDER_THAN_BLOCK",
            "192.168.1.0/27  | TOO_NARROW",
            "10.1.1.5/24     | NOT_NETWORK_ADDRESS"
    })
    @WithMockUser(roles = "SUPER_ADMIN")
    @DisplayName("POST — 위반: 400 재렌더 + 필드 오류 문구 · 저장 안 함")
    void save_invalid(String cidr, AllowedCidrViolation violation) throws Exception {
        given(systemSettingService.accessibleSettings(any())).willReturn(List.of(card(null)));
        mvc.perform(post(SAVE).param("cidr", cidr))
                .andExpect(status().isBadRequest())
                .andExpect(view().name("system/settings"))
                .andExpect(content().string(containsString(org.springframework.web.util.HtmlUtils.htmlEscape(violation.message()))));
        verify(systemSettingService, never()).saveProvisioningAllowedRange(any(), anyCollection());
    }

    @Test
    @WithMockUser(roles = "USER")
    @DisplayName("POST — 권한 미달(direct POST): 값이 틀려도 403 (SystemSettingAccessDeniedException)")
    void save_forbidden() throws Exception {
        willThrow(new SystemSettingAccessDeniedException())
                .given(systemSettingService).assertAccessible(SystemSettingKey.PROVISIONING_ALLOWED_RANGE, Set.of(Role.USER));
        mvc.perform(post(SAVE).param("cidr", "8.8.8.0/24").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden())
                .andExpect(content().string(containsString("이 설정을 변경할 권한이 없습니다.")));
        verify(systemSettingService, never()).saveProvisioningAllowedRange(any(), anyCollection());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("POST — 권한 미달 + 네이티브 폼(HTML): 500 이 아니라 403 오류 화면 (CP5 F-1)")
    void save_forbidden_html() throws Exception {
        willThrow(new SystemSettingAccessDeniedException())
                .given(systemSettingService).assertAccessible(eq(SystemSettingKey.PROVISIONING_ALLOWED_RANGE), anyCollection());
        mvc.perform(post(SAVE).param("cidr", "10.1.1.0/24").accept(MediaType.TEXT_HTML))
                .andExpect(status().isForbidden())
                .andExpect(view().name("error"))
                .andExpect(content().string(containsString("이 설정을 변경할 권한이 없습니다.")));
        verify(systemSettingService, never()).saveProvisioningAllowedRange(any(), anyCollection());
    }
}
