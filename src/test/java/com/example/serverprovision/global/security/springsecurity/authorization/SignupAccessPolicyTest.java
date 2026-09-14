package com.example.serverprovision.global.security.springsecurity.authorization;

import com.example.serverprovision.global.security.springsecurity.service.UsersService;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

/** S18 D-12 — /signup 인가: 관리자 부재면 누구나, 있으면 ROLE_ADMIN 만. */
@ExtendWith(MockitoExtension.class)
class SignupAccessPolicyTest {

	@Mock UsersService usersService;
	@InjectMocks SignupAccessPolicy policy;

	private final RequestAuthorizationContext context = new RequestAuthorizationContext(mock(HttpServletRequest.class));

	private static Authentication anonymous() {
		return new AnonymousAuthenticationToken("key", "anonymousUser", AuthorityUtils.createAuthorityList("ROLE_ANONYMOUS"));
	}

	private static Authentication with(String... roles) {
		return UsernamePasswordAuthenticationToken.authenticated("u", null, AuthorityUtils.createAuthorityList(roles));
	}

	@Test
	@DisplayName("부트스트랩 열림 — 익명도 허용")
	void open_anonymousGranted() {
		given(usersService.bootstrapOpen()).willReturn(true);
		assertThat(policy.authorize(SignupAccessPolicyTest::anonymous, context).isGranted()).isTrue();
	}

	@Test
	@DisplayName("부트스트랩 닫힘 — 익명 거부 · ROLE_USER 거부 · ROLE_ADMIN 허용")
	void closed_adminOnly() {
		given(usersService.bootstrapOpen()).willReturn(false);
		assertThat(policy.authorize(SignupAccessPolicyTest::anonymous, context).isGranted()).isFalse();
		assertThat(policy.authorize(() -> with("ROLE_USER"), context).isGranted()).isFalse();
		assertThat(policy.authorize(() -> with("ROLE_ADMIN", "ROLE_USER"), context).isGranted()).isTrue();
	}
}
