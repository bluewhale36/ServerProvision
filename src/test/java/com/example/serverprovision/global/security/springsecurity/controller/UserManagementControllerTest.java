package com.example.serverprovision.global.security.springsecurity.controller;

import com.example.serverprovision.global.security.springsecurity.authorization.ReasonedAccessDeniedHandler;
import com.example.serverprovision.global.security.springsecurity.authorization.SuperAdminOnlyPolicy;
import com.example.serverprovision.global.security.springsecurity.authorization.MustChangePasswordSuccessHandler;
import com.example.serverprovision.global.security.springsecurity.authorization.SignupAccessPolicy;
import com.example.serverprovision.global.security.springsecurity.config.PasswordEncoderConfig;
import com.example.serverprovision.global.security.springsecurity.config.SecurityConfig;
import com.example.serverprovision.global.security.springsecurity.domain.Role;
import com.example.serverprovision.global.security.springsecurity.domain.SignupMode;
import com.example.serverprovision.global.security.springsecurity.domain.UserAction;
import com.example.serverprovision.global.security.springsecurity.domain.UserDetails_Impl;
import com.example.serverprovision.global.security.springsecurity.dto.response.UserDetailResponse;
import com.example.serverprovision.global.security.springsecurity.dto.response.UserSummaryResponse;
import com.example.serverprovision.global.security.springsecurity.entity.Users;
import com.example.serverprovision.global.security.springsecurity.exception.DuplicateUsernameException;
import com.example.serverprovision.global.security.springsecurity.exception.PasswordMismatchException;
import com.example.serverprovision.global.security.springsecurity.exception.UserManagementBlockedException;
import com.example.serverprovision.global.security.springsecurity.exception.UserNotFoundException;
import com.example.serverprovision.global.security.springsecurity.service.UserManagementService;
import com.example.serverprovision.global.security.springsecurity.service.UsersQueryService;
import com.example.serverprovision.global.security.springsecurity.service.UsersService;
import com.example.serverprovision.support.FixedClockTestConfig;
import com.example.serverprovision.global.exception.ErrorReason;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.mapping.JpaMetamodelMappingContext;
import org.springframework.http.MediaType;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.time.LocalDateTime;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

/**
 * MA8 — 사용자 관리의 HTTP 계층. 실제 SecurityFilterChain(SecurityConfig)을 얹어 등급별 접근(최고 관리자 200 · 관리자 · 사용자 403 ·
 * 익명 로그인 이동)과 각 동작의 성공 · 400 · 404 · 409 를 본다(Boot 4 의 @WebMvcTest 는 보안 필터를 자동 적용하지 않으므로
 * springSecurity() 를 직접 단다 — S18 LoginPageSecurityTest 선례). Mocking 은 서비스까지 — advice 의 예외 → 상태 매핑은 실제로 돈다.
 */
@WebMvcTest(controllers = UserManagementController.class)
@Import({SecurityConfig.class, PasswordEncoderConfig.class, SignupAccessPolicy.class,
		SuperAdminOnlyPolicy.class, ReasonedAccessDeniedHandler.class,   // HF29 — 웹 체인의 거절 사유
		 MustChangePasswordSuccessHandler.class,
		SignupSubmission.class, FixedClockTestConfig.class})
class UserManagementControllerTest {

	@Autowired WebApplicationContext context;
	@MockitoBean UsersService usersService;
	@MockitoBean UsersQueryService queryService;
	@MockitoBean UserManagementService managementService;
	@MockitoBean UserDetailsService userDetailsService;
	@MockitoBean JpaMetamodelMappingContext jpaMetamodelMappingContext;

	private MockMvc mvc;

	@BeforeEach
	void setUp() {
		mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
	}

	private static RequestPostProcessor as(String username, Role level) {
		Users u = Users.builder().id(1L).username(username).password("x").name(username).isEnabled(true).isLocked(false).build();
		return user(new UserDetails_Impl(u, List.copyOf(Role.expand(List.of(level)))));
	}

	private static final RequestPostProcessor SUPER = as("kim", Role.SUPER_ADMIN);

