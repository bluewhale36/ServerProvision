package com.example.serverprovision.management.raidcard.controller;

import com.example.serverprovision.management.raidcard.service.RaidCardLifecycleService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * MA7. RAID 카드의 lifecycle 상태 전이 진입점 —
 * toggle / softDelete / restore / purge / deprecate / undeprecate (BoardModel R3-2 선례).
 *
 * <p>성공 시 Miller 의 selectId 를 보존하며 목록으로 리다이렉트한다
 * (softDelete / purge 는 row 가 시야에서 사라지므로 고정 목록으로 이동).</p>
 */
@Controller
@RequestMapping("/management/raidcard")
@RequiredArgsConstructor
public class RaidCardLifecycleController {

	private final RaidCardLifecycleService raidCardService;

	// ==== 상태 전이 ===================================================

	@PostMapping("/{id}/toggle")
	public String toggle(@PathVariable Long id) {
		raidCardService.toggleEnabled(id);
		return RaidCardControllerSupport.redirectToListWithSelect(id);
	}

	@PostMapping("/{id}/delete")
	public String delete(@PathVariable Long id) {
		raidCardService.softDelete(id);
		// 삭제된 항목은 기본 보기에서 사라지므로 선택 복원 없이 전체 목록으로 이동
		return "redirect:/management/raidcard";
	}

	@PostMapping("/{id}/restore")
	public String restore(
			@PathVariable Long id,
			@RequestParam(name = "cascade", defaultValue = "false") boolean cascade
	) {
		raidCardService.restore(id, cascade);
		return RaidCardControllerSupport.redirectToListWithSelect(id);
	}

	// ==== hard-delete with typed-name 검증 =============================

	@PostMapping("/{id}/purge")
	public String purge(
			@PathVariable Long id,
			@RequestParam("typedName") String typedName
	) {
		raidCardService.purgeWithTypedNameCheck(id, typedName);
		return "redirect:/management/raidcard?includeDeleted=true";
	}

	// ==== Deprecate / Undeprecate =====================================

	@PostMapping("/{id}/deprecate")
	public String deprecate(@PathVariable Long id) {
		raidCardService.deprecate(id);
		return RaidCardControllerSupport.redirectToListWithSelect(id);
	}

	@PostMapping("/{id}/undeprecate")
	public String undeprecate(@PathVariable Long id) {
		raidCardService.undeprecate(id);
		return RaidCardControllerSupport.redirectToListWithSelect(id);
	}
}
