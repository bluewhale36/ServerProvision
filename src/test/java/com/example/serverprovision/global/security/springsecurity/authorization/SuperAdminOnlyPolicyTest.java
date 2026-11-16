package com.example.serverprovision.global.security.springsecurity.authorization;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.authorization.AuthorizationResult;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/** HF29 — /users 인가: 의미는 hasRole("SUPER_ADMIN") 그대로, 거절이면 사유를 실은 ReasonedDenial. */
class SuperAdminOnlyPolicyTest {

	private final SuperAdminOnlyPolicy policy = new SuperAdminOnlyPolicy();
	private final RequestAuthorizationContext context = new RequestAuthorizationContext(mock(HttpServletRequest.class));

	private static Authentication with(String... roles) {
		return UsernamePasswordAuthenticationToken.authenticated("u", null, AuthorityUtils.createAuthorityList(roles));
	}

	@Test
	@DisplayName("ROLE_SUPER_ADMIN 은 허용 — 사유 없음")
	void superAdminGranted() {
		AuthorizationResult result = policy.authorize(() -> with("ROLE_SUPER_ADMIN", "ROLE_ADMIN", "ROLE_USER"), context);
		assertThat(result.isGranted()).isTrue();
		assertThat(result).isNotInstanceOf(ReasonedDenial.class);
	}

	@Test
	@DisplayName("ROLE_ADMIN · ROLE_USER 는 거절 — 사유 '사용자 관리는 최고 관리자만 할 수 있습니다.'")
	void othersDeniedWithReason() {
		for (Authentication auth : new Authentication[]{with("ROLE_ADMIN", "ROLE_USER"), with("ROLE_USER")}) {
			AuthorizationResult result = policy.authorize(() -> auth, context);
			assertThat(result.isGranted()).isFalse();
			assertThat(result).isInstanceOfSatisfying(ReasonedDenial.class,
					d -> assertThat(d.reason()).isEqualTo("사용자 관리는 최고 관리자만 할 수 있습니다."));
		}
	}
}
