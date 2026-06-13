package com.example.serverprovision.management.board.controller;

import com.example.serverprovision.management.board.service.metadata.BoardModelLifecycleService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * A2. 메인보드 모델의 lifecycle 상태 전이 진입점 —
 * toggle / softDelete / restore / purge / deprecate / undeprecate.
 *
 * <p>R3-2 — 단일 fat {@code BoardModelController} 분할. 메타 CRUD 는 {@link BoardModelMetadataController},
 * nudge confirm 은 {@link BoardModelNudgeController} 가 관할.</p>
 *
 * <p>본 컨트롤러의 의존성은 {@link BoardModelLifecycleService} 단독 (R3-3). 성공 시 Miller 의 selectId 를 보존하며
 * 목록으로 리다이렉트한다 (softDelete / purge 는 row 가 시야에서 사라지므로 고정 목록으로 이동).</p>
 */
@Controller
@RequestMapping("/management/board")
@RequiredArgsConstructor
public class BoardModelLifecycleController {

	private final BoardModelLifecycleService boardModelService;

	// ==== 상태 전이 ===================================================

	@PostMapping("/{id}/toggle")
	public String toggle(@PathVariable Long id) {
		boardModelService.toggleEnabled(id);
		return BoardControllerSupport.redirectToListWithSelect(id);
	}

	@PostMapping("/{id}/delete")
	public String delete(@PathVariable Long id) {
		boardModelService.softDelete(id);
		// 삭제된 항목은 기본 보기에서 사라지므로 선택 복원 없이 전체 목록으로 이동
		return "redirect:/management/board";
	}

	@PostMapping("/{id}/restore")
	public String restore(
			@PathVariable Long id,
			@RequestParam(name = "cascade", defaultValue = "false") boolean cascade
	) {
		boardModelService.restore(id, cascade);
		return BoardControllerSupport.redirectToListWithSelect(id);
	}

	// ==== S5-2-2 — hard-delete with typed-name 검증 ====================
	@PostMapping("/{id}/purge")
	public String purge(
			@PathVariable Long id,
			@RequestParam("typedName") String typedName
	) {
		boardModelService.purgeWithTypedNameCheck(id, typedName);
		return "redirect:/management/board?includeDeleted=true";
	}

	// ==== MK2 — Deprecate / Undeprecate ===============================

	@PostMapping("/{id}/deprecate")
	public String deprecate(@PathVariable Long id) {
		boardModelService.deprecate(id);
		return BoardControllerSupport.redirectToListWithSelect(id);
	}

	@PostMapping("/{id}/undeprecate")
	public String undeprecate(@PathVariable Long id) {
		boardModelService.undeprecate(id);
		return BoardControllerSupport.redirectToListWithSelect(id);
	}
}
