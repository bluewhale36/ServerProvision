package com.example.serverprovision.global.security.springsecurity.authorization;

import com.example.serverprovision.global.security.springsecurity.domain.UserDetails_Impl;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 초기 비밀번호 계정의 다른 화면 이동을 변경 화면으로 되돌린다(S18 D-14). 허용 경로(변경 화면 · 로그인 · 로그아웃 · 오류 · 정적 자산 ·
 * 게스트 채널 · 배경 작업 폴링)는 {@link MustChangePasswordWebConfig} 의 제외 패턴이 정한다. 세션의 principal 을 읽으므로 변경
 * 성공 시 principal 을 갱신해야 한다({@link SessionPrincipalRefresher}).
 */
@Component
public class MustChangePasswordInterceptor implements HandlerInterceptor {

	@Override
	public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
		if (authentication != null && authentication.getPrincipal() instanceof UserDetails_Impl principal && principal.mustChangePassword()) {
			response.sendRedirect(request.getContextPath() + MustChangePasswordSuccessHandler.FIRST_LOGIN_URL);
			return false;
		}
		return true;
	}
}
