package com.example.serverprovision.management.board.controller;

import com.example.serverprovision.management.board.dto.request.BoardModelCreateRequest;
import com.example.serverprovision.management.board.dto.request.BoardModelUpdateRequest;
import com.example.serverprovision.management.board.dto.response.BoardModelCreateResponse;
import com.example.serverprovision.management.board.dto.response.BoardModelResponse;
import com.example.serverprovision.management.board.enums.Vendor;
import com.example.serverprovision.management.board.service.metadata.BoardModelMetadataService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * A2. 메인보드 모델 메타 자원의 CRUD 진입점 — 목록 / 신규 폼 / 생성 / 수정 폼 / 수정.
 *
 * <p>R3-2 — 단일 fat {@code BoardModelController} 를 책임군으로 분할 :
 * <ul>
 *   <li>메타 CRUD : 본 컨트롤러</li>
 *   <li>상태 전이 (toggle / delete / restore / purge / deprecate / undeprecate) : {@link BoardModelLifecycleController}</li>
 *   <li>nudge confirm (proceed / replace / cancel) : {@link BoardModelNudgeController}</li>
 * </ul>
 *
 * <p>본 컨트롤러의 의존성은 {@link BoardModelMetadataService} 단독 (R3-3 — Service metadata 분리).</p>
 *
 * <p>레이어 약속 :
 * <ul>
 *   <li>뷰에는 Request / Response 만 넘긴다 (엔티티 직접 노출 금지).</li>
 *   <li>성공 시 {@code /management/board?selectId=...} 로 리다이렉트해 Miller 초기 선택을 복원한다.</li>
 *   <li>검증 실패({@code BindingResult}) 는 같은 폼 뷰로 돌아가 Thymeleaf 의 field errors 를 렌더한다.</li>
 *   <li>도메인 예외({@code NotFound}, {@code Conflict}) 는 {@link com.example.serverprovision.global.exception.WebExceptionHandler}
 *       (HTML) / {@link com.example.serverprovision.global.exception.ApiExceptionHandler} (JSON) 가 Accept 헤더에 따라 처리한다.</li>
 * </ul>
 */
@Controller
@RequestMapping("/management/board")
@RequiredArgsConstructor
public class BoardModelMetadataController {

	private final BoardModelMetadataService boardModelService;

	// ==== 목록 ========================================================

	@GetMapping
	public String list(
			@RequestParam(name = "includeDeleted", defaultValue = "false") boolean includeDeleted,
			@RequestParam(name = "selectId", required = false) Long selectId,
			// S5-4 — C1 (Vendor) 선택 보존. '삭제된 항목 포함' 토글이나 새로고침 시 동일 vendor active.
			@RequestParam(name = "selectKey", required = false) String selectKey,
			Model model
	) {
		model.addAttribute("boardGroups", boardModelService.findAllGrouped(includeDeleted));
		model.addAttribute("includeDeleted", includeDeleted);
		model.addAttribute("selectId", selectId);
		model.addAttribute("selectKey", selectKey);
		return "management/board/list";
	}

	// ==== 신규 등록 ===================================================

	@GetMapping("/new")
	public String newForm(Model model) {
		model.addAttribute("boardModelForm", new BoardModelCreateRequest(null, "", ""));
		model.addAttribute("vendorOptions", List.of(Vendor.values()));
		return "management/board/new";
	}

	/**
	 * MK2 WAVE 1 — XHR JSON 응답으로 통일. 메타 충돌 시 409 + NudgeRequiredResponse 가 advice 매핑으로 회신.
	 */
	@PostMapping(produces = "application/json")
	@ResponseBody
	public ResponseEntity<?> create(
			@Valid @ModelAttribute("boardModelForm") BoardModelCreateRequest request,
			BindingResult bindingResult
	) {
		if (bindingResult.hasErrors()) {
			return ResponseEntity.badRequest().body(
					BoardControllerSupport.toValidationError(bindingResult));
		}
		Long id = boardModelService.create(request);
		return ResponseEntity.ok(new BoardModelCreateResponse(id, "/management/board?selectId=" + id));
	}

	// ==== 수정 ========================================================

	@GetMapping("/{id}/edit")
	public String editForm(@PathVariable Long id, Model model) {
		BoardModelResponse board = boardModelService.findById(id);
		model.addAttribute(
				"boardModelForm", new BoardModelUpdateRequest(
						board.modelName(),
						BoardControllerSupport.nullToEmpty(board.description())
				)
		);
		model.addAttribute("boardModelId", id);
		model.addAttribute("vendorLabel", board.vendor().getDisplayName());
		return "management/board/edit";
	}

	@PostMapping("/{id}/edit")
	public String update(
			@PathVariable Long id,
			@Valid @ModelAttribute("boardModelForm") BoardModelUpdateRequest request,
			BindingResult bindingResult,
			Model model
	) {
		if (bindingResult.hasErrors()) {
			BoardModelResponse board = boardModelService.findById(id);
			model.addAttribute("boardModelId", id);
			model.addAttribute("vendorLabel", board.vendor().getDisplayName());
			return "management/board/edit";
		}
		boardModelService.update(id, request);
		return BoardControllerSupport.redirectToListWithSelect(id);
	}
}
