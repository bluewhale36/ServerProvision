package com.example.serverprovision.global.security.springsecurity.guest.authentication;

import com.example.serverprovision.execution.config.PxeBootProperties;
import com.example.serverprovision.global.security.springsecurity.domain.MachineAuthority;

import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.authority.AuthorityUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * PXE 부팅 계정의 Basic Auth 를 {@link MachineAuthority#PXE_BOOT} 로 바꾼다(S19-2 D-2).
 * <p>{@link GuestTokenAuthenticationProvider} 와 같은 이유로 bean 이 아니다 — Guest Filter Config 가 직접 만들어 그 체인의
 * {@code ProviderManager} 에만 넣는다(웹 체인의 httpBasic 은 관리자 계정을 본다 · 섞이지 않는다).</p>
 */
@RequiredArgsConstructor
public class PxeBootAuthenticationProvider implements AuthenticationProvider {

	private final PxeBootProperties properties;

	@Override
	public Authentication authenticate(Authentication authentication) throws AuthenticationException {
		String username = authentication.getName();
		Object credentials = authentication.getCredentials();
		String presented = credentials == null ? null : credentials.toString();
		if (
				!properties.configured() || presented == null || !properties.username().equals(username) ||
				!MessageDigest.isEqual(properties.secret().getBytes(StandardCharsets.UTF_8), presented.getBytes(StandardCharsets.UTF_8))
		) {
			throw new BadCredentialsException("PXE 부팅 credential 이 유효하지 않습니다.");
		}
		return UsernamePasswordAuthenticationToken.authenticated(
				username, null,
				AuthorityUtils.createAuthorityList(MachineAuthority.PXE_BOOT.getAuthority())
		);
	}

	@Override
	public boolean supports(Class<?> authentication) {
		return UsernamePasswordAuthenticationToken.class.isAssignableFrom(authentication);
	}
}
