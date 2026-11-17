package com.example.serverprovision.global.security.springsecurity.authorization;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** 강제 변경 인터셉터 등록 — 변경 화면 · 인증 흐름 · 오류 · 정적 자산 · 게스트 채널 · 배경 작업 폴링 · 사용 매뉴얼은 제외한다. */
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
						"/global/**", "/css/**", "/fonts/**", "/icons/**", "/vendor/**", "/favicon.ico",
						"/api/pxe/**",
						"/jobs", "/jobs/**",
						// HF28 — 사용 매뉴얼은 첫 로그인 안내를 담은 정적 문서라 비밀번호를 바꾸기 전에도 읽을 수 있어야 한다(docs B-1)
						"/manual", "/manual/**");
	}
}
