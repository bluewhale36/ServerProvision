package com.example.serverprovision.global.security.springsecurity.controller;

import com.example.serverprovision.global.security.springsecurity.domain.Role;
import com.example.serverprovision.global.security.springsecurity.dto.request.SignUpRequest;
import com.example.serverprovision.global.security.springsecurity.service.UsersService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;

@Controller
@RequiredArgsConstructor
public class UsersController {

	private final UsersService usersService;

	@GetMapping("/signup")
	public String signup(Model model) {
		model.addAttribute("roles", Role.values());
		return "signup";
	}

	@PostMapping("/signup")
	public String signup(@ModelAttribute SignUpRequest signUpRequest) {
		usersService.signup(signUpRequest);
		return "redirect:/login";
	}
}
