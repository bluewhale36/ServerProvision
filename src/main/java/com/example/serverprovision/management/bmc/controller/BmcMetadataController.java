package com.example.serverprovision.management.bmc.controller;

import com.example.serverprovision.management.bmc.dto.request.BmcUpdateRequest;
import com.example.serverprovision.management.bmc.dto.response.BmcResponse;
import com.example.serverprovision.management.bmc.service.BmcService;
import com.example.serverprovision.management.board.dto.response.BoardModelResponse;
import com.example.serverprovision.management.board.service.metadata.BoardModelMetadataService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;

/**
 * MA4 BMC 펌웨어 메타데이터 (목록 / 편집 폼 / 메타 수정) MVC 컨트롤러.
 *
 * <p>R5-1 분할 — 단일 {@code BmcController} 에서 조회 / 메타데이터 편집 책임을 분리.
 * lifecycle 은 {@link BmcLifecycleController}, 업로드는 {@link BmcUploadController},
 * nudge 는 {@link BmcNudgeController}, job 은 {@link BmcJobController}, browse 는
 * {@link BmcBrowseController} 가 각각 관할한다.</p>
 */
@Controller
@RequestMapping("/management/bmc")
@RequiredArgsConstructor
public class BmcMetadataController {

	private final BmcService bmcService;
	private final BoardModelMetadataService boardModelService;

	@GetMapping
	public String list(
			@RequestParam(name = "includeDeleted", defaultValue = "false") boolean includeDeleted,
			@RequestParam(name = "selectId", required = false) Long selectId,
			@RequestParam(name = "selectBoardId", required = false) Long selectBoardId,
			@RequestParam(name = "selectedBoardId", required = false) Long selectedBoardId,
			Model model
	) {
		Long initialBoardId = selectBoardId != null ? selectBoardId : selectedBoardId;
		model.addAttribute("boards", bmcService.findAllGrouped(includeDeleted));
		model.addAttribute("includeDeleted", includeDeleted);
		model.addAttribute("selectId", selectId);
		model.addAttribute("selectBoardId", initialBoardId);
		return "management/bmc/list";
	}

	@GetMapping("/{boardId}/bmc/{bmcId}/edit")
	public String editForm(
			@PathVariable("boardId") Long boardId,
			@PathVariable("bmcId") Long bmcId,
			Model model
	) {
		BmcResponse bmc = bmcService.findBmc(boardId, bmcId);
		BoardModelResponse board = boardModelService.findById(boardId);
		model.addAttribute(
				"bmcForm", new BmcUpdateRequest(
						bmc.name(),
						bmc.version(),
						BmcControllerSupport.nullToEmpty(bmc.description())
				)
		);
		model.addAttribute("treeRootPath", bmc.treeRootPath());
		model.addAttribute("entrypointRelativePath", bmc.entrypointRelativePath());
		BmcControllerSupport.populateFormContext(model, boardId, bmcId, board);
		return "management/bmc/bmc-edit";
	}

	@PostMapping("/{boardId}/bmc/{bmcId}/edit")
	public String update(
			@PathVariable("boardId") Long boardId,
			@PathVariable("bmcId") Long bmcId,
			@Valid @ModelAttribute("bmcForm") BmcUpdateRequest request,
			BindingResult bindingResult,
			Model model
	) {
		if (bindingResult.hasErrors()) {
			BmcResponse bmc = bmcService.findBmc(boardId, bmcId);
			BoardModelResponse board = boardModelService.findById(boardId);
			model.addAttribute("treeRootPath", bmc.treeRootPath());
			model.addAttribute("entrypointRelativePath", bmc.entrypointRelativePath());
			BmcControllerSupport.populateFormContext(model, boardId, bmcId, board);
			return "management/bmc/bmc-edit";
		}
		bmcService.update(boardId, bmcId, request);
		return BmcControllerSupport.redirectToListWithSelect(bmcId);
	}
}
