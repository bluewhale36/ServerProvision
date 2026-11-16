package com.example.serverprovision.global.security.springsecurity.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.security.core.session.SessionRegistryImpl;
import org.springframework.security.web.session.HttpSessionEventPublisher;

/**
 * 관리 화면 세션 등록소(MA8 D-8). 등록소는 다른 사용자의 세션을 만료시키는 데만 쓴다 — 동시 세션 수는 제한하지 않는다
 * ({@code maximumSessions(-1)}). {@link HttpSessionEventPublisher} 가 있어야 세션이 끝날 때 등록소에서도 빠진다(없으면 쌓인다).
 */
@Configuration
public class SessionRegistryConfig {

	@Bean
	public SessionRegistry sessionRegistry() {
		return new SessionRegistryImpl();
	}

	@Bean
	public HttpSessionEventPublisher httpSessionEventPublisher() {
		return new HttpSessionEventPublisher();
	}
}