	private static UserSummaryResponse summary(long id, String username, Role level, boolean enabled) {
		return new UserSummaryResponse(id, username, "홍길동", level, enabled, false, LocalDateTime.of(2026, 11, 16, 21, 0));
	}

	private static UserDetailResponse detail(UserSummaryResponse s, Map<UserAction, String> reasons) {
		Map<UserAction, String> all = new EnumMap<>(UserAction.class);
		for (UserAction a : UserAction.values()) all.put(a, null);
		all.putAll(reasons);
		Map<Role, String> levels = new EnumMap<>(Role.class);
		for (Role r : Role.values()) levels.put(r, UserAction.levelChangeBlockReason(s.level(), r));
		return new UserDetailResponse(s, all, levels);
	}

	// ==== 인가 ====================================================

	@Test
	@DisplayName("인가 — /users: 최고 관리자 200 · 관리자 403 · 사용자 403 · 익명 302 /login")
	void authorization_list() throws Exception {
		Page<UserSummaryResponse> page = new PageImpl<>(List.of(summary(2, "park", Role.USER, true)), PageRequest.of(0, 20), 1);
		given(queryService.search(any(), any())).willReturn(page);

		mvc.perform(get("/users").with(SUPER).accept(MediaType.TEXT_HTML))
				.andExpect(status().isOk())
				.andExpect(view().name("users/user-list"))
				.andExpect(content().string(containsString("park")))
				.andExpect(content().string(containsString("사용자 관리")));
		// HF29 — 403 은 의미 그대로(ADMIN · USER 거절) + 거절 사유가 요청 속성에 실린다(/error 가 message 로 그린다 — ErrorPageReasonTest)
		mvc.perform(get("/users").with(as("park", Role.ADMIN)).accept(MediaType.TEXT_HTML))
				.andExpect(status().isForbidden())
				.andExpect(request().attribute(ErrorReason.REQUEST_ATTRIBUTE, "사용자 관리는 최고 관리자만 할 수 있습니다."));
		mvc.perform(get("/users").with(as("choi", Role.USER)).accept(MediaType.TEXT_HTML))
				.andExpect(status().isForbidden())
				.andExpect(request().attribute(ErrorReason.REQUEST_ATTRIBUTE, "사용자 관리는 최고 관리자만 할 수 있습니다."));
		// 익명은 핸들러가 아니라 인증 진입점(로그인 이동) — 사유를 싣지 않는다
		mvc.perform(get("/users").accept(MediaType.TEXT_HTML))
				.andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/login"))
				.andExpect(request().attribute(ErrorReason.REQUEST_ATTRIBUTE, nullValue()));
	}

	@Test
	@DisplayName("HF29 — XHR · JSON 요청의 403 도 같은 사유를 싣는다(협상은 /error 가 맡는다) · 익명 XHR 은 진입점")
	void authorization_xhrJson() throws Exception {
		mvc.perform(post("/users/2/disable").with(as("park", Role.ADMIN))
						.header("X-Requested-With", "XMLHttpRequest").accept(MediaType.APPLICATION_JSON))
				.andExpect(status().isForbidden())
				.andExpect(request().attribute(ErrorReason.REQUEST_ATTRIBUTE, "사용자 관리는 최고 관리자만 할 수 있습니다."));
		mvc.perform(get("/users").header("X-Requested-With", "XMLHttpRequest").accept(MediaType.APPLICATION_JSON))
				.andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/login"))
				.andExpect(request().attribute(ErrorReason.REQUEST_ATTRIBUTE, nullValue()));
		verify(managementService, never()).disable(any(), any());
	}

