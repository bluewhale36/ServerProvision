package com.example.serverprovision.global.security.springsecurity.controller;

import com.example.serverprovision.global.exception.FieldBoundBadRequestException;
import com.example.serverprovision.global.exception.FieldBoundConflictException;
import com.example.serverprovision.global.security.springsecurity.domain.Role;
import com.example.serverprovision.global.security.springsecurity.domain.SignupMode;
import com.example.serverprovision.global.security.springsecurity.dto.request.SignUpRequest;
import com.example.serverprovision.global.security.springsecurity.entity.Users;
import com.example.serverprovision.global.security.springsecurity.service.UsersService;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * 계정 생성 폼의 제출 · 재렌더(MA8) — 부트스트랩({@code /signup})과 관리 생성({@code /users})이 같은 폼 조각과 같은 규칙을 쓰므로
 * 두 컨트롤러에 복붙하지 않고 여기 한 벌로 둔다. 경로마다 다른 것(권한 · 뷰 · 성공 이동)은 {@link SignupMode} 가 정한다.
 */
@Component
@RequiredArgsConstructor
public class SignupSubmission {

	private final UsersService usersService;

	public String submit(SignupMode mode, SignUpRequest request, BindingResult bindingResult, Model model,
	                     HttpServletResponse response, RedirectAttributes redirectAttributes) {
		// 위조값(role=NOPE)은 바인딩이 이미 typeMismatch 를 남겼다 — 같은 칸에 두 메시지를 겹치지 않는다(S18 CP5 B7).
		if (mode.requiresRoleSelection() && request.getRole() == null && !bindingResult.hasFieldErrors("role")) {
			bindingResult.rejectValue("role", "selection-required", "권한 등급을 고르십시오.");   // 코드 "required" 는 messages 의 일반 문구가 이긴다
		}
		if (!bindingResult.hasErrors() && usersService.usernameTaken(request.username())) {
			bindingResult.rejectValue("username", "duplicate", "이미 사용 중인 아이디입니다.");   // UI 1차 차단 — 서비스와 같은 판정
		}
		if (bindingResult.hasErrors()) {
			response.setStatus(HttpStatus.BAD_REQUEST.value());
			return render(model, mode, request);
		}
		Users created;
		try {
			created = usersService.signup(request, mode);
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
		return mode.successRedirect(created.getId());
	}

	/** 두 경로가 같은 폼 조각을 쓰므로 모델 재료도 하나로 조립한다. 비밀번호 두 칸은 재렌더에서 비운다. */
	public String render(Model model, SignupMode mode, SignUpRequest request) {
		model.addAttribute("signUpForm", request);
		model.addAttribute("signupMode", mode);
		model.addAttribute("roleOptions", Role.values());
		return mode.view();
	}

	public static SignUpRequest emptyForm() {
		return new SignUpRequest("", "", "", "", null);
	}
}
