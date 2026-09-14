package com.example.serverprovision.global.security.springsecurity.controller;

import com.example.serverprovision.global.security.springsecurity.service.UsersService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/** 로그인 화면 — 제출은 Spring Security 의 필터가 처리하고, 이 컨트롤러는 화면과 "최초 관리자 등록" 링크의 노출만 맡는다(S18 D-1). */
@Controller
@RequiredArgsConstructor
public class LoginController {

	private final UsersService usersService;

	@GetMapping("/login")
	public String login(Model model) {
		model.addAttribute("bootstrapOpen", usersService.bootstrapOpen());
		return "security/login";
	}
}
