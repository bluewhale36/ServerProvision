package com.example.serverprovision.global.security.springsecurity.guest;

import org.springframework.security.core.context.SecurityContextHolder;

import java.util.UUID;

/** 테스트에서 게스트 체인이 세운 principal 을 흉내 낸다 — 슬라이스 테스트는 필터가 없으므로 컨텍스트를 직접 채운다. */
public final class GuestTestSupport {

	private GuestTestSupport() {
	}

	public static GuestPrincipal authenticateAs(UUID guestServerId, UUID systemUUID) {
		GuestPrincipal principal = new GuestPrincipal(guestServerId, systemUUID);
		SecurityContextHolder.getContext().setAuthentication(GuestAuthenticationToken.authenticated(principal));
		return principal;
	}

	public static void clear() {
		SecurityContextHolder.clearContext();
	}
}
