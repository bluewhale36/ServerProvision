package com.example.serverprovision.global.security.springsecurity.authorization;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** 강제 변경 인터셉터 등록 — 변경 화면 · 인증 흐름 · 오류 · 정적 자산 · 게스트 채널 · 배경 작업 폴링은 제외한다. */
@Configuration
@RequiredArgsConstructor
public class MustChangePasswordWebConfig implements WebMvcConfigurer {

	private final MustChangePasswordInterceptor interceptor;

	@Override
	public void addInterceptors(InterceptorRegistry registry) {
		registry.addInterceptor(interceptor)
				.addPathPatterns("/**")
				.excludePathPatterns(
						MustChangePasswordSuccessHandler.CHANGE_PASSWORD_PATH,
						"/login", "/logout", "/error",
						"/global/**", "/css/**", "/fonts/**", "/vendor/**", "/favicon.ico",
						"/api/pxe/**",
						"/jobs", "/jobs/**");
	}
}
