package com.example.serverprovision.management.subprogram.controller;

import com.example.serverprovision.management.board.service.metadata.BoardModelMetadataService;
import com.example.serverprovision.management.common.dto.response.IntegrityStatusResponse;
import com.example.serverprovision.management.subprogram.dto.request.SubprogramCreateRequest;
import com.example.serverprovision.management.subprogram.dto.request.SubprogramUpdateRequest;
import com.example.serverprovision.management.subprogram.dto.response.BoardWithSubprogramListResponse;
import com.example.serverprovision.management.subprogram.dto.response.SubprogramResponse;
import com.example.serverprovision.management.subprogram.enums.SubprogramKind;
import com.example.serverprovision.management.subprogram.service.SubprogramIntegrityService;
import com.example.serverprovision.management.subprogram.service.SubprogramLifecycleService;
import com.example.serverprovision.management.subprogram.service.SubprogramService;
import com.example.serverprovision.management.subprogram.vo.BoardScope;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;

/**
 * MA5 Subprogram (Driver + Utility) 관리 MVC + REST 컨트롤러 — CRUD + lifecycle + REST 조회 (슬림).
 *
 * <p>BIOS / BMC 와 달리 페이지에 Miller 2 개 (Driver / Utility) 가 동시에 떠 있고, 등록 폼은 kind 별로
 * 별도 진입한다.</p>
 *
 * <p>R6-1 — 기능군 분리. 업로드 (intent/upload/register-existing/verify/delete-intent) 는
 * {@link SubprogramUploadController}, nudge confirm 6 종은 {@link SubprogramNudgeController},
 * 디렉토리 탐색 (browse) 은 {@link SubprogramBrowseController} 가 관할. 본 컨트롤러는 Miller 메인 +
 * 신규/편집 폼 + 단순 lifecycle 전이 (toggle/delete/restore/deprecate/undeprecate/purge) + REST 조회
 * (items/detail/integrity-status) 만 잔류. lifecycle 은 단순 service 호출 + redirect 라 별도
 * LifecycleController 신설 시 의존 중복만 늘어 over-split 절제 (슬림 컨트롤러 잔류).</p>
 *
 * <p>MK2 — 도메인 예외 → HTTP 응답 매핑은 {@code ApiExceptionHandler} / {@code WebExceptionHandler} 가
 * 일괄 책임진다. 본 컨트롤러에는 try/catch 없음 (도메인 예외는 그대로 propagate).</p>
 */
@Controller
@RequestMapping("/management/subprogram")
@RequiredArgsConstructor
public class SubprogramController {

	private final SubprogramService subprogramService;
	private final SubprogramLifecycleService subprogramLifecycleService;
	private final SubprogramIntegrityService subprogramIntegrityService;
	private final BoardModelMetadataService boardModelService;

	/* ─────────────────────────── 메인 페이지 ─────────────────────────── */

	@GetMapping
	public String list(
			@RequestParam(name = "includeDeleted", defaultValue = "false") boolean includeDeleted,
			@RequestParam(name = "selectId", required = false) Long selectId,
			@RequestParam(name = "selectKind", required = false) SubprogramKind selectKind,
			// S5-4 — C1 (scope = boardId 또는 'common') 선택 보존. selectKind 와 함께 어느 미러를 가리키는지 결정.
			@RequestParam(name = "selectKey", required = false) String selectKey,
			Model model
	) {
		model.addAttribute("driverGroups", subprogramService.findAllGrouped(SubprogramKind.DRIVER, includeDeleted));
		model.addAttribute("utilityGroups", subprogramService.findAllGrouped(SubprogramKind.UTILITY, includeDeleted));
		model.addAttribute("includeDeleted", includeDeleted);
		model.addAttribute("selectId", selectId);
		model.addAttribute("selectKind", selectKind);
		model.addAttribute("selectKey", selectKey);
		return "management/subprogram/list";
	}

	/* ─────────────────────────── 신규 등록 폼 ─────────────────────────── */

	@GetMapping("/new")
	public String newForm(
			@RequestParam(name = "kind") SubprogramKind kind,
			@RequestParam(name = "boardScope", required = false) String boardScopeToken,
			Model model
	) {
		model.addAttribute("kind", kind);
		model.addAttribute("kindToken", kind.pathToken());
		model.addAttribute("kindDisplayName", kind.getDisplayName());
		model.addAttribute("vendorGroups", boardModelService.findAllGrouped(false));
		model.addAttribute("subprogramForm", new SubprogramCreateRequest("", "", "", "", false));

		// Miller 에서 사전 선택된 boardScope 가 있으면 폼 라디오/select 초기값 주입.
		// boardScopeToken 가 "common" 이면 prefillScopeMode=common, 양의 정수면 prefillScopeMode=board.
		// 잘못된 토큰은 단순 무시 (사용자 navigation 보조라 도메인 흐름이 아님).
		String prefillScopeMode = "common";
		Long prefillBoardId = null;
		if (boardScopeToken != null && !boardScopeToken.isBlank()
				&& !"common".equalsIgnoreCase(boardScopeToken)
				&& boardScopeToken.chars().allMatch(Character::isDigit)) {
			prefillScopeMode = "board";
			prefillBoardId = Long.parseLong(boardScopeToken);
		}
		model.addAttribute("prefillScopeMode", prefillScopeMode);
		model.addAttribute("prefillBoardId", prefillBoardId);
		return "management/subprogram/subprogram-new";
	}

