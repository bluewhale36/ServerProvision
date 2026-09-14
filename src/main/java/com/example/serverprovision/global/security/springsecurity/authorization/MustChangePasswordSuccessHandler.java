package com.example.serverprovision.global.security.springsecurity.authorization;

import com.example.serverprovision.global.security.springsecurity.domain.UserDetails_Impl;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.SavedRequestAwareAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * 로그인 성공 뒤 이동(S18 D-14): 초기 비밀번호 계정은 변경 화면으로, 그 밖은 저장된 요청 또는 루트로. 인증 판정은 건드리지 않는다 —
 * 성공한 뒤의 목적지만 고른다. 주소창 우회는 {@link MustChangePasswordInterceptor} 가 막는다.
 */
@Component
public class MustChangePasswordSuccessHandler extends SavedRequestAwareAuthenticationSuccessHandler {

	public static final String CHANGE_PASSWORD_PATH = "/password/change";
	public static final String FIRST_LOGIN_URL = CHANGE_PASSWORD_PATH + "?first=1";

	public MustChangePasswordSuccessHandler() {
		setDefaultTargetUrl("/");
	}

	@Override
	public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response, Authentication authentication)
			throws ServletException, IOException {
		if (authentication.getPrincipal() instanceof UserDetails_Impl principal && principal.mustChangePassword()) {
			clearAuthenticationAttributes(request);
			getRedirectStrategy().sendRedirect(request, response, FIRST_LOGIN_URL);
			return;
		}
		super.onAuthenticationSuccess(request, response, authentication);
	}
}
