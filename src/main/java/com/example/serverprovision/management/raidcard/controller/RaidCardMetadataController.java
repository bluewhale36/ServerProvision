package com.example.serverprovision.management.raidcard.controller;

import com.example.serverprovision.management.raidcard.enums.RaidChipFamily;
import com.example.serverprovision.management.common.web.ControllerValidationSupport;
import com.example.serverprovision.management.raidcard.dto.request.RaidCardCreateRequest;
import com.example.serverprovision.management.raidcard.dto.request.RaidCardUpdateRequest;
import com.example.serverprovision.management.raidcard.dto.response.RaidCardCreateResponse;
import com.example.serverprovision.management.raidcard.dto.response.RaidCardResponse;
import com.example.serverprovision.management.raidcard.dto.response.RaidCardVendorGroupResponse;
import com.example.serverprovision.management.raidcard.enums.RaidCardVendor;
import com.example.serverprovision.management.raidcard.enums.RaidLevel;
import com.example.serverprovision.management.raidcard.service.RaidCardMetadataService;
import com.example.serverprovision.management.raidcard.service.RaidCardObservationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * MA7. RAID 카드 메타 자원의 CRUD 진입점 — 목록 / 신규 폼 / 생성 / 수정 폼 / 수정.
 *
 * <p>컨트롤러 3분할(BoardModel R3-2 선례) :
 * <ul>
 *   <li>메타 CRUD : 본 컨트롤러</li>
 *   <li>상태 전이 : {@link RaidCardLifecycleController}</li>
 *   <li>nudge confirm : {@link RaidCardNudgeController}</li>
 * </ul>
 *
 * <p>레이어 약속 — 뷰에는 Request / Response 만, 성공 시 {@code selectId} 로 Miller 선택 복원,
 * 검증 실패는 같은 폼 뷰 재렌더, 도메인 예외는 advice 가 Accept 헤더에 따라 처리.</p>
 */
@Controller
@RequestMapping("/management/raidcard")
@RequiredArgsConstructor
public class RaidCardMetadataController {

	private final RaidCardMetadataService raidCardService;
	private final RaidCardObservationService observationService;

	// ==== 목록 ========================================================

	@GetMapping
	public String list(
			@RequestParam(name = "includeDeleted", defaultValue = "false") boolean includeDeleted,
			@RequestParam(name = "selectId", required = false) Long selectId,
			@RequestParam(name = "selectKey", required = false) String selectKey,
			Model model
	) {
		List<RaidCardVendorGroupResponse> groups = raidCardService.findAllGrouped(includeDeleted);
		model.addAttribute("raidCardGroups", groups);
		// E3.5-5-b — 카드별 관측 요약(파생 · 비삭제 카드만). 배지 · 버튼 · tooltip 이 이 판정 하나를 본다.
		model.addAttribute("observationByCard", observationService.summariesByCard(groups));
		model.addAttribute("includeDeleted", includeDeleted);
		model.addAttribute("selectId", selectId);
		model.addAttribute("selectKey", selectKey);
		return "management/raidcard/list";
	}

	// ==== 신규 등록 ===================================================

	/**
	 * CP6 개정 — 목록 C2 하단 등록 버튼이 현재 제조사를 {@code ?vendor=} 로 넘기면 폼에 프리셀렉트한다
	 * (ISO 의 osId · BIOS/BMC 의 boardId 처럼 등록 진입점이 선택 컨텍스트를 담아 가는 기존 방식).
	 */
	@GetMapping("/new")
	public String newForm(
			@RequestParam(name = "vendor", required = false) RaidCardVendor vendor,
			Model model
	) {
		model.addAttribute("raidCardForm",
				new RaidCardCreateRequest(vendor, "", null, List.of(), 0, "", ""));
		model.addAttribute("vendorOptions", List.of(RaidCardVendor.values()));
		model.addAttribute("levelOptions", List.of(RaidLevel.values()));
		model.addAttribute("chipFamilyOptions", List.of(RaidChipFamily.values()));
		return "management/raidcard/new";
	}

	/**
	 * XHR JSON 응답 — 메타 충돌 시 409 + NudgeRequiredResponse 가 advice 매핑으로 회신 (MK2 선례).
	 */
	@PostMapping(produces = "application/json")
	@ResponseBody
	public ResponseEntity<?> create(
			@Valid @ModelAttribute("raidCardForm") RaidCardCreateRequest request,
			BindingResult bindingResult
	) {
		if (bindingResult.hasErrors()) {
			return ResponseEntity.badRequest().body(
					ControllerValidationSupport.toValidationError(bindingResult));
		}
		Long id = raidCardService.create(request);
		return ResponseEntity.ok(new RaidCardCreateResponse(id, "/management/raidcard?selectId=" + id));
	}

	// ==== 수정 ========================================================

	@GetMapping("/{id}/edit")
	public String editForm(@PathVariable Long id, Model model) {
		RaidCardResponse card = raidCardService.findById(id);
		model.addAttribute(
				"raidCardForm", new RaidCardUpdateRequest(
						card.modelName(),
						card.chipFamily(),
						card.supportedRaidLevels(),
						card.cacheCapacityGb(),
						RaidCardControllerSupport.nullToEmpty(card.pciSubsystemIdDisplay()),
						RaidCardControllerSupport.nullToEmpty(card.description())
				)
		);
		populateEditView(model, id, card);
		return "management/raidcard/edit";
	}

	@PostMapping("/{id}/edit")
	public String update(
			@PathVariable Long id,
			@Valid @ModelAttribute("raidCardForm") RaidCardUpdateRequest request,
			BindingResult bindingResult,
			Model model
	) {
		if (bindingResult.hasErrors()) {
			populateEditView(model, id, raidCardService.findById(id));
			return "management/raidcard/edit";
		}
		raidCardService.update(id, request);
		return RaidCardControllerSupport.redirectToListWithSelect(id);
	}

	// ==== 관측값으로 확정 (E3.5-5-b) ====================================

	/**
	 * 미확인 카드의 Subsystem 을 게스트 관측값으로 채운다. BindingResult 없는 상태 변경 폼 — 전역 가로채기가
	 * 보내고 거절(409)은 안내 모달로 수렴한다. 화면이 같은 판정으로 버튼을 숨기거나 잠그므로 409 는 direct POST 몫이다.
	 */
	@PostMapping("/{id}/confirm-observed")
	public String confirmObserved(@PathVariable Long id) {
		observationService.confirmObserved(id);
		return RaidCardControllerSupport.redirectToListWithSelect(id);
	}

	/** 수정 폼 최초 진입과 검증 실패 재렌더가 같은 보조 모델을 쓴다 — 두 곳 복붙 방지. */
	private void populateEditView(Model model, Long id, RaidCardResponse card) {
		model.addAttribute("raidCardId", id);
		model.addAttribute("vendorLabel", card.vendor().getDisplayName());
		model.addAttribute("levelOptions", List.of(RaidLevel.values()));
		model.addAttribute("chipFamilyOptions", List.of(RaidChipFamily.values()));
	}
}
