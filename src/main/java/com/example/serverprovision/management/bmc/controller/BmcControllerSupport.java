package com.example.serverprovision.management.bmc.controller;

import com.example.serverprovision.global.marker.ResourceType;
import com.example.serverprovision.global.trash.service.TypedNameVerifier;
import com.example.serverprovision.management.board.dto.response.BoardModelResponse;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;

/**
 * BMC 컨트롤러 분할 (R5-1) 후 6 기능 컨트롤러가 공통으로 쓰는 정적 헬퍼.
 *
 * <p>이전엔 단일 {@code BmcController} 의 private (static) 메서드로 같은 로직을 보관했는데,
 * 컨트롤러를 6 개로 분리하면서 redirect URL 조립 / 폼 컨텍스트 채움 / null 보정 / BindingResult
 * 첫-에러 추출 / typedName guard 가 여러 컨트롤러에 동시에 필요해졌다. CLAUDE.md 의 "중복 로직은
 * 즉시 공통 유틸로 추출" 원칙에 따라 정적 헬퍼 클래스로 승격.</p>
 *
 * <p>{@code typedNameGuard} 는 {@link TypedNameVerifier} 를 인자로 받는 정적 함수(컨트롤러가
 * 주입받은 verifier 를 넘김)로 두어 Support 가 Spring bean 의존을 갖지 않게 한다.</p>
 */
public final class BmcControllerSupport {

	private BmcControllerSupport() {
	}

	/**
	 * BMC 목록 페이지로 redirect 하되 Miller 의 selectId 를 보존한다.
	 * 단일 자원의 상태 전이 (update / toggle / restore / deprecate / undeprecate 등) 후 호출자가 사용.
	 */
	public static String redirectToListWithSelect(Long selectId) {
		return "redirect:/management/bmc?selectId=" + selectId;
	}

	/**
	 * BMC 폼 (신규 / 수정) 의 보조 모델 채움. boardId 와 contextLabel 은 두 폼 모두 필요하고,
	 * bmcId 는 수정 폼에만 의미가 있어 nullable 로 처리.
	 */
	public static void populateFormContext(Model model, Long boardId, Long bmcId, BoardModelResponse board) {
		model.addAttribute("boardId", boardId);
		model.addAttribute("bmcId", bmcId);
		model.addAttribute("contextLabel", board.vendor().getDisplayName() + " · " + board.modelName());
	}

	/**
	 * Thymeleaf 폼 binding 안전화 — null 문자열을 빈 문자열로 변환.
	 */
	public static String nullToEmpty(String value) {
		return value == null ? "" : value;
	}

	/**
	 * BindingResult 의 첫 필드 에러를 "field: message" 형태의 단일 메시지로 추출한다.
	 * Layer A 검증 실패 시 JSON 응답 바디로 회신할 메시지를 만든다 (intent / upload / register 공용).
	 */
	public static String firstError(BindingResult bindingResult) {
		return bindingResult.getFieldErrors().stream()
				.findFirst()
				.map(err -> err.getField() + ": " + err.getDefaultMessage())
				.orElse("입력값이 올바르지 않습니다.");
	}

	/**
	 * typedName 이 비어있지 않으면 검증한다 (nudge replace / intent-nudge replace 공용).
	 * Support 가 bean 의존을 갖지 않도록 verifier 를 인자로 받는다.
	 */
	public static void typedNameGuard(TypedNameVerifier verifier, ResourceType resourceType, Long targetId, String typedName) {
		if (typedName != null && !typedName.isBlank()) {
			verifier.verify(resourceType, targetId, typedName);
		}
	}
}
