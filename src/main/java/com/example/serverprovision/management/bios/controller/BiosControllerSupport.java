package com.example.serverprovision.management.bios.controller;

import com.example.serverprovision.global.exception.ApiErrorResponse;
import com.example.serverprovision.management.board.dto.response.BoardModelResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;

/**
 * BIOS 컨트롤러 6분할 후 공통으로 쓰이는 view / 응답 헬퍼.
 *
 * <p>이전엔 {@code BiosController} 안 private 메서드로 같은 로직을 보관했는데, 컨트롤러를
 * Metadata / Lifecycle / Upload / Nudge / Integrity / Browse 6 개로 분리하면서 redirect URL 조립 /
 * 폼 컨텍스트 채움 / null 보정 / BindingResult 첫 필드 에러 메시지 조립이 여러 컨트롤러에 동시에
 * 필요해졌다. CLAUDE.md 의 "중복 로직은 즉시 공통 유틸로 추출" 원칙에 따라 정적 헬퍼 클래스로 승격.
 * (선례 : {@code OSControllerSupport})</p>
 */
public final class BiosControllerSupport {

	private BiosControllerSupport() {
	}

	/**
	 * BIOS 목록 페이지로 redirect 하되 Miller 의 selectId 를 보존한다.
	 * 단일 자원의 상태 전이 (update / toggle / restore / deprecate / undeprecate) 후 호출자가 사용.
	 */
	public static String redirectToListWithSelect(Long selectId) {
		return "redirect:/management/bios?selectId=" + selectId;
	}

	/**
	 * BIOS 폼 (신규 / 수정) 의 보조 모델 채움. boardId 와 contextLabel (예: "Gigabyte · MS73-HB1") 은
	 * 두 폼 모두 필요하고, biosId 는 수정 폼에만 필요해 nullable 로 처리.
	 */
	public static void populateFormContext(Model model, Long boardId, Long biosId, BoardModelResponse board) {
		model.addAttribute("boardId", boardId);
		if (biosId != null) {
			model.addAttribute("biosId", biosId);
		}
		model.addAttribute(
				"contextLabel",
				board.vendor().getDisplayName() + " · " + board.modelName()
		);
	}

	/**
	 * Thymeleaf 폼 binding 안전화 — null 문자열을 빈 문자열로 변환.
	 */
	public static String nullToEmpty(String value) {
		return value == null ? "" : value;
	}

	/**
	 * MK2 — Layer A (BindingResult) 검증 실패만 직접 응답으로 조립. 도메인 예외는 advice 가 일괄 처리.
	 * intent / upload / register-existing 의 badRequest 메시지 조립 복붙을 단일 소스로 흡수.
	 */
	public static ResponseEntity<?> badRequestFromBinding(BindingResult bindingResult) {
		String msg = bindingResult.getFieldErrors().stream()
				.findFirst()
				.map(err -> err.getField() + ": " + err.getDefaultMessage())
				.orElse("입력값이 올바르지 않습니다.");
		return ResponseEntity.badRequest().body(new ApiErrorResponse(msg));
	}
}
