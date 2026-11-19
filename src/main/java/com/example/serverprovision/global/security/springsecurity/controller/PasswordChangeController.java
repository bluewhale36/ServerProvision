package com.example.serverprovision.global.security.springsecurity.controller;

import com.example.serverprovision.global.security.springsecurity.domain.UserDetails_Impl;
import com.example.serverprovision.global.exception.FieldBoundBadRequestException;
import com.example.serverprovision.global.security.springsecurity.authorization.MustChangePasswordSuccessHandler;
import com.example.serverprovision.global.security.springsecurity.authorization.SessionPrincipalRefresher;
import com.example.serverprovision.global.security.springsecurity.dto.request.PasswordChangeRequest;
import com.example.serverprovision.global.security.springsecurity.service.UsersService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/** 비밀번호 변경(S18 D-15) — 강제 변경(첫 로그인)과 자발 변경이 같은 화면이다. 성공하면 세션의 principal 을 갱신해 인터셉터가 풀린다. */
@Controller
@RequestMapping(MustChangePasswordSuccessHandler.CHANGE_PASSWORD_PATH)
@RequiredArgsConstructor
public class PasswordChangeController {

	private final UsersService usersService;
	private final SessionPrincipalRefresher principalRefresher;

	@GetMapping
	public String form(Model model) {
		return render(model, new PasswordChangeRequest("", "", ""), forcedNow());
	}

	@PostMapping
	public String change(@Valid @ModelAttribute("passwordForm") PasswordChangeRequest form,
	                     BindingResult bindingResult,
	                     Model model,
	                     HttpServletRequest request,
	                     HttpServletResponse response,
	                     RedirectAttributes redirectAttributes) {
		boolean forced = forcedNow();
		if (bindingResult.hasErrors()) {
			response.setStatus(HttpStatus.BAD_REQUEST.value());
			return render(model, form, forced);
		}
		String username = currentUsername();
		try {
			usersService.changePassword(username, form);
		} catch (FieldBoundBadRequestException ex) {
			bindingResult.rejectValue(ex.fieldName(), "fieldBound", ex.getMessage());
			response.setStatus(HttpStatus.BAD_REQUEST.value());
			return render(model, form, forced);
		}
		principalRefresher.refresh(username, request, response);
		redirectAttributes.addFlashAttribute("flashMessage", "비밀번호를 바꿨습니다.");
		return "redirect:/provisioning/server";   // "/" 는 MainController 가 한 번 더 redirect 해 flash 가 사라진다(CP5 F-1) — 실제 홈으로 바로
	}

	/**
	 * 강제 변경 중인가(HF28 · docs C-25) — 강제 변경 인터셉터와 같은 값(세션 principal 의 {@code mustChangePassword()})으로 판정한다.
	 * 종전에는 주소의 {@code ?first=1} 로 판정해, 사이드바 링크로 들어오면 강제 계정인데도 강제 아님으로 그려졌다(취소 왕복).
	 * 주소에 {@code first} 가 붙어 와도 판정에 쓰지 않는다(인터셉터의 되돌림 주소 호환).
	 */
	private static boolean forcedNow() {
		var authentication = SecurityContextHolder.getContext().getAuthentication();
		return authentication != null && authentication.getPrincipal() instanceof UserDetails_Impl principal
				&& principal.mustChangePassword();
	}

	/** 세션 컨텍스트에서 읽는다 — {@code @AuthenticationPrincipal} 은 보안 MVC 통합이 없는 슬라이스(테스트)에서 해석되지 않는다. */
	private static String currentUsername() {
		return SecurityContextHolder.getContext().getAuthentication().getName();
	}

	private String render(Model model, PasswordChangeRequest form, boolean forced) {
		model.addAttribute("passwordForm", form);
		model.addAttribute("forced", forced);
		return "security/password-change";
	}
}
