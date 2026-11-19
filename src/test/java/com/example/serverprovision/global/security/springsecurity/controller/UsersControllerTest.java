package com.example.serverprovision.global.security.springsecurity.controller;

import com.example.serverprovision.global.security.springsecurity.domain.SignupMode;
import com.example.serverprovision.global.security.springsecurity.entity.Users;
import com.example.serverprovision.global.security.springsecurity.exception.DuplicateUsernameException;
import com.example.serverprovision.global.security.springsecurity.service.UsersService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.mapping.JpaMetamodelMappingContext;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
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

/**
 * S18 · MA8 — 최초 계정 등록(/signup) 화면 · 제출의 HTTP 계층. MA8 부터 /signup 은 부트스트랩 전용이다(관리 생성은
 * {@code UserManagementControllerTest}). 인가(SignupAccessPolicy)는 필터 몫이라 여기 없다 — 검증 재렌더 · 중복 1차 차단 ·
 * 안전망 예외의 필드 매핑 · 성공 이동을 본다. Mocking 은 서비스까지.
 */
@WebMvcTest(controllers = UsersController.class)
@Import(SignupSubmission.class)
class UsersControllerTest {

	@Autowired MockMvc mvc;
	@MockitoBean UsersService usersService;
	@MockitoBean JpaMetamodelMappingContext jpaMetamodelMappingContext;

	// ==== 화면 ====================================================

	@Test
	@DisplayName("GET /signup — 부트스트랩 뷰(등급 선택 없음 · 최고 관리자 안내 · 제출 대상 /signup)")
	void form_bootstrap() throws Exception {
		mvc.perform(get("/signup"))
				.andExpect(status().isOk())
				.andExpect(view().name("security/signup-bootstrap"))
				.andExpect(model().attribute("signupMode", SignupMode.BOOTSTRAP))
				.andExpect(content().string(containsString("최초 관리자 등록")))
				.andExpect(content().string(containsString("최고 관리자가 됩니다")))
				.andExpect(content().string(containsString("action=\"/signup\"")))
				.andExpect(content().string(org.hamcrest.Matchers.not(containsString("role-ADMIN"))));
	}

	// ==== 400 — 검증 재렌더 ====================================================

	@Test
	@DisplayName("400 — 빈 제출: 아이디 · 비밀번호 · 확인 · 이름 4 필드에 각 1 메시지(부트스트랩은 등급을 묻지 않는다) · 서비스 미호출")
	void submit_blank_fourFieldErrors() throws Exception {
		mvc.perform(post("/signup").param("username", "").param("password", "").param("retypedPassword", "").param("name", ""))
				.andExpect(status().isBadRequest())
				.andExpect(view().name("security/signup-bootstrap"))
				.andExpect(model().attributeHasFieldErrors("signUpForm", "username", "password", "retypedPassword", "name"))
				.andExpect(model().attributeErrorCount("signUpForm", 4));
		verify(usersService, never()).signup(any(), any());
	}

	@Test
	@DisplayName("400 — 비밀번호 규칙 위반(한글) · 불일치(passwordConfirmed) · 아이디 65자")
	void submit_ruleViolations() throws Exception {
		mvc.perform(post("/signup").param("username", "a".repeat(65)).param("password", "한글비밀번호1!")
						.param("retypedPassword", "Other1234!").param("name", "홍길동"))
				.andExpect(status().isBadRequest())
				.andExpect(view().name("security/signup-bootstrap"))
				.andExpect(model().attributeHasFieldErrors("signUpForm", "username", "password", "passwordConfirmed"))
				.andExpect(content().string(containsString("비밀번호가 일치하지 않습니다.")))
				.andExpect(content().string(containsString("아이디는 64자 이하로 입력해야 합니다.")));
	}

	@Test
	@DisplayName("400 — 중복 아이디는 usernameTaken 으로 먼저 막는다(UI 1차 차단) · 서비스 미호출")
	void submit_duplicate_blockedBeforeService() throws Exception {
		given(usersService.usernameTaken("admin")).willReturn(true);
		mvc.perform(post("/signup").param("username", "admin").param("password", "Passw0rd!")
						.param("retypedPassword", "Passw0rd!").param("name", "관리자"))
				.andExpect(status().isBadRequest())
				.andExpect(model().attributeHasFieldErrors("signUpForm", "username"))
				.andExpect(content().string(containsString("이미 사용 중인 아이디입니다.")));
		verify(usersService, never()).signup(any(), any());
	}

	// ==== 409 — 안전망 ====================================================

	@Test
	@DisplayName("409 — 레이스로 서비스가 DuplicateUsernameException 을 내면 username 필드 오류로 재렌더")
	void submit_raceDuplicate_409() throws Exception {
		given(usersService.usernameTaken("admin")).willReturn(false);
		given(usersService.signup(any(), eq(SignupMode.BOOTSTRAP))).willThrow(new DuplicateUsernameException("admin"));
		mvc.perform(post("/signup").param("username", "admin").param("password", "Passw0rd!")
						.param("retypedPassword", "Passw0rd!").param("name", "관리자"))
				.andExpect(status().isConflict())
				.andExpect(view().name("security/signup-bootstrap"))
				.andExpect(model().attributeHasFieldErrors("signUpForm", "username"));
	}

	// ==== 성공 ====================================================

	@Test
	@DisplayName("302 — 부트스트랩 가입은 /login 으로 · flash 문구")
	void submit_success() throws Exception {
		Users saved = Users.builder().id(1L).username("admin").name("관리자").build();
		given(usersService.signup(any(), eq(SignupMode.BOOTSTRAP))).willReturn(saved);
		mvc.perform(post("/signup").param("username", "admin").param("password", "Passw0rd!")
						.param("retypedPassword", "Passw0rd!").param("name", "관리자"))
				.andExpect(status().is3xxRedirection())
				.andExpect(redirectedUrl("/login"))
				.andExpect(flash().attribute("flashMessage", containsString("최초 관리자")));
	}
}
