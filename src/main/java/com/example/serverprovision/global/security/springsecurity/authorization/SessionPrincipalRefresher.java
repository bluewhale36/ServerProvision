package com.example.serverprovision.global.security.springsecurity.authorization;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.stereotype.Component;

/**
 * 세션의 principal 을 DB 의 현재 상태로 갈아 끼운다(S18 D-14) — 비밀번호 변경 뒤 강제 변경 플래그가 세션에 남아 인터셉터가
 * 계속 되돌리는 것을 막는다. Security 6 부터 컨텍스트 저장이 명시적이라 저장소에도 함께 쓴다.
 */
@Component
@RequiredArgsConstructor
public class SessionPrincipalRefresher {

	private final UserDetailsService userDetailsService;
	private final SecurityContextRepository securityContextRepository = new HttpSessionSecurityContextRepository();

	public void refresh(String username, HttpServletRequest request, HttpServletResponse response) {
		UserDetails fresh = userDetailsService.loadUserByUsername(username);
		Authentication authentication = UsernamePasswordAuthenticationToken.authenticated(fresh, null, fresh.getAuthorities());
		SecurityContext context = SecurityContextHolder.createEmptyContext();
		context.setAuthentication(authentication);
		SecurityContextHolder.setContext(context);
		securityContextRepository.saveContext(context, request, response);
	}
}
