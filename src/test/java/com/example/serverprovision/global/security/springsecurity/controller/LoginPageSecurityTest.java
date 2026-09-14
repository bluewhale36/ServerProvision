package com.example.serverprovision.global.security.springsecurity.controller;

import com.example.serverprovision.global.security.springsecurity.authorization.MustChangePasswordSuccessHandler;
import com.example.serverprovision.global.security.springsecurity.authorization.SignupAccessPolicy;
import com.example.serverprovision.global.security.springsecurity.config.PasswordEncoderConfig;
import com.example.serverprovision.global.security.springsecurity.config.SecurityConfig;
import com.example.serverprovision.global.security.springsecurity.domain.Role;
import com.example.serverprovision.global.security.springsecurity.domain.UserDetails_Impl;
import com.example.serverprovision.global.security.springsecurity.entity.Users;
import com.example.serverprovision.global.security.springsecurity.service.UsersService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.mapping.JpaMetamodelMappingContext;
import org.springframework.http.MediaType;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * S18 — 인증 경계의 최소 회귀: 실제 SecurityFilterChain(SecurityConfig)을 MockMvc 에 얹어 익명 · USER · ADMIN 이 어디까지 가는지와
 * 초기 비밀번호 계정의 로그인 목적지를 본다. 인증 판정 자체(UserDetailsService · 인코더)는 실물 인코더 + mock 사용자다.
 */
@WebMvcTest(controllers = LoginController.class)
@Import({SecurityConfig.class, PasswordEncoderConfig.class, SignupAccessPolicy.class, MustChangePasswordSuccessHandler.class})
class LoginPageSecurityTest {

	@Autowired WebApplicationContext context;
	@Autowired PasswordEncoder passwordEncoder;
	@MockitoBean UsersService usersService;
	@MockitoBean UserDetailsService userDetailsService;
	@MockitoBean JpaMetamodelMappingContext jpaMetamodelMappingContext;

	private MockMvc mvc;

	@BeforeEach
	void setUp() {
		mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
	}

	private UserDetails_Impl user(String password, boolean mustChange, Role... roles) {
		Users u = Users.builder().id(1L).username("hong.gildong").password(passwordEncoder.encode(password)).name("홍길동")
				.isEnabled(true).isLocked(false).mustChangePassword(mustChange).build();
		return new UserDetails_Impl(u, List.of(roles));
	}

	@Test
	@DisplayName("익명 — /login 200(자산 링크 · 부트스트랩이면 등록 링크) · 보호 화면은 302 /login · 정적 자산은 통과")
	void anonymous_boundary() throws Exception {
		given(usersService.bootstrapOpen()).willReturn(true);
		mvc.perform(get("/login").accept(MediaType.TEXT_HTML))
				.andExpect(status().isOk())
				.andExpect(content().string(containsString("/global/auth.css")))
				.andExpect(content().string(containsString("href=\"/signup\"")));
		given(usersService.bootstrapOpen()).willReturn(false);
		mvc.perform(get("/login").accept(MediaType.TEXT_HTML))
				.andExpect(content().string(org.hamcrest.Matchers.not(containsString("href=\"/signup\""))));

		mvc.perform(get("/management/os").accept(MediaType.TEXT_HTML))
				.andExpect(status().is3xxRedirection())
				.andExpect(redirectedUrl("/login"));
		mvc.perform(get("/global/style.css")).andExpect(status().isOk());
	}

	@Test
	@DisplayName("세션 없는 XHR(X-Requested-With) 은 httpBasic 401 이 아니라 302 /login — XhrRedirectFilter 가 브라우저 이동으로 바꾼다(CP5 C3)")
	void anonymous_xhr_redirectsToLogin() throws Exception {
		mvc.perform(post("/system/windows-install/oem-sync")
						.header("X-Requested-With", "XMLHttpRequest")
						.accept("application/json,text/html;q=0.9,*/*;q=0.5"))
				.andExpect(status().is3xxRedirection())
				.andExpect(redirectedUrl("/login"));
		mvc.perform(get("/management/os").accept(MediaType.ALL))   // 비브라우저(curl 류)는 종전대로 Basic 401
				.andExpect(status().isUnauthorized());
	}

	@Test
	@DisplayName("/signup 인가 — 부트스트랩 열림: 익명 통과 / 닫힘: 익명 302 · USER 403 · ADMIN 통과")
	void signup_authorization() throws Exception {
		given(usersService.bootstrapOpen()).willReturn(true);
		mvc.perform(get("/signup").accept(MediaType.TEXT_HTML)).andExpect(status().isNotFound());   // 통과 → 이 슬라이스에 UsersController 가 없어 404

		given(usersService.bootstrapOpen()).willReturn(false);
		mvc.perform(get("/signup").accept(MediaType.TEXT_HTML))
				.andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/login"));
	}

	@Test
	@WithMockUser(roles = "USER")
	@DisplayName("/signup 인가 — 닫힘 + ROLE_USER 는 403")
	void signup_userForbidden() throws Exception {
		given(usersService.bootstrapOpen()).willReturn(false);
		mvc.perform(get("/signup").accept(MediaType.TEXT_HTML)).andExpect(status().isForbidden());
	}

	@Test
	@WithMockUser(roles = {"ADMIN", "USER"})
	@DisplayName("/signup 인가 — 닫힘 + ROLE_ADMIN 은 통과")
	void signup_adminAllowed() throws Exception {
		given(usersService.bootstrapOpen()).willReturn(false);
		mvc.perform(get("/signup").accept(MediaType.TEXT_HTML)).andExpect(status().isNotFound());
	}

	@Test
	@DisplayName("로그인 목적지 — 초기 비밀번호 계정은 /password/change?first=1 · 일반 계정은 / · 틀린 비밀번호는 /login?error=true")
	void login_destinations() throws Exception {
		given(userDetailsService.loadUserByUsername("hong.gildong")).willReturn(user("Init1234!", true, Role.USER));
		mvc.perform(post("/login").param("username", "hong.gildong").param("password", "Init1234!"))
				.andExpect(status().is3xxRedirection())
				.andExpect(redirectedUrl("/password/change?first=1"));

		given(userDetailsService.loadUserByUsername("hong.gildong")).willReturn(user("NewPass99!", false, Role.USER));
		mvc.perform(post("/login").param("username", "hong.gildong").param("password", "NewPass99!"))
				.andExpect(status().is3xxRedirection())
				.andExpect(redirectedUrl("/"));

		mvc.perform(post("/login").param("username", "hong.gildong").param("password", "wrong"))
				.andExpect(status().is3xxRedirection())
				.andExpect(redirectedUrl("/login?error=true"));
	}

	@Test
	@WithMockUser
	@DisplayName("로그아웃 — 브라우저(Accept text/html)의 POST /logout 은 /login?logout 으로")
	void logout_post() throws Exception {
		mvc.perform(post("/logout").accept(MediaType.TEXT_HTML))
				.andExpect(status().is3xxRedirection())
				.andExpect(redirectedUrl("/login?logout"));
	}
}
