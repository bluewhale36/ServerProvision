package com.example.serverprovision.global.ui.controller;

import com.example.serverprovision.global.marker.ResourceType;
import com.example.serverprovision.global.trash.service.TypedNameVerifier;
import com.example.serverprovision.global.ui.enums.ConfirmModalType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * S5-6 — modal fragment lazy-load 의 단일 entry endpoint.
 *
 * <p>page 진입 시점에 modal markup 을 미리 렌더하지 않고, 사용자가 trigger 한 시점에 본 endpoint 가
 * Thymeleaf fragment HTML 을 응답한다. (resourceType, resourceId) 로 자원 lookup 까지 본 시점에 수행 →
 * typed-name expected value 같은 민감 정보의 페이지 진입 시 DOM 노출을 차단.</p>
 *
 * <p>modal 별 분기는 {@link ConfirmModalType} enum 의 abstract method 다형성으로 흡수 — controller 는
 * <code>modalType.resolveModel(...)</code> + <code>modalType.fragmentView()</code> 만 호출.</p>
 */
@Slf4j
@Controller
@RequestMapping("/ui/confirm-modal")
@RequiredArgsConstructor
public class ConfirmModalFragmentController {

	private final TypedNameVerifier typedNameVerifier;

	/**
	 * modalType + resourceType + resourceId 로 modal fragment 응답.
	 *
	 * @param modalType    {@link ConfirmModalType} (path) — Spring 의 enum 바인딩 실패 시 400 자동 매핑
	 * @param resourceType {@link ResourceType} (query) — 동일
	 * @param resourceId   자원 PK (query)
	 * @return Thymeleaf fragment view name ({@code "templateName :: fragmentName"} 형식)
	 */
	@GetMapping("/{modalType}")
	public String fragment(
			@PathVariable("modalType") ConfirmModalType modalType,
			@RequestParam("resourceType") ResourceType resourceType,
			@RequestParam("resourceId") Long resourceId,
			Model model
	) {
		log.debug(
				"[confirm-modal] type={} resourceType={} resourceId={}",
				modalType, resourceType, resourceId
		);
		modalType.resolveModel(resourceType, resourceId, typedNameVerifier, model);
		return modalType.fragmentView();
	}
}