	@Test
	@DisplayName("인가 — 관리자는 사용자 추가 화면 · 추가 POST · 동작 POST 모두 403 · 서비스 미호출")
	void authorization_adminCannotWrite() throws Exception {
		RequestPostProcessor admin = as("park", Role.ADMIN);
		mvc.perform(get("/users/new").with(admin).accept(MediaType.TEXT_HTML)).andExpect(status().isForbidden());
		mvc.perform(post("/users").with(admin).param("username", "x").param("name", "x")
				.param("password", "Init1234!").param("retypedPassword", "Init1234!").param("role", "SUPER_ADMIN"))
				.andExpect(status().isForbidden());
		mvc.perform(post("/users/2/role").with(admin).param("level", "SUPER_ADMIN")).andExpect(status().isForbidden());
		mvc.perform(post("/users/2/disable").with(admin)).andExpect(status().isForbidden());
		verify(usersService, never()).signup(any(), any());
		verify(managementService, never()).changeRole(any(), any(), any());
		verify(managementService, never()).disable(any(), any());
	}

	// ==== 사용자 추가 ====================================================

	@Test
	@DisplayName("사용자 추가 — 화면은 등급 라디오 · 제출 대상 /users / 성공 302 상세 · flash / 빈 등급 400 / 중복 409 재렌더")
	void create() throws Exception {
		mvc.perform(get("/users/new").with(SUPER).accept(MediaType.TEXT_HTML))
				.andExpect(status().isOk())
				.andExpect(view().name("users/user-new"))
				.andExpect(content().string(containsString("type=\"radio\"")))
				.andExpect(content().string(containsString("role-SUPER_ADMIN")))
				.andExpect(content().string(containsString("action=\"/users\"")));

		Users created = Users.builder().id(7L).username("hong").name("홍길동").build();
		given(usersService.signup(any(), eq(SignupMode.MANAGED))).willReturn(created);
		mvc.perform(post("/users").with(SUPER).param("username", "hong").param("name", "홍길동")
						.param("password", "Init1234!").param("retypedPassword", "Init1234!").param("role", "ADMIN"))
				.andExpect(status().is3xxRedirection())
				.andExpect(redirectedUrl("/users/7"))
				.andExpect(flash().attribute("flashMessage", containsString("hong")));

		mvc.perform(post("/users").with(SUPER).param("username", "lee").param("name", "이")
						.param("password", "Init1234!").param("retypedPassword", "Init1234!"))
				.andExpect(status().isBadRequest())
				.andExpect(model().attributeHasFieldErrors("signUpForm", "role"))
				.andExpect(content().string(containsString("권한 등급을 골라야 합니다.")));

		given(usersService.signup(any(), eq(SignupMode.MANAGED))).willThrow(new DuplicateUsernameException("dup"));
		mvc.perform(post("/users").with(SUPER).param("username", "dup").param("name", "중복")
						.param("password", "Init1234!").param("retypedPassword", "Init1234!").param("role", "USER"))
				.andExpect(status().isConflict())
				.andExpect(view().name("users/user-new"))
				.andExpect(model().attributeHasFieldErrors("signUpForm", "username"));
	}

	@Test
	@DisplayName("사용자 추가 400 — 위조 등급(role=NOPE)은 typeMismatch 하나만(등급 미선택 문구와 겹치지 않는다)")
	void create_forgedRole() throws Exception {
		mvc.perform(post("/users").with(SUPER).param("username", "hong").param("name", "홍길동")
						.param("password", "Init1234!").param("retypedPassword", "Init1234!").param("role", "NOPE"))
				.andExpect(status().isBadRequest())
				.andExpect(model().attributeHasFieldErrors("signUpForm", "role"))
				.andExpect(model().attributeErrorCount("signUpForm", 1))
				.andExpect(content().string(not(containsString("권한 등급을 골라야 합니다."))));
		verify(usersService, never()).signup(any(), any());
	}

	// ==== 상세 ====================================================

