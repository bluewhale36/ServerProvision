package com.example.serverprovision.global.security.springsecurity.authorization;

import org.springframework.security.authorization.AuthorityAuthorizationManager;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.authorization.AuthorizationResult;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;
import org.springframework.stereotype.Component;

import java.util.function.Supplier;

/**
 * {@code /users/**} 인가(MA8 · HF29) — 의미는 종전 {@code hasRole("SUPER_ADMIN")} 그대로이고, 거절에 사유를 싣는 것만 더했다.
 * 판정은 Spring 의 {@link AuthorityAuthorizationManager} 에 맡긴다(권한 계층 해석 · 익명 처리 동일).
 */
@Component
public class SuperAdminOnlyPolicy implements AuthorizationManager<RequestAuthorizationContext> {

	static final String REASON = "사용자 관리는 최고 관리자만 할 수 있습니다.";

	private final AuthorizationManager<RequestAuthorizationContext> superAdmin = AuthorityAuthorizationManager.hasRole("SUPER_ADMIN");

	@Override
	public AuthorizationResult authorize(Supplier<? extends Authentication> authentication, RequestAuthorizationContext context) {
		AuthorizationResult result = superAdmin.authorize(authentication, context);
		return result != null && result.isGranted() ? new AuthorizationDecision(true) : new ReasonedDenial(REASON);
	}
}
