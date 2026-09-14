package com.example.serverprovision.global.security.springsecurity.authorization;

import com.example.serverprovision.global.security.springsecurity.service.UsersService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authorization.AuthorityAuthorizationManager;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.authorization.AuthorizationResult;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;
import org.springframework.stereotype.Component;

import java.util.function.Supplier;

/**
 * {@code /signup} 인가(S18 D-12 · 사용자 결정 (b)+(c)): 관리자가 아직 없으면 누구나(최초 관리자 등록), 있으면 ROLE_ADMIN 만.
 * 부트스트랩 판정은 {@link UsersService#bootstrapOpen()} 하나 — 화면 모드 선택 · 로그인 화면의 등록 링크와 같은 사실을 본다.
 */
@Component
@RequiredArgsConstructor
public class SignupAccessPolicy implements AuthorizationManager<RequestAuthorizationContext> {

	private final UsersService usersService;
	private final AuthorizationManager<RequestAuthorizationContext> adminOnly = AuthorityAuthorizationManager.hasRole("ADMIN");

	@Override
	public AuthorizationResult authorize(Supplier<? extends Authentication> authentication, RequestAuthorizationContext context) {
		if (usersService.bootstrapOpen()) {
			return new AuthorizationDecision(true);
		}
		return adminOnly.authorize(authentication, context);
	}
}
