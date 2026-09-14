package com.example.serverprovision.global.security.springsecurity.controller;

import com.example.serverprovision.global.exception.FieldBoundBadRequestException;
import com.example.serverprovision.global.exception.FieldBoundConflictException;
import com.example.serverprovision.global.security.springsecurity.domain.Role;
import com.example.serverprovision.global.security.springsecurity.domain.SignupMode;
import com.example.serverprovision.global.security.springsecurity.dto.request.SignUpRequest;
import com.example.serverprovision.global.security.springsecurity.service.UsersService;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;

/**
 * 가입 = 접근 사용자 등록(S18 D-13). 인가는 {@code SignupAccessPolicy}(관리자 부재면 누구나 · 있으면 ROLE_ADMIN)가 앞에서 끝내고,
 * 여기서는 같은 판정으로 모드를 고른다. 검증 실패 · 중복 · 불일치는 같은 뷰를 다시 렌더한다(필드 오류).
 */
@Controller
@RequiredArgsConstructor
public class UsersController {

	private final UsersService usersService;

	@GetMapping("/signup")
	public String signup(Model model) {
		SignupMode mode = currentMode();
		return render(model, mode, new SignUpRequest("", "", "", "", List.of()));
	}

	@PostMapping("/signup")
	public String signup(@Valid @ModelAttribute("signUpForm") SignUpRequest request,
	                     BindingResult bindingResult,
	                     Model model,
	                     HttpServletResponse response,
	                     RedirectAttributes redirectAttributes) {
		SignupMode mode = currentMode();
		// 위조값(roles=NOPE)은 바인딩이 이미 typeMismatch 를 남겼다 — 같은 칸에 두 메시지를 겹치지 않는다(CP5 B7).
		if (mode.requiresRoleSelection() && request.rolesOrEmpty().isEmpty() && !bindingResult.hasFieldErrors("roles")) {
			bindingResult.rejectValue("roles", "selection-required", "역할을 하나 이상 고르십시오.");   // 코드 "required" 는 messages 의 일반 문구가 이긴다
		}
		if (!bindingResult.hasErrors() && usersService.usernameTaken(request.username())) {
			bindingResult.rejectValue("username", "duplicate", "이미 사용 중인 아이디입니다.");   // UI 1차 차단 — 서비스와 같은 판정
		}
		if (bindingResult.hasErrors()) {
			response.setStatus(HttpStatus.BAD_REQUEST.value());
			return render(model, mode, request);
		}
		try {
			usersService.signup(request, mode);
		} catch (FieldBoundConflictException ex) {           // 레이스 · direct POST 안전망 — 예외가 필드를 들고 온다
			bindingResult.rejectValue(ex.fieldName(), "fieldBound", ex.getMessage());
			response.setStatus(HttpStatus.CONFLICT.value());
			return render(model, mode, request);
		} catch (FieldBoundBadRequestException ex) {
			bindingResult.rejectValue(ex.fieldName(), "fieldBound", ex.getMessage());
			response.setStatus(HttpStatus.BAD_REQUEST.value());
			return render(model, mode, request);
		}
		redirectAttributes.addFlashAttribute("flashMessage", mode.successMessage(request.username().trim()));
		return mode.successRedirect();
	}

	private SignupMode currentMode() {
		return SignupMode.of(usersService.bootstrapOpen());
	}

	/** 두 모드가 같은 폼 fragment 를 쓰므로 모델 재료도 하나로 조립한다. 비밀번호 두 칸은 재렌더에서 비운다. */
	private String render(Model model, SignupMode mode, SignUpRequest request) {
		model.addAttribute("signUpForm", request);
		model.addAttribute("signupMode", mode);
		model.addAttribute("roleOptions", Role.values());
		return mode.view();
	}
}