	@Test
	@DisplayName("상세 — 막힌 동작은 disabled + tooltip 사유(UserAction.blockReason) · 허용 동작은 제출 폼 + 확인 모달 마커")
	void detail_blockedAndAllowed() throws Exception {
		given(queryService.detail(2L, "kim")).willReturn(detail(summary(2, "park", Role.ADMIN, false), Map.of(
				UserAction.DISABLE, "이미 비활성화된 계정입니다.",
				UserAction.RESET_PASSWORD, "비활성화된 계정입니다. 먼저 활성화해야 합니다.")));

		mvc.perform(get("/users/2").with(SUPER).accept(MediaType.TEXT_HTML))
				.andExpect(status().isOk())
				.andExpect(content().string(containsString("data-nav-back")))   // CP7 — 공용 '← 목록으로' 조각
				.andExpect(content().string(not(containsString("초기 비밀번호를 지정하지 못했습니다."))))
				.andExpect(view().name("users/user-detail"))
				.andExpect(content().string(containsString("data-tooltip=\"비활성화된 계정입니다. 먼저 활성화해야 합니다.\"")))
				.andExpect(content().string(containsString("action=\"/users/2/enable\"")))   // 비활성 → 활성화 폼
				.andExpect(content().string(containsString("data-confirm-user-role")))
				.andExpect(content().string(containsString("관리자 (현재 등급)")))   // CP5 D-1 — 현재 등급 선택지는 tooltip 과 함께
				.andExpect(content().string(containsString("data-tooltip=\"이미 그 권한 등급입니다.\"")))
				.andExpect(content().string(containsString("data-level-submit disabled")))   // 고르기 전에는 제출 버튼이 잠겨 있다
				.andExpect(content().string(not(containsString("<fieldset"))))
				.andExpect(content().string(containsString("data-native-submit")));
	}

	@Test
	@DisplayName("상세 404 — 없는 id 는 UserNotFoundException → 404")
	void detail_notFound() throws Exception {
		given(queryService.detail(99L, "kim")).willThrow(new UserNotFoundException(99L));
		mvc.perform(get("/users/99").with(SUPER).accept(MediaType.TEXT_HTML))
				.andExpect(status().isNotFound())
				.andExpect(content().string(containsString("사용자를 찾을 수 없습니다. 목록에서 다시 골라야 합니다.")))   // HF29 — id 미노출
				.andExpect(content().string(not(containsString("id=99"))));
	}

	// ==== 동작 ====================================================

	@Test
	@DisplayName("권한 등급 변경 — 302 상세 + flash / 등급 없음 · 위조 400 / 자기 자신 409 / 없는 대상 404")
	void changeRole() throws Exception {
		mvc.perform(post("/users/2/role").with(SUPER).param("level", "ADMIN"))
				.andExpect(status().is3xxRedirection())
				.andExpect(redirectedUrl("/users/2"))
				.andExpect(flash().attribute("flashMessage", containsString("'관리자' 등급으로")));
		verify(managementService).changeRole("kim", 2L, Role.ADMIN);

		mvc.perform(post("/users/2/role").with(SUPER)).andExpect(status().isBadRequest());
		mvc.perform(post("/users/2/role").with(SUPER).param("level", "NOPE")).andExpect(status().isBadRequest());

		willThrow(new UserManagementBlockedException("자기 자신의 권한 등급은 바꿀 수 없습니다."))
				.given(managementService).changeRole("kim", 1L, Role.USER);
		mvc.perform(post("/users/1/role").with(SUPER).param("level", "USER")
						.header("X-Requested-With", "XMLHttpRequest").accept(MediaType.APPLICATION_JSON))
				.andExpect(status().isConflict())
				.andExpect(content().string(containsString("자기 자신의 권한 등급은 바꿀 수 없습니다.")));

		willThrow(new UserNotFoundException(99L)).given(managementService).changeRole("kim", 99L, Role.USER);
		mvc.perform(post("/users/99/role").with(SUPER).param("level", "USER")
						.header("X-Requested-With", "XMLHttpRequest").accept(MediaType.APPLICATION_JSON))
				.andExpect(status().isNotFound());
	}

