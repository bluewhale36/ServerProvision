package com.example.serverprovision.global.security.springsecurity.controller;

import com.example.serverprovision.global.security.springsecurity.domain.SignupMode;
import com.example.serverprovision.global.security.springsecurity.dto.request.SignUpRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * 최초 계정 등록(S18 D-13 · MA8 개정) — 관리자가 하나도 없을 때만 열리고({@code SignupAccessPolicy}), 만드는 계정은 최고 관리자다.
 * 그 뒤의 계정 생성은 사용자 관리({@link UserManagementController})가 맡는다. 검증 실패 · 중복 · 불일치는 같은 뷰를 다시 렌더한다.
 */
@Controller
@RequiredArgsConstructor
public class UsersController {

	private final SignupSubmission signupSubmission;

	@GetMapping("/signup")
	public String signup(Model model) {
		return signupSubmission.render(model, SignupMode.BOOTSTRAP, SignupSubmission.emptyForm());
	}

	@PostMapping("/signup")
	public String signup(@Valid @ModelAttribute("signUpForm") SignUpRequest request,
	                     BindingResult bindingResult,
	                     Model model,
	                     HttpServletResponse response,
	                     RedirectAttributes redirectAttributes) {
		return signupSubmission.submit(SignupMode.BOOTSTRAP, request, bindingResult, model, response, redirectAttributes);
	}
}
