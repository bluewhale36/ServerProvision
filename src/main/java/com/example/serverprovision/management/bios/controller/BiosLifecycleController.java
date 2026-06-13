package com.example.serverprovision.management.bios.controller;

import com.example.serverprovision.global.lifecycle.DeleteIntentRegistry;
import com.example.serverprovision.global.lifecycle.DeleteIntentToken;
import com.example.serverprovision.global.marker.ResourceType;
import com.example.serverprovision.management.bios.service.BiosService;
import com.example.serverprovision.management.common.dto.request.DeleteIntentRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

/**
 * BIOS 번들의 lifecycle 상태 전이 진입점 —
 * toggle / soft-delete / delete-intent / restore / deprecate / undeprecate / purge.
 *
 * <p>R4-1 — fat {@code BiosController} 6분할 결과. SSR 폼 submit 경로는 redirect 로,
 * delete-intent (MK3-2 reject modal) 만 XHR JSON 204 로 응답한다. 도메인 가드 예외는
 * 기존 advice 가 일괄 매핑 — 컨트롤러 try/catch 추가 금지.</p>
 */
@Controller
@RequestMapping("/management/bios")
@RequiredArgsConstructor
public class BiosLifecycleController {

	private final BiosService biosService;
	private final DeleteIntentRegistry deleteIntentRegistry;

	// ==== 상태 전이 =====================================================

	@PostMapping("/{boardId}/bios/{biosId}/toggle")
	public String toggle(
			@PathVariable("boardId") Long boardId,
			@PathVariable("biosId") Long biosId
	) {
		biosService.toggleEnabled(boardId, biosId);
		return BiosControllerSupport.redirectToListWithSelect(biosId);
	}

	@PostMapping("/{boardId}/bios/{biosId}/delete")
	public String delete(
			@PathVariable("boardId") Long boardId,
			@PathVariable("biosId") Long biosId
	) {
		biosService.softDelete(boardId, biosId);
		return "redirect:/management/bios?selectBoardId=" + boardId;
	}

	/**
	 * MK3-2 (DCM3-2.3) — softDelete reject modal 의 두 번째 호출 (XHR JSON).
	 */
	@PostMapping(path = "/{boardId}/bios/{biosId}/delete-intent/{token}", produces = "application/json")
	@ResponseBody
	public ResponseEntity<Void> deleteWithIntent(
			@PathVariable("boardId") Long boardId,
			@PathVariable("biosId") Long biosId,
			@PathVariable("token") String token,
			@Valid @RequestBody DeleteIntentRequest request
	) {
		DeleteIntentToken parsed = DeleteIntentToken.parse(token);
		deleteIntentRegistry.consume(parsed, ResourceType.BIOS_BUNDLE, biosId);
		biosService.softDeleteWithIntent(boardId, biosId, request.action());
		return ResponseEntity.noContent().build();
	}

	@PostMapping("/{boardId}/bios/{biosId}/restore")
	public String restore(
			@PathVariable("boardId") Long boardId,
			@PathVariable("biosId") Long biosId
	) {
		biosService.restore(boardId, biosId);
		return BiosControllerSupport.redirectToListWithSelect(biosId);
	}

	// ==== MK2 — Deprecate / Undeprecate / Purge ========================

	/**
	 * Active → Deprecated. SSR 폼 submit 진입 (XHR 아님). 엔티티 가드가 부적합 상태를 거절하면
	 * advice 가 409 + error.html 로 회신.
	 */
	@PostMapping("/{boardId}/bios/{biosId}/deprecate")
	public String deprecate(
			@PathVariable("boardId") Long boardId,
			@PathVariable("biosId") Long biosId
	) {
		biosService.deprecate(boardId, biosId);
		return BiosControllerSupport.redirectToListWithSelect(biosId);
	}

	/**
	 * Deprecated → Active. SSR 폼 submit.
	 */
	@PostMapping("/{boardId}/bios/{biosId}/undeprecate")
	public String undeprecate(
			@PathVariable("boardId") Long boardId,
			@PathVariable("biosId") Long biosId
	) {
		biosService.undeprecate(boardId, biosId);
		return BiosControllerSupport.redirectToListWithSelect(biosId);
	}

	/**
	 * SoftDeleted 자원의 영구 삭제. 활성/Deprecated 자원에 호출되면 advice 가 409 회신.
	 */
	@PostMapping("/{boardId}/bios/{biosId}/purge")
	public String purge(
			@PathVariable("boardId") Long boardId,
			@PathVariable("biosId") Long biosId,
			@RequestParam("typedName") String typedName
	) {
		biosService.purgeWithTypedNameCheck(boardId, biosId, typedName);
		return "redirect:/management/bios?selectBoardId=" + boardId + "&includeDeleted=true";
	}
}