	@Test
	@DisplayName("비활성화 · 활성화 — 302 상세 + flash / stale 화면의 재비활성화 409 / 없는 대상 404")
	void disableEnable() throws Exception {
		mvc.perform(post("/users/3/disable").with(SUPER))
				.andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/users/3"))
				.andExpect(flash().attribute("flashMessage", containsString("비활성화했습니다")));
		mvc.perform(post("/users/3/enable").with(SUPER))
				.andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/users/3"))
				.andExpect(flash().attribute("flashMessage", containsString("활성화했습니다")));

		willThrow(new UserManagementBlockedException("이미 비활성화된 계정입니다.")).given(managementService).disable("kim", 4L);
		mvc.perform(post("/users/4/disable").with(SUPER).header("X-Requested-With", "XMLHttpRequest").accept(MediaType.APPLICATION_JSON))
				.andExpect(status().isConflict())
				.andExpect(content().string(containsString("이미 비활성화된 계정입니다.")));

		willThrow(new UserNotFoundException(99L)).given(managementService).enable("kim", 99L);
		mvc.perform(post("/users/99/enable").with(SUPER).header("X-Requested-With", "XMLHttpRequest").accept(MediaType.APPLICATION_JSON))
				.andExpect(status().isNotFound());
	}

	@Test
	@DisplayName("초기 비밀번호 지정 — 302 + flash / 규칙 위반 · 불일치 400 상세 재렌더(필드 오류) · 서비스 안전망 불일치도 400")
	void assignPassword() throws Exception {
		mvc.perform(post("/users/3/password").with(SUPER).param("password", "Init5678!").param("retypedPassword", "Init5678!"))
				.andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/users/3"))
				.andExpect(flash().attribute("flashMessage", containsString("초기 비밀번호를 지정했습니다")));

		given(queryService.detail(3L, "kim")).willReturn(detail(summary(3, "park", Role.USER, true), Map.of()));
		mvc.perform(post("/users/3/password").with(SUPER).param("password", "short").param("retypedPassword", "other"))
				.andExpect(status().isBadRequest())
				.andExpect(view().name("users/user-detail"))
				.andExpect(model().attributeHasFieldErrors("passwordForm", "password", "passwordConfirmed"))
				.andExpect(content().string(containsString("초기 비밀번호를 지정하지 못했습니다.")))   // CP7 — 맨 위 실패 안내
				.andExpect(content().string(containsString("data-rule-regex=")))                     // 제출 전 확인이 쓸 서버 규칙
				.andExpect(content().string(containsString("data-mismatch-message=\"비밀번호가 일치하지 않습니다.\"")));

		// HF29 — 서버 거절(409)은 공통 오류 화면이 아니라 상세 재렌더 + 카드 안 사유 · 폼은 비운다
		willThrow(new UserManagementBlockedException("비활성화된 계정입니다. 먼저 활성화해야 합니다."))
				.given(managementService).assignInitialPassword(eq("kim"), eq(4L), any());
		given(queryService.detail(4L, "kim")).willReturn(detail(summary(4, "choi", Role.USER, false),
				Map.of(UserAction.RESET_PASSWORD, "비활성화된 계정입니다. 먼저 활성화해야 합니다.")));
		mvc.perform(post("/users/4/password").with(SUPER).param("password", "Init5678!").param("retypedPassword", "Init5678!"))
				.andExpect(status().isConflict())
				.andExpect(view().name("users/user-detail"))
				.andExpect(content().string(containsString("초기 비밀번호를 지정하지 못했습니다. 비활성화된 계정입니다. 먼저 활성화해야 합니다.")))
				.andExpect(model().attribute("passwordForm", org.hamcrest.Matchers.hasProperty("password", nullValue())));

		willThrow(PasswordMismatchException.retyped()).given(managementService).assignInitialPassword(eq("kim"), eq(3L), any());
		mvc.perform(post("/users/3/password").with(SUPER).param("password", "Init5678!").param("retypedPassword", "Init5678!"))
				.andExpect(status().isBadRequest())
				.andExpect(model().attributeHasFieldErrors("passwordForm", "retypedPassword"));
	}

	@Test
	@DisplayName("사이드바 — 최고 관리자만 '사용자 관리' 링크를 본다(관리자 화면에는 링크 없음은 403 이라 목록에서 확인)")
	void sidebarLink() throws Exception {
		given(queryService.search(any(), any())).willReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));
		mvc.perform(get("/users").with(SUPER).accept(MediaType.TEXT_HTML))
				.andExpect(content().string(containsString("href=\"/users\"")))
				.andExpect(content().string(not(containsString("href=\"/signup\""))));
	}
}
