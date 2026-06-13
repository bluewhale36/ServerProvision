package com.example.serverprovision.management.bmc.controller;

import com.example.serverprovision.global.exception.*;
import com.example.serverprovision.management.bmc.dto.request.BmcCreateRequest;
import com.example.serverprovision.management.bmc.dto.request.BmcRegisterExistingRequest;
import com.example.serverprovision.management.bmc.dto.request.BmcUploadIntentRequest;
import com.example.serverprovision.management.bmc.dto.response.BmcUploadIntentResponse;
import com.example.serverprovision.management.bmc.dto.response.BmcUploadResponse;
import com.example.serverprovision.management.bmc.enums.BmcUploadMode;
import com.example.serverprovision.management.bmc.service.BmcService;
import com.example.serverprovision.management.bmc.service.BmcUploadIntentService;
import com.example.serverprovision.management.board.dto.response.BoardModelResponse;
import com.example.serverprovision.management.board.service.metadata.BoardModelMetadataService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

/**
 * MA4 BMC 펌웨어 등록 (신규 폼 / upload-intent / 번들 업로드 / 기존 디렉토리 claim) MVC 컨트롤러.
 *
 * <p>R5-1 분할 — 단일 {@code BmcController} 에서 업로드 책임을 분리.
 * 의존성: {@link BmcService}, {@link BmcUploadIntentService}, {@link BoardModelMetadataService}.</p>
 *
 * <p>{@code uploadBundle} 의 multi-catch 는 R5-1 시점엔 컨트롤러 로컬로 유지(advice 승급은
 * 후속 슬라이스 이월). 동작 보존 우선.</p>
 */
@Controller
@RequestMapping("/management/bmc")
@RequiredArgsConstructor
public class BmcUploadController {

	private final BmcService bmcService;
	private final BmcUploadIntentService bmcUploadIntentService;
	private final BoardModelMetadataService boardModelService;

	@GetMapping("/{boardId}/new")
	public String newForm(
			@PathVariable("boardId") Long boardId,
			// S5-5 — AJAX (XMLHttpRequest) 진입 시 formCard fragment 반환.
			@RequestHeader(value = "X-Requested-With", required = false) String requestedWith,
			Model model
	) {
		BoardModelResponse board = boardModelService.findById(boardId);
		model.addAttribute("bmcForm", new BmcCreateRequest("", "", "", "", false, ""));
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
		model.addAttribute("bmcForm", new BmcCreateRequest("", "", "", "", false, ""));
		model.addAttribute("boardId", null);
		model.addAttribute("contextLabel", null);
		model.addAttribute("vendorGroups", boardModelService.findAllGrouped(false));
		return "management/bmc/bmc-new";
	}

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

	@PostMapping(path = "/{boardId}/upload")
	@ResponseBody
	public ResponseEntity<?> uploadBundle(
			@PathVariable("boardId") Long boardId,
			@Valid @ModelAttribute BmcCreateRequest request,
			BindingResult bindingResult,
			@RequestParam("uploadMode") BmcUploadMode uploadMode,
			@RequestParam(value = "folderFiles", required = false) MultipartFile[] folderFiles,
			@RequestParam(value = "zipFile", required = false) MultipartFile zipFile,
			@RequestParam(value = "singleFile", required = false) MultipartFile singleFile,
			@RequestHeader(name = "X-Upload-Token", required = false) String uploadToken
	) {
		if (bindingResult.hasErrors()) {
			return ResponseEntity.badRequest().body(new ApiErrorResponse(BmcControllerSupport.firstError(bindingResult)));
		}
		try {
			bmcUploadIntentService.consume(boardId, uploadToken);
			Long id = bmcService.addBmc(boardId, request, uploadMode, folderFiles, zipFile, singleFile);
			return ResponseEntity.ok(new BmcUploadResponse(id, "/management/bmc?selectId=" + id));
		} catch (NotFoundException e) {
			return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ApiErrorResponse(e.getMessage()));
		} catch (FieldBoundConflictException e) {
			// S4 — 필드 직결 충돌은 fieldErrors 동봉.
			return ResponseEntity.status(HttpStatus.CONFLICT)
					.body(ApiErrorResponse.ofFieldBound(e.getMessage(), e.fieldName()));
		} catch (ConflictException e) {
			return ResponseEntity.status(HttpStatus.CONFLICT).body(new ApiErrorResponse(e.getMessage()));
		} catch (DomainException e) {
			// B3 — 보안 예외는 SecurityException 계층으로 분리되어 본 catch 에 흡수되지 않고 통과한다.
			return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(new ApiErrorResponse(e.getMessage()));
		}
	}

	/**
	 * 기존 디렉토리 등록 — 업로드 없이 이미 콘텐츠가 차 있는 트리를 BMC 자원으로 claim.
	 */
	@PostMapping(path = "/{boardId}/register-existing")
	@ResponseBody
	public ResponseEntity<?> registerExisting(
			@PathVariable("boardId") Long boardId,
			@Valid @RequestBody BmcRegisterExistingRequest request,
			BindingResult bindingResult
	) {
		if (bindingResult.hasErrors()) {
			return ResponseEntity.badRequest().body(new ApiErrorResponse(BmcControllerSupport.firstError(bindingResult)));
		}
		Long id = bmcService.registerExisting(boardId, request);
		return ResponseEntity.ok(new BmcUploadResponse(id, "/management/bmc?selectId=" + id));
	}
}
