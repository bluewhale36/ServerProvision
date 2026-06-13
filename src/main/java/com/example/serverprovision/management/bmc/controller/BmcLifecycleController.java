package com.example.serverprovision.management.bmc.controller;

import com.example.serverprovision.global.lifecycle.DeleteIntentRegistry;
import com.example.serverprovision.global.lifecycle.DeleteIntentToken;
import com.example.serverprovision.global.marker.ResourceType;
import com.example.serverprovision.management.bmc.service.BmcService;
import com.example.serverprovision.management.common.dto.request.DeleteIntentRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

/**
 * MA4 BMC 펌웨어 lifecycle (toggle / softDelete / restore / deprecate / undeprecate / purge)
 * MVC 컨트롤러.
 *
 * <p>R5-1 분할 — 단일 {@code BmcController} 에서 상태 전이 책임을 분리.
 * 의존성: {@link BmcService} (도메인 hub), {@link DeleteIntentRegistry} (softDelete reject
 * modal 의 2차 호출 token 검증).</p>
 */
@Controller
@RequestMapping("/management/bmc")
@RequiredArgsConstructor
public class BmcLifecycleController {

	private final BmcService bmcService;
	private final DeleteIntentRegistry deleteIntentRegistry;

	@PostMapping("/{boardId}/bmc/{bmcId}/toggle")
	public String toggle(
			@PathVariable("boardId") Long boardId,
			@PathVariable("bmcId") Long bmcId
	) {
		bmcService.toggleEnabled(boardId, bmcId);
		return BmcControllerSupport.redirectToListWithSelect(bmcId);
	}

	@PostMapping("/{boardId}/bmc/{bmcId}/delete")
	public String delete(
			@PathVariable("boardId") Long boardId,
			@PathVariable("bmcId") Long bmcId
	) {
		bmcService.softDelete(boardId, bmcId);
		return "redirect:/management/bmc?selectBoardId=" + boardId;
	}

	/**
	 * MK3-2 (DCM3-2.3) — softDelete reject modal 의 두 번째 호출 (XHR JSON).
	 */
	@PostMapping(path = "/{boardId}/bmc/{bmcId}/delete-intent/{token}", produces = "application/json")
	@ResponseBody
	public ResponseEntity<Void> deleteWithIntent(
			@PathVariable("boardId") Long boardId,
			@PathVariable("bmcId") Long bmcId,
			@PathVariable("token") String token,
			@Valid @RequestBody DeleteIntentRequest request
	) {
		DeleteIntentToken parsed = DeleteIntentToken.parse(token);
		deleteIntentRegistry.consume(parsed, ResourceType.BMC_FIRMWARE, bmcId);
		bmcService.softDeleteWithIntent(boardId, bmcId, request.action());
		return ResponseEntity.noContent().build();
	}

	@PostMapping("/{boardId}/bmc/{bmcId}/restore")
	public String restore(
			@PathVariable("boardId") Long boardId,
			@PathVariable("bmcId") Long bmcId
	) {
		bmcService.restore(boardId, bmcId);
		return BmcControllerSupport.redirectToListWithSelect(bmcId);
	}

	// ==== MK2 lifecycle 액션 (Deprecated 토글 + 영구 삭제) =================

	@PostMapping("/{boardId}/bmc/{bmcId}/deprecate")
	public String deprecate(
			@PathVariable("boardId") Long boardId,
			@PathVariable("bmcId") Long bmcId
	) {
		bmcService.deprecate(boardId, bmcId);
		return BmcControllerSupport.redirectToListWithSelect(bmcId);
	}

	@PostMapping("/{boardId}/bmc/{bmcId}/undeprecate")
	public String undeprecate(
			@PathVariable("boardId") Long boardId,
			@PathVariable("bmcId") Long bmcId
	) {
		bmcService.undeprecate(boardId, bmcId);
		return BmcControllerSupport.redirectToListWithSelect(bmcId);
	}

	/**
	 * 영구 삭제. 휴지통(soft-deleted) 상태 한정 — 가드는 Service 가 수행.
	 */
	@PostMapping("/{boardId}/bmc/{bmcId}/purge")
	public String purge(
			@PathVariable("boardId") Long boardId,
			@PathVariable("bmcId") Long bmcId,
			@RequestParam("typedName") String typedName
	) {
		bmcService.purgeWithTypedNameCheck(boardId, bmcId, typedName);
		return "redirect:/management/bmc?selectBoardId=" + boardId + "&includeDeleted=true";
	}
}
