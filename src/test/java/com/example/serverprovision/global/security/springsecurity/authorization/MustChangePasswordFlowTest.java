package com.example.serverprovision.global.security.springsecurity.authorization;

import com.example.serverprovision.global.security.springsecurity.domain.Role;
import com.example.serverprovision.global.security.springsecurity.domain.UserDetails_Impl;
import com.example.serverprovision.global.security.springsecurity.entity.Users;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** S18 D-14 — 첫 로그인의 목적지(성공 핸들러)와 변경 전 다른 화면 차단(인터셉터). 세션 principal 의 플래그가 재료다. */
class MustChangePasswordFlowTest {

	private final MustChangePasswordSuccessHandler handler = new MustChangePasswordSuccessHandler();
	private final MustChangePasswordInterceptor interceptor = new MustChangePasswordInterceptor();

	private static Authentication principalWith(boolean mustChange) {
		Users user = Users.builder().id(1L).username("hong.gildong").password("$2a$x").name("홍길동")
				.isEnabled(true).isLocked(false).mustChangePassword(mustChange).build();
		UserDetails_Impl details = new UserDetails_Impl(user, List.of(Role.USER));
		return UsernamePasswordAuthenticationToken.authenticated(details, null, details.getAuthorities());
	}

	@AfterEach
	void clear() {
		SecurityContextHolder.clearContext();
	}

	@Test
	@DisplayName("성공 핸들러 — 초기 비밀번호 계정은 /password/change?first=1 · 일반 계정은 기본 목적지 /")
	void successHandler_redirectsFlaggedPrincipal() throws Exception {
		MockHttpServletRequest req = new MockHttpServletRequest("POST", "/login");
		MockHttpServletResponse res = new MockHttpServletResponse();
		handler.onAuthenticationSuccess(req, res, principalWith(true));
		assertThat(res.getRedirectedUrl()).isEqualTo("/password/change?first=1");

		MockHttpServletResponse res2 = new MockHttpServletResponse();
		handler.onAuthenticationSuccess(new MockHttpServletRequest("POST", "/login"), res2, principalWith(false));
		assertThat(res2.getRedirectedUrl()).isEqualTo("/");
	}

	@Test
	@DisplayName("인터셉터 — 플래그 계정의 요청은 변경 화면으로 되돌리고(false) · 일반 계정 · 비인증은 통과(true)")
	void interceptor_blocksFlaggedPrincipal() throws Exception {
		SecurityContextHolder.getContext().setAuthentication(principalWith(true));
		MockHttpServletResponse res = new MockHttpServletResponse();
		assertThat(interceptor.preHandle(new MockHttpServletRequest("GET", "/provisioning/server"), res, new Object())).isFalse();
		assertThat(res.getRedirectedUrl()).isEqualTo("/password/change?first=1");

		SecurityContextHolder.getContext().setAuthentication(principalWith(false));
		assertThat(interceptor.preHandle(new MockHttpServletRequest("GET", "/provisioning/server"), new MockHttpServletResponse(), new Object())).isTrue();

		SecurityContextHolder.clearContext();
		assertThat(interceptor.preHandle(new MockHttpServletRequest("GET", "/login"), new MockHttpServletResponse(), new Object())).isTrue();
	}
}
