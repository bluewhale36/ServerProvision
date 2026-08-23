package com.example.serverprovision.management.bios.controller;

import com.example.serverprovision.management.bios.dto.request.BiosCreateRequest;
import com.example.serverprovision.management.bios.dto.request.BiosUpdateRequest;
import com.example.serverprovision.management.bios.dto.response.BiosResponse;
import com.example.serverprovision.management.bios.service.BiosFirmwareFilePolicy;
import com.example.serverprovision.management.bios.service.BiosService;
import com.example.serverprovision.management.board.dto.response.BoardModelResponse;
import com.example.serverprovision.management.board.service.metadata.BoardModelMetadataService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.PatchMapping;
import com.example.serverprovision.management.bios.dto.request.BiosRankRequest;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;

/**
 * BIOS 번들 메타데이터 (Miller 목록 / 폼 / CRUD) 진입점.
 *
 * <p>R4-1 — fat {@code BiosController} 를 기능군별 6 컨트롤러로 분리한 결과.
 * 본 컨트롤러는 Miller 목록 + 신규/수정 폼 + 메타 수정 submit 을 담당하며,
 * lifecycle / upload / nudge / integrity / browse 는 별도 컨트롤러로 분리됐다.</p>
 */
@Controller
@RequestMapping("/management/bios")
@RequiredArgsConstructor
public class BiosMetadataController {

	private final BiosService biosService;
	private final BoardModelMetadataService boardModelService;
	private final BiosFirmwareFilePolicy biosFirmwareFilePolicy;

	// ==== 목록 ========================================================

	@GetMapping
	public String list(
			@RequestParam(name = "includeDeleted", defaultValue = "false") boolean includeDeleted,
			@RequestParam(name = "selectId", required = false) Long selectId,
			@RequestParam(name = "selectBoardId", required = false) Long selectBoardId,
			@RequestParam(name = "selectedBoardId", required = false) Long selectedBoardId,
			Model model
	) {
		Long initialBoardId = selectBoardId != null ? selectBoardId : selectedBoardId;
		model.addAttribute("boards", biosService.findAllGrouped(includeDeleted));
		model.addAttribute("includeDeleted", includeDeleted);
		model.addAttribute("selectId", selectId);
		model.addAttribute("selectBoardId", initialBoardId);
		return "management/bios/list";
	}

	// ==== 신규 번들 등록 ===============================================

	@GetMapping("/{boardId}/new")
	public String newForm(
			@PathVariable("boardId") Long boardId,
			// S5-5 — 외부 진입 (/new) 의 board select 가 AJAX 로 본 endpoint 를 호출.
			// X-Requested-With=XMLHttpRequest 헤더 시 fragment 만 반환, 직접 진입 시 풀 페이지.
			@RequestHeader(value = "X-Requested-With", required = false) String requestedWith,
			Model model
	) {
		BoardModelResponse board = boardModelService.findById(boardId);
		model.addAttribute("biosForm", new BiosCreateRequest("", "", "", "", false));
		// R12-1 — vendor 별 파일 정책(금지 파일명 · 허용 확장자) SSOT 를 data 속성으로 내려
		//         JavaScript 사전 검사 · accept 속성이 같은 데이터를 쓴다.
		model.addAttribute("firmwareForbiddenNames", biosFirmwareFilePolicy.forbiddenNamesCsv(board.vendor()));
		model.addAttribute("firmwareForbiddenMessage", biosFirmwareFilePolicy.forbiddenMessage(board.vendor()));
		model.addAttribute("firmwareAllowedExtensions", biosFirmwareFilePolicy.allowedExtensionsCsv(board.vendor()));
		model.addAttribute("firmwareAcceptAttribute", biosFirmwareFilePolicy.acceptAttribute(board.vendor()));
		model.addAttribute("firmwareInvalidExtensionMessage", biosFirmwareFilePolicy.invalidExtensionMessage(board.vendor()));
		BiosControllerSupport.populateFormContext(model, boardId, null, board);
		boolean ajax = "XMLHttpRequest".equalsIgnoreCase(requestedWith);
		return ajax ? "management/bios/bios-new :: formCard" : "management/bios/bios-new";
	}

	/**
	 * S5-5 — 외부 우상단 "+ 신규 BIOS 등록" 진입점. boardId 미지정 진입에서는
	 * 메인보드 모델 선택 단계를 먼저 보여주고, 선택 시 {@code /{boardId}/new} 로 redirect 한다.
	 * 기존 {@link #newForm(Long, String, Model)} 와는 별개의 진입 경로.
	 */
	@GetMapping("/new")
	public String newFormWithoutBoard(Model model) {
		// R12-1 — 폼 카드는 board 선택 후 AJAX fragment 로 주입되므로 vendor 별 정책 속성은 그 시점에 내려간다.
		model.addAttribute("biosForm", new BiosCreateRequest("", "", "", "", false));
		model.addAttribute("boardId", null);
		model.addAttribute("contextLabel", null);
		model.addAttribute("vendorGroups", boardModelService.findAllGrouped(false));
		return "management/bios/bios-new";
	}

	// ==== 메타 수정 ===================================================

	@GetMapping("/{boardId}/bios/{biosId}/edit")
	public String editForm(
			@PathVariable("boardId") Long boardId,
			@PathVariable("biosId") Long biosId,
			Model model
	) {
		BiosResponse bios = biosService.findBios(boardId, biosId);
		BoardModelResponse board = boardModelService.findById(boardId);
		model.addAttribute(
				"biosForm", new BiosUpdateRequest(
						bios.name(),
						bios.version(),
						BiosControllerSupport.nullToEmpty(bios.description())
				)
		);
		model.addAttribute("treeRootPath", bios.treeRootPath());
		model.addAttribute("entrypointRelativePath", bios.entrypointRelativePath());
		BiosControllerSupport.populateFormContext(model, boardId, biosId, board);
		return "management/bios/bios-edit";
	}

	@PostMapping("/{boardId}/bios/{biosId}/edit")
	public String update(
			@PathVariable("boardId") Long boardId,
			@PathVariable("biosId") Long biosId,
			@Valid @ModelAttribute("biosForm") BiosUpdateRequest request,
			BindingResult bindingResult,
			Model model
	) {
		if (bindingResult.hasErrors()) {
			BiosResponse bios = biosService.findBios(boardId, biosId);
			BoardModelResponse board = boardModelService.findById(boardId);
			model.addAttribute("treeRootPath", bios.treeRootPath());
			model.addAttribute("entrypointRelativePath", bios.entrypointRelativePath());
			BiosControllerSupport.populateFormContext(model, boardId, biosId, board);
			return "management/bios/bios-edit";
		}
		biosService.update(boardId, biosId, request);
		return BiosControllerSupport.redirectToListWithSelect(biosId);
	}

	/**
	 * 버전 순서 재정렬(E2-1-a) — 목록 드래그의 저장 XHR. 순수 JSON 계약이라 전역 폼 인터셉터 밖이며
	 * (new-form.md — 페이지 전용 JS 처리), 성공은 204 로 조용히 끝난다(순서가 이미 화면에 보인다).
	 */
	@PatchMapping(path = "/{boardId}/rank", consumes = org.springframework.http.MediaType.APPLICATION_JSON_VALUE)
	@ResponseBody
	public org.springframework.http.ResponseEntity<Void> reorderRanks(
			@PathVariable Long boardId,
			@RequestBody @jakarta.validation.Valid BiosRankRequest request
	) {
		biosService.reorderVersionRanks(boardId, request.orderedIds());
		return org.springframework.http.ResponseEntity.noContent().build();
	}
}
