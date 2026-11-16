package com.example.serverprovision.global.security.springsecurity.authorization;

import com.example.serverprovision.global.security.springsecurity.service.UsersService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.authorization.AuthorizationResult;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;
import org.springframework.stereotype.Component;

import java.util.function.Supplier;

/**
 * {@code /signup} 인가(S18 D-12 · MA8 개정): 관리자가 아직 없을 때만 누구나(최초 계정 = 최고 관리자 등록), 그 뒤에는 아무도 못 연다.
 * 계정 생성은 MA8 부터 최고 관리자의 사용자 관리({@code /users/new})로 통합됐다(사용자 결정 U-3). 거절은 비인증이면 로그인 화면,
 * 인증이면 403 이다. 부트스트랩 판정은 {@link UsersService#bootstrapOpen()} 하나 — 로그인 화면의 등록 링크와 같은 사실을 본다.
 */
@Component
@RequiredArgsConstructor
public class SignupAccessPolicy implements AuthorizationManager<RequestAuthorizationContext> {

	private final UsersService usersService;

	@Override
	public AuthorizationResult authorize(Supplier<? extends Authentication> authentication, RequestAuthorizationContext context) {
		return new AuthorizationDecision(usersService.bootstrapOpen());
	}
}