	/* ─────────────────────────── 편집 폼 ─────────────────────────── */

	@GetMapping("/{id:[0-9]+}/edit")
	public String editForm(@PathVariable("id") Long id, Model model) {
		SubprogramResponse sp = subprogramService.findSubprogram(id);
		model.addAttribute("subprogram", sp);
		model.addAttribute(
				"subprogramForm", new SubprogramUpdateRequest(
						sp.name(),
						sp.version(),
						SubprogramControllerSupport.nullToEmpty(sp.description()),
						SubprogramControllerSupport.nullToEmpty(sp.entrypointRelativePath())
				)
		);
		model.addAttribute("kind", sp.kind());
		model.addAttribute("kindDisplayName", sp.kind().getDisplayName());
		return "management/subprogram/subprogram-edit";
	}

	@PostMapping("/{id:[0-9]+}/edit")
	public String update(
			@PathVariable("id") Long id,
			@Valid @ModelAttribute("subprogramForm") SubprogramUpdateRequest request,
			BindingResult bindingResult,
			Model model
	) {
		if (bindingResult.hasErrors()) {
			SubprogramResponse sp = subprogramService.findSubprogram(id);
			model.addAttribute("subprogram", sp);
			model.addAttribute("kind", sp.kind());
			model.addAttribute("kindDisplayName", sp.kind().getDisplayName());
			return "management/subprogram/subprogram-edit";
		}
		subprogramService.update(id, request);
		return SubprogramControllerSupport.redirectToListWithSelect(id);
	}

	/* ─────────────────────────── 단순 액션 (redirect) ─────────────────────────── */

	@PostMapping("/{id:[0-9]+}/toggle")
	public String toggle(@PathVariable("id") Long id) {
		subprogramLifecycleService.toggleEnabled(id);
		return SubprogramControllerSupport.redirectToListWithSelect(id);
	}

	@PostMapping("/{id:[0-9]+}/delete")
	public String delete(@PathVariable("id") Long id) {
		subprogramLifecycleService.softDelete(id);
		return "redirect:/management/subprogram";
	}

	@PostMapping("/{id:[0-9]+}/restore")
	public String restore(@PathVariable("id") Long id) {
		subprogramLifecycleService.restore(id);
		return SubprogramControllerSupport.redirectToListWithSelect(id);
	}

	/**
	 * MK2 — Active → Deprecated 전이.
	 */
	@PostMapping("/{id:[0-9]+}/deprecate")
	public String deprecate(@PathVariable("id") Long id) {
		subprogramLifecycleService.deprecate(id);
		return SubprogramControllerSupport.redirectToListWithSelect(id);
	}

	/**
	 * MK2 — Deprecated → Active 전이.
	 */
	@PostMapping("/{id:[0-9]+}/undeprecate")
	public String undeprecate(@PathVariable("id") Long id) {
		subprogramLifecycleService.undeprecate(id);
		return SubprogramControllerSupport.redirectToListWithSelect(id);
	}

	/**
	 * MK2 — 영구 삭제. 본 액션은 SoftDeleted 상태 자원에 한해 허용된다 (Service 가드).
	 * 영구 삭제된 자원은 redirect 시 selectId 로 잡을 수 없으므로 단순 list 로 이동.
	 */
	@PostMapping("/{id:[0-9]+}/purge")
	public String purge(
			@PathVariable("id") Long id,
			@RequestParam("typedName") String typedName
	) {
		subprogramLifecycleService.purgeWithTypedNameCheck(id, typedName);
		return "redirect:/management/subprogram?includeDeleted=true";
	}

	/* ─────────────────────────── REST: 목록 / 상세 / 무결성 ─────────────────────────── */

	@GetMapping("/items")
	@ResponseBody
	public BoardWithSubprogramListResponse items(
			@RequestParam("kind") SubprogramKind kind,
			@RequestParam("boardScope") String boardScopeToken,
			@RequestParam(name = "includeDeleted", defaultValue = "false") boolean includeDeleted
	) {
		BoardScope scope = BoardScope.fromPathToken(boardScopeToken);
		return subprogramService.findByScope(kind, scope, includeDeleted);
	}

	@GetMapping("/{id:[0-9]+}")
	@ResponseBody
	public SubprogramResponse detail(@PathVariable("id") Long id) {
		return subprogramService.findSubprogram(id);
	}

	@GetMapping("/{id:[0-9]+}/integrity-status")
	@ResponseBody
	public IntegrityStatusResponse integrityStatus(@PathVariable("id") Long id) {
		return subprogramIntegrityService.findIntegrityStatus(id);
	}
}
