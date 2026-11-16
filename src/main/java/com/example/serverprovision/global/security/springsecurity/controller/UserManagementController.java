package com.example.serverprovision.global.security.springsecurity.controller;

import com.example.serverprovision.global.exception.FieldBoundBadRequestException;
import com.example.serverprovision.global.security.springsecurity.domain.PasswordRule;
import com.example.serverprovision.global.security.springsecurity.domain.Role;
import com.example.serverprovision.global.security.springsecurity.domain.SignupMode;
import com.example.serverprovision.global.security.springsecurity.dto.request.InitialPasswordRequest;
import com.example.serverprovision.global.security.springsecurity.dto.request.RoleChangeRequest;
import com.example.serverprovision.global.security.springsecurity.dto.request.SignUpRequest;
import com.example.serverprovision.global.security.springsecurity.dto.request.UserListQuery;
import com.example.serverprovision.global.security.springsecurity.enums.UserSortField;
import com.example.serverprovision.global.security.springsecurity.service.UserManagementService;
import com.example.serverprovision.global.security.springsecurity.service.UsersQueryService;
import com.example.serverprovision.global.web.list.ListDateRangeDefaults;
import com.example.serverprovision.global.web.list.ListLinks;
import com.example.serverprovision.global.web.list.Paging;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.security.Principal;
import java.time.LocalDate;

/**
 * 사용자 관리(MA8) — 최고 관리자 전용(인가는 {@code SecurityConfig} 의 {@code /users/**} 규칙이 앞에서 끝낸다).
 * 목록 · 추가 · 상세 · 권한 등급 변경 · 비활성화 · 활성화 · 초기 비밀번호 지정. 동작의 차단 판정은 {@code UserAction.blockReason}
 * 하나를 화면(상세의 disabled 사유)과 서비스 가드가 함께 쓴다.
 */
@Controller
@RequestMapping("/users")
@RequiredArgsConstructor
public class UserManagementController {

	private final UsersQueryService queryService;
	private final UserManagementService managementService;
	private final SignupSubmission signupSubmission;
	private final ListDateRangeDefaults rangeDefaults;

	/** 목록 — 조회 띠의 규칙(조건 바인딩 · 기본 기간 · 정렬 화이트리스트)은 서버 그룹 목록과 같다. */
	@GetMapping
	public String list(@ModelAttribute("query") UserListQuery requested,
	                   @PageableDefault(size = Paging.DEFAULT_SIZE) Pageable pageable,
	                   ListLinks links, Model model) {
		LocalDate today = rangeDefaults.addTo(model);
		UserListQuery query = requested.withDefaultRange(today);
		model.addAttribute("query", query);
		model.addAttribute("filtered", requested.isFiltered(today));
		model.addAttribute("page", queryService.search(query, pageable));
		model.addAttribute("links", links);
		model.addAttribute("sortFields", UserSortField.values());
		model.addAttribute("sortDefault", UserListQuery.DEFAULT_SORT);
		model.addAttribute("levelOptions", Role.values());
		return "users/user-list";
	}

	@GetMapping("/new")
	public String newForm(Model model) {
		return signupSubmission.render(model, SignupMode.MANAGED, SignupSubmission.emptyForm());
	}

	@PostMapping
	public String create(@Valid @ModelAttribute("signUpForm") SignUpRequest request,
	                     BindingResult bindingResult,
	                     Model model,
	                     HttpServletResponse response,
	                     RedirectAttributes redirectAttributes) {
		return signupSubmission.submit(SignupMode.MANAGED, request, bindingResult, model, response, redirectAttributes);
	}

	@GetMapping("/{id}")
	public String detail(@PathVariable Long id, Principal principal, Model model) {
		return renderDetail(id, principal, model, new InitialPasswordRequest());
	}

	@PostMapping("/{id}/role")
	public String changeRole(@PathVariable Long id, @Valid @ModelAttribute RoleChangeRequest request,
	                         Principal principal, RedirectAttributes redirectAttributes) {
		managementService.changeRole(principal.getName(), id, request.getLevel());
		redirectAttributes.addFlashAttribute("flashMessage",
				"권한 등급을 '" + request.getLevel().getDescription() + "' 등급으로 바꿨습니다. 그 사용자는 다시 로그인해야 합니다.");
		return redirectToDetail(id);
	}

	@PostMapping("/{id}/disable")
	public String disable(@PathVariable Long id, Principal principal, RedirectAttributes redirectAttributes) {
		managementService.disable(principal.getName(), id);
		redirectAttributes.addFlashAttribute("flashMessage", "계정을 비활성화했습니다. 그 사용자의 로그인 상태를 끝냈습니다.");
		return redirectToDetail(id);
	}

	@PostMapping("/{id}/enable")
	public String enable(@PathVariable Long id, Principal principal, RedirectAttributes redirectAttributes) {
		managementService.enable(principal.getName(), id);
		redirectAttributes.addFlashAttribute("flashMessage", "계정을 활성화했습니다. 이제 로그인할 수 있습니다.");
		return redirectToDetail(id);
	}

	/** 초기 비밀번호 지정 — 검증 실패는 상세를 다시 렌더한다(필드 오류). 성공하면 그 사용자는 다음 로그인에서 변경을 요구받는다. */
	@PostMapping("/{id}/password")
	public String assignPassword(@PathVariable Long id,
	                             @Valid @ModelAttribute("passwordForm") InitialPasswordRequest request,
	                             BindingResult bindingResult,
	                             Principal principal, Model model, HttpServletResponse response,
	                             RedirectAttributes redirectAttributes) {
		if (bindingResult.hasErrors()) {
			response.setStatus(HttpStatus.BAD_REQUEST.value());
			model.addAttribute("passwordFailed", true);
			return renderDetail(id, principal, model, request);
		}
		try {
			managementService.assignInitialPassword(principal.getName(), id, request);
		} catch (FieldBoundBadRequestException ex) {           // direct POST 안전망 — 재입력 불일치
			bindingResult.rejectValue(ex.fieldName(), "fieldBound", ex.getMessage());
			response.setStatus(HttpStatus.BAD_REQUEST.value());
			model.addAttribute("passwordFailed", true);
			return renderDetail(id, principal, model, request);
		}
		redirectAttributes.addFlashAttribute("flashMessage",
				"초기 비밀번호를 지정했습니다. 그 사용자는 다음 로그인에서 새 비밀번호로 바꿔야 합니다.");
		return redirectToDetail(id);
	}

	private String renderDetail(Long id, Principal principal, Model model, InitialPasswordRequest passwordForm) {
		model.addAttribute("detail", queryService.detail(id, principal.getName()));
		model.addAttribute("passwordForm", passwordForm);
		model.addAttribute("levelOptions", Role.values());
		// CP7 — 제출 전 확인이 쓸 규칙 · 문구. 규칙의 주인은 서버 상수이고 화면은 내려받아 쓰기만 한다(복사본 없음).
		model.addAttribute("passwordRuleRegex", PasswordRule.REGEX);
		model.addAttribute("passwordRuleMessage", PasswordRule.MESSAGE);
		model.addAttribute("passwordRequiredMessage", InitialPasswordRequest.REQUIRED);
		model.addAttribute("passwordRetypeRequiredMessage", InitialPasswordRequest.RETYPE_REQUIRED);
		model.addAttribute("passwordMismatchMessage", InitialPasswordRequest.MISMATCH);
		return "users/user-detail";
	}

	private static String redirectToDetail(Long id) {
		return "redirect:/users/" + id;
	}
}
