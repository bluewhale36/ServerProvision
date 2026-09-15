package com.example.serverprovision.global.security.springsecurity.guest.authentication;

import com.example.serverprovision.execution.config.PxeBootProperties;
import com.example.serverprovision.global.security.springsecurity.domain.MachineAuthority;
import com.example.serverprovision.global.security.springsecurity.guest.GuestAuthenticationToken;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** S19-2 D-2 — PXE 부팅 계정 provider: 정확한 credential 만 ROLE_PXE_BOOT · 사용자명 · secret · 미설정 전부 BadCredentials. */
class PxeBootAuthenticationProviderTest {

	private static final PxeBootAuthenticationProvider PROVIDER =
			new PxeBootAuthenticationProvider(new PxeBootProperties("pxe", "s3cret", ""));

	@Test
	@DisplayName("정확한 credential → 인증됨 · 권한 ROLE_PXE_BOOT 하나 · 비밀번호는 들지 않는다")
	void exact_authenticatesAsPxeBoot() {
		Authentication result = PROVIDER.authenticate(UsernamePasswordAuthenticationToken.unauthenticated("pxe", "s3cret"));
		assertThat(result.isAuthenticated()).isTrue();
		assertThat(result.getName()).isEqualTo("pxe");
		assertThat(result.getCredentials()).isNull();
		assertThat(result.getAuthorities()).extracting(GrantedAuthority::getAuthority).containsExactly(MachineAuthority.PXE_BOOT.getAuthority());
	}

	@Test
	@DisplayName("사용자명 다름 · secret 다름 · 빈 secret · 접두 일치 → 전부 BadCredentials")
	void wrong_rejected() {
		for (String[] c : new String[][]{{"admin", "s3cret"}, {"pxe", "S3cret"}, {"pxe", ""}, {"pxe", "s3cre"}, {"pxe", "s3cret "}}) {
			assertThatThrownBy(() -> PROVIDER.authenticate(UsernamePasswordAuthenticationToken.unauthenticated(c[0], c[1])))
					.as(c[0] + "/" + c[1]).isInstanceOf(BadCredentialsException.class);
		}
	}

	@Test
	@DisplayName("secret 미설정 인스턴스 — 어떤 credential 도 받지 않는다(채널 닫힘)")
	void unconfigured_rejectsEverything() {
		PxeBootAuthenticationProvider closed = new PxeBootAuthenticationProvider(new PxeBootProperties("pxe", "", ""));
		assertThatThrownBy(() -> closed.authenticate(UsernamePasswordAuthenticationToken.unauthenticated("pxe", "")))
				.isInstanceOf(BadCredentialsException.class);
	}

	@Test
	@DisplayName("supports — Basic 의 UsernamePasswordAuthenticationToken 만 · 게스트 토큰은 다른 provider 몫")
	void supports_basicOnly() {
		assertThat(PROVIDER.supports(UsernamePasswordAuthenticationToken.class)).isTrue();
		assertThat(PROVIDER.supports(GuestAuthenticationToken.class)).isFalse();
	}
}
