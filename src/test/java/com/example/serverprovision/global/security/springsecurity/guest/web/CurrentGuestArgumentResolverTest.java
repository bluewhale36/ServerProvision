package com.example.serverprovision.global.security.springsecurity.guest.web;

import com.example.serverprovision.global.security.springsecurity.exception.GuestUnauthenticatedException;
import com.example.serverprovision.global.security.springsecurity.guest.GuestPrincipal;
import com.example.serverprovision.global.security.springsecurity.guest.GuestTestSupport;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.test.context.support.WithMockUser;

import java.lang.reflect.Method;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** S19-1 D-6 — @CurrentGuest 해석: 게스트 principal 이면 주입 · 없거나 다른 principal(웹 사용자)이면 401 성격 예외. */
class CurrentGuestArgumentResolverTest {

	private final CurrentGuestArgumentResolver resolver = new CurrentGuestArgumentResolver();

	@SuppressWarnings("unused")
	void handler(@CurrentGuest GuestPrincipal guest, String other) {
	}

	private MethodParameter param(int index) throws NoSuchMethodException {
		Method m = getClass().getDeclaredMethod("handler", GuestPrincipal.class, String.class);
		return new MethodParameter(m, index);
	}

	@AfterEach
	void clear() {
		SecurityContextHolder.clearContext();
	}

	@Test
	@DisplayName("supports — @CurrentGuest GuestPrincipal 만")
	void supports() throws Exception {
		assertThat(resolver.supportsParameter(param(0))).isTrue();
		assertThat(resolver.supportsParameter(param(1))).isFalse();
	}

	@Test
	@DisplayName("resolve — 게스트 principal 주입 · 컨텍스트 비면 GuestUnauthenticatedException")
	void resolve() throws Exception {
		UUID id = UUID.randomUUID();
		GuestPrincipal expected = GuestTestSupport.authenticateAs(id, UUID.randomUUID());
		assertThat(resolver.resolveArgument(param(0), null, null, null)).isEqualTo(expected);

		SecurityContextHolder.clearContext();
		assertThatThrownBy(() -> resolver.resolveArgument(param(0), null, null, null)).isInstanceOf(GuestUnauthenticatedException.class);
	}

	@Test
	@WithMockUser
	@DisplayName("resolve — 웹 사용자 principal 은 게스트가 아니다 → 401 성격 예외")
	void resolve_webUserIsNotGuest() throws Exception {
		assertThatThrownBy(() -> resolver.resolveArgument(param(0), null, null, null)).isInstanceOf(GuestUnauthenticatedException.class);
	}
}
