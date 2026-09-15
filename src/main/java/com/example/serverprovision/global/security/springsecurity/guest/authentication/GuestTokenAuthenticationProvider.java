package com.example.serverprovision.global.security.springsecurity.guest.authentication;

import com.example.serverprovision.global.security.springsecurity.guest.GuestAuthenticationToken;
import com.example.serverprovision.global.security.springsecurity.guest.GuestCredential;

import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;

/**
 * 게스트 credential 을 principal 로 바꾼다(S19-1 D-3) — 풀리지 않는 credential 은 {@link BadCredentialsException}(→ 401 JSON).
 * <p>bean 이 아니다 — {@code AuthenticationProvider} bean 이 하나라도 있으면 Spring Security 가 전역 AuthenticationManager 를 그것만으로
 * 구성해 웹 체인의 formLogin(UserDetailsService + 인코더)이 죽는다(CP5 F-1 실측). Guest Filter Config 가 직접 만든다.</p>
 */
@RequiredArgsConstructor
public class GuestTokenAuthenticationProvider implements AuthenticationProvider {

	private final GuestPrincipalResolver resolver;

	@Override
	public Authentication authenticate(Authentication authentication) throws AuthenticationException {
		GuestCredential credential = ((GuestAuthenticationToken) authentication).credential();
		return resolver.resolve(credential)
				.map(GuestAuthenticationToken::authenticated)
				.orElseThrow(() -> new BadCredentialsException("게스트 credential 이 유효하지 않습니다."));
	}

	@Override
	public boolean supports(Class<?> authentication) {
		return GuestAuthenticationToken.class.isAssignableFrom(authentication);
	}
}
