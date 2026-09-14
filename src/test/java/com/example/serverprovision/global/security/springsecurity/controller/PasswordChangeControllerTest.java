package com.example.serverprovision.global.security.springsecurity.controller;

import com.example.serverprovision.global.security.springsecurity.authorization.SessionPrincipalRefresher;
import com.example.serverprovision.global.security.springsecurity.exception.PasswordMismatchException;
import com.example.serverprovision.global.security.springsecurity.service.UsersService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.data.jpa.mapping.JpaMetamodelMappingContext;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

/** S18 D-15 — 비밀번호 변경 화면 · 제출: 강제 안내 · 검증 재렌더 · 현재 비밀번호 오입력 필드 매핑 · 성공 시 principal 갱신 후 / 로. */
@WebMvcTest(controllers = PasswordChangeController.class)
@WithMockUser(username = "hong.gildong")
class PasswordChangeControllerTest {

	@Autowired MockMvc mvc;
	@MockitoBean UsersService usersService;
	@MockitoBean SessionPrincipalRefresher principalRefresher;
	@MockitoBean JpaMetamodelMappingContext jpaMetamodelMappingContext;

	@Test
	@DisplayName("GET — ?first=1 이면 강제 안내 배너 · 없으면 배너 없음")
	void form_forcedBanner() throws Exception {
		mvc.perform(get("/password/change").param("first", "1"))
				.andExpect(status().isOk())
				.andExpect(view().name("security/password-change"))
				.andExpect(model().attribute("forced", true))
				.andExpect(content().string(containsString("초기 비밀번호로 로그인했습니다")));
		mvc.perform(get("/password/change"))
				.andExpect(status().isOk())
				.andExpect(model().attribute("forced", false))
				.andExpect(content().string(org.hamcrest.Matchers.not(containsString("초기 비밀번호로 로그인했습니다"))));
	}

	@Test
	@DisplayName("400 — 빈 제출 3 필드 · 새 비밀번호 불일치 · 현재와 같은 값 · 규칙 위반 · 서비스 미호출")
	void submit_validation() throws Exception {
		mvc.perform(post("/password/change").param("currentPassword", "").param("newPassword", "").param("retypedPassword", ""))
				.andExpect(status().isBadRequest())
				.andExpect(model().attributeHasFieldErrors("passwordForm", "currentPassword", "newPassword", "retypedPassword"))
				.andExpect(model().attributeErrorCount("passwordForm", 3));
		mvc.perform(post("/password/change").param("currentPassword", "Init1234!").param("newPassword", "Init1234!").param("retypedPassword", "Other99!"))
				.andExpect(status().isBadRequest())
				.andExpect(model().attributeHasFieldErrors("passwordForm", "newPasswordConfirmed", "newPasswordDifferent"));
		verify(usersService, never()).changePassword(any(), any());
	}

	@Test
	@DisplayName("400 — 현재 비밀번호 오입력은 서비스 예외가 currentPassword 필드로 매핑돼 재렌더")
	void submit_currentWrong() throws Exception {
		willThrow(PasswordMismatchException.current()).given(usersService).changePassword(eq("hong.gildong"), any());
		mvc.perform(post("/password/change").param("first", "1").param("currentPassword", "wrong").param("newPassword", "NewPass99!").param("retypedPassword", "NewPass99!"))
				.andExpect(status().isBadRequest())
				.andExpect(model().attributeHasFieldErrors("passwordForm", "currentPassword"))
				.andExpect(model().attribute("forced", true))
				.andExpect(content().string(containsString("현재 비밀번호가 올바르지 않습니다.")));
		verify(principalRefresher, never()).refresh(any(), any(), any());
	}

	@Test
	@DisplayName("302 — 성공: 서비스 변경 → 세션 principal 갱신 → 서버 목록으로 flash(CP5 F-1: / 는 재리다이렉트로 flash 소실)")
	void submit_success() throws Exception {
		mvc.perform(post("/password/change").param("currentPassword", "Init1234!").param("newPassword", "NewPass99!").param("retypedPassword", "NewPass99!"))
				.andExpect(status().is3xxRedirection())
				.andExpect(redirectedUrl("/provisioning/server"))
				.andExpect(flash().attribute("flashMessage", "비밀번호를 바꿨습니다."));
		verify(usersService).changePassword(eq("hong.gildong"), any());
		verify(principalRefresher).refresh(eq("hong.gildong"), any(), any());
	}
}
