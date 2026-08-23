package com.example.serverprovision.management.bmc.controller;

import com.example.serverprovision.global.exception.ApiErrorResponse;
import com.example.serverprovision.management.bmc.dto.request.BmcCreateRequest;
import com.example.serverprovision.management.bmc.dto.request.BmcUploadIntentRequest;
import com.example.serverprovision.management.bmc.dto.response.BmcUploadIntentResponse;
import com.example.serverprovision.management.bmc.dto.response.BmcUploadResponse;
import com.example.serverprovision.management.bmc.service.BmcFirmwareFilePolicy;
import com.example.serverprovision.management.bmc.service.BmcRegistrationService;
import com.example.serverprovision.management.bmc.service.BmcUploadIntentService;
import com.example.serverprovision.management.board.dto.response.BoardModelResponse;
import com.example.serverprovision.management.board.service.metadata.BoardModelMetadataService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

/**
 * MA4 BMC 펌웨어 등록 (신규 폼 / upload-intent / 등록 본체) MVC 컨트롤러.
 *
 * <p>R12-2 — 번들(폴더 · zip) 업로드와 별도 기존 디렉토리 등록({@code /register-existing})을 폐지하고
 * BIOS 와 동일한 단일 흐름으로 통합했다. {@code /upload} 의 {@code firmwareFile} 은 선택이다 —
 * 파일이 있으면 intent 토큰을 소비하고 해석된 경로에 저장하며, 없으면 토큰 없이 그 경로의
 * 기존 파일을 자원으로 등록(claim)한다.</p>
 *
 * <p>R12-2 — R5-1 부터 이월돼 있던 {@code uploadBundle} 의 multi-catch(NotFound · FieldBoundConflict ·
 * Conflict · Domain 4단)를 걷어내고 도메인 예외를 {@code ApiExceptionHandler} 로 넘겼다. 새 예외가
 * 추가되는 슬라이스에서 분기를 한 줄 더 늘리는 것은 "조건 분기 확장 금지" 원칙에 어긋나며,
 * BIOS 는 이미 같은 정리를 마쳤다. Layer A(BindingResult) 검증 실패만 직접 응답한다.</p>
 */
@Controller
@RequestMapping("/management/bmc")
@RequiredArgsConstructor
public class BmcUploadController {

	private final BmcRegistrationService bmcRegistrationService;
	private final BmcUploadIntentService bmcUploadIntentService;
	private final BmcFirmwareFilePolicy bmcFirmwareFilePolicy;
	private final BoardModelMetadataService boardModelService;

	@GetMapping("/{boardId}/new")
	public String newForm(
			@PathVariable("boardId") Long boardId,
			// S5-5 — AJAX (XMLHttpRequest) 진입 시 formCard fragment 반환.
			@RequestHeader(value = "X-Requested-With", required = false) String requestedWith,
			Model model
	) {
		BoardModelResponse board = boardModelService.findById(boardId);
		model.addAttribute("bmcForm", new BmcCreateRequest("", "", "", "", false));
		// R12-2 — vendor 별 파일 정책(허용 확장자 · 금지 파일명) SSOT 를 data 속성으로 내려
		//         JavaScript 사전 검사 · accept 속성이 같은 데이터를 쓴다.
		model.addAttribute("firmwareForbiddenNames", bmcFirmwareFilePolicy.forbiddenNamesCsv(board.vendor()));
		model.addAttribute("firmwareForbiddenMessage", bmcFirmwareFilePolicy.forbiddenMessage(board.vendor()));
		model.addAttribute("firmwareAllowedExtensions", bmcFirmwareFilePolicy.allowedExtensionsCsv(board.vendor()));
		model.addAttribute("firmwareAcceptAttribute", bmcFirmwareFilePolicy.acceptAttribute(board.vendor()));
		model.addAttribute("firmwareInvalidExtensionMessage", bmcFirmwareFilePolicy.invalidExtensionMessage(board.vendor()));
		BmcControllerSupport.populateFormContext(model, boardId, null, board);
		boolean ajax = "XMLHttpRequest".equalsIgnoreCase(requestedWith);
		return ajax ? "management/bmc/bmc-new :: formCard" : "management/bmc/bmc-new";
	}

	/**
	 * S5-5 — 외부 우상단 "+ 신규 BMC 등록" 진입점. boardId 미지정 진입에서는
	 * 메인보드 모델 선택 단계를 먼저 보여주고, 선택 시 {@code /{boardId}/new} 로 redirect 한다.
	 */
	@GetMapping("/new")
	public String newFormWithoutBoard(Model model) {
		// R12-2 — 폼 카드는 board 선택 후 AJAX fragment 로 주입되므로 vendor 별 정책 속성은 그 시점에 내려간다.
		model.addAttribute("bmcForm", new BmcCreateRequest("", "", "", "", false));
		model.addAttribute("boardId", null);
		model.addAttribute("contextLabel", null);
		model.addAttribute("vendorGroups", boardModelService.findAllGrouped(false));
		return "management/bmc/bmc-new";
	}

	/**
	 * 업로드 Intent 핸드셰이크 — 파일 바이트 전송 이전 하드 검증(파일명 정책 포함) + 토큰 발급.
	 * 업로드 파일이 없는 등록(claim)은 이 핸드셰이크를 거치지 않는다.
	 */
	@PostMapping(path = "/{boardId}/upload-intent")
	@ResponseBody
	public ResponseEntity<?> intent(
			@PathVariable("boardId") Long boardId,
			@Valid @RequestBody BmcUploadIntentRequest request,
			BindingResult bindingResult
	) {
		// MK2 WAVE 2 — Layer A 검증 실패만 직접 응답. 도메인 예외 (NotFound / Conflict / FieldBoundConflict /
		//       BmcNudgeRequired / Security) 는 ApiExceptionHandler 가 일괄 처리 (try/catch 제거 — S3-4 정합).
		if (bindingResult.hasErrors()) {
			return ResponseEntity.badRequest().body(new ApiErrorResponse(BmcControllerSupport.firstError(bindingResult)));
		}
		BmcUploadIntentResponse body = bmcUploadIntentService.issue(boardId, request);
		return ResponseEntity.ok(body);
	}

	/**
	 * 등록 본체. {@code firmwareFile} 이 있으면 업로드 저장(토큰 소비), 없으면 기존 파일 claim(토큰 불요).
	 */
	@PostMapping(path = "/{boardId}/upload")
	@ResponseBody
	public ResponseEntity<?> register(
			@PathVariable("boardId") Long boardId,
			@Valid @ModelAttribute BmcCreateRequest request,
			BindingResult bindingResult,
			@RequestParam(value = "firmwareFile", required = false) MultipartFile firmwareFile,
			@RequestHeader(name = "X-Upload-Token", required = false) String uploadToken
	) {
		if (bindingResult.hasErrors()) {
			return ResponseEntity.badRequest().body(new ApiErrorResponse(BmcControllerSupport.firstError(bindingResult)));
		}
		boolean hasFile = firmwareFile != null && !firmwareFile.isEmpty();
		if (hasFile) {
			bmcUploadIntentService.consume(boardId, uploadToken);
		}
		Long id = bmcRegistrationService.addBmc(boardId, request, firmwareFile);
		return ResponseEntity.ok(new BmcUploadResponse(id, "/management/bmc?selectId=" + id));
	}
}
