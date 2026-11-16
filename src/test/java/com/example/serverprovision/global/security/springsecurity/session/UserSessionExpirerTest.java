package com.example.serverprovision.global.security.springsecurity.session;

import com.example.serverprovision.global.security.springsecurity.domain.Role;
import com.example.serverprovision.global.security.springsecurity.domain.UserDetails_Impl;
import com.example.serverprovision.global.security.springsecurity.entity.Users;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.session.SessionInformation;
import org.springframework.security.core.session.SessionRegistryImpl;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** MA8 D-8 — 사용자명으로 그 사람의 세션만 만료 표시한다(로그인마다 새로 만든 principal 도 같은 사람으로 묶인다). */
class UserSessionExpirerTest {

	private static UserDetails_Impl principal(String username) {
		Users u = Users.builder().id(1L).username(username).password("x").name(username).isEnabled(true).isLocked(false).build();
		return new UserDetails_Impl(u, List.of(Role.USER));
	}

	@Test
	@DisplayName("대상의 세션 둘(서로 다른 로그인)만 만료 · 다른 사용자 세션은 그대로")
	void expiresOnlyTarget() {
		SessionRegistryImpl registry = new SessionRegistryImpl();
		registry.registerNewSession("s1", principal("park"));
		registry.registerNewSession("s2", principal("park"));   // 다른 브라우저 — 새 principal 객체
		registry.registerNewSession("s3", principal("kim"));

		new UserSessionExpirer(registry).onExpireRequested(new UserSessionsExpireRequested("park"));

		assertThat(registry.getSessionInformation("s1").isExpired()).isTrue();
		assertThat(registry.getSessionInformation("s2").isExpired()).isTrue();
		assertThat(registry.getSessionInformation("s3").isExpired()).isFalse();
	}

	@Test
	@DisplayName("principal 동등성 — 같은 사용자명이면 같은 키로 묶인다(UserDetails_Impl.equals)")
	void principalEquality() {
		SessionRegistryImpl registry = new SessionRegistryImpl();
		registry.registerNewSession("s1", principal("park"));
		registry.registerNewSession("s2", principal("park"));
		List<SessionInformation> sessions = registry.getAllSessions(principal("park"), false);
		assertThat(sessions).hasSize(2);
	}
}
