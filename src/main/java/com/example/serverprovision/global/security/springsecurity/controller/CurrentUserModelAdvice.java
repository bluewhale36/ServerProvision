package com.example.serverprovision.global.security.springsecurity.controller;

import com.example.serverprovision.global.security.springsecurity.domain.UserDetails_Impl;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

/** 레이아웃의 계정 영역(S18 D-16)이 읽는 현재 사용자 — 비인증(로그인 화면 · 게스트 채널)은 null. */
@ControllerAdvice
public class CurrentUserModelAdvice {

	/**
	 * @param mustChangePassword 초기 비밀번호 강제 변경 중인가(HF28) — 사이드바가 메뉴를 잠그는 근거. 강제 변경 인터셉터 · 변경 화면과
	 *                           같은 값(세션 principal 의 {@code mustChangePassword()})이라 화면과 서버 가드가 어긋나지 않는다
	 */
	public record CurrentUser(String username, String name, boolean superAdmin, boolean mustChangePassword) {
	}

	@ModelAttribute("currentUser")
	public CurrentUser currentUser() {
		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
		if (authentication == null || !(authentication.getPrincipal() instanceof UserDetails_Impl principal)) {
			return null;
		}
		return new CurrentUser(principal.getUsername(), principal.displayName(), principal.isSuperAdmin(),
				principal.mustChangePassword());
	}
}
