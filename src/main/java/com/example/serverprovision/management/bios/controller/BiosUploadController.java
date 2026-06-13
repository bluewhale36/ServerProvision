package com.example.serverprovision.management.bios.controller;

import com.example.serverprovision.management.bios.dto.request.BiosCreateRequest;
import com.example.serverprovision.management.bios.dto.request.BiosRegisterExistingRequest;
import com.example.serverprovision.management.bios.dto.request.BiosUploadIntentRequest;
import com.example.serverprovision.management.bios.dto.response.BiosUploadIntentResponse;
import com.example.serverprovision.management.bios.dto.response.BiosUploadResponse;
import com.example.serverprovision.management.bios.enums.BiosUploadMode;
import com.example.serverprovision.management.bios.service.BiosService;
import com.example.serverprovision.management.bios.service.BiosUploadIntentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

/**
 * BIOS 번들 업로드 진입점 — {@code /upload-intent} → {@code /upload} 2단 핸드셰이크 + 기존 트리 claim.
 *
 * <p>R4-1 — fat {@code BiosController} 6분할 결과. 모든 엔드포인트는 JSON 응답.
 * Layer A (BindingResult) 검증 실패만 직접 응답 ({@link BiosControllerSupport#badRequestFromBinding})
 * 하고, 도메인 예외 (NotFound / Conflict / FieldBoundConflict / BiosNudgeRequired / Security) 는
 * ApiExceptionHandler 가 일괄 처리 — 컨트롤러 try/catch 없음.</p>
 */
@Controller
@RequestMapping("/management/bios")
@RequiredArgsConstructor
public class BiosUploadController {

	private final BiosUploadIntentService biosUploadIntentService;
	private final BiosService biosService;

	/**
	 * 업로드 Intent 핸드셰이크 — 번들 바이트 전송 이전 하드 검증 + 토큰 발급.
	 */
	@PostMapping(path = "/{boardId}/upload-intent")
	@ResponseBody
	public ResponseEntity<?> intent(
			@PathVariable("boardId") Long boardId,
			@Valid @RequestBody BiosUploadIntentRequest request,
			BindingResult bindingResult
	) {
		// MK2 — Layer A 검증 실패만 직접 응답. 도메인 예외 (NotFound / Conflict / FieldBoundConflict /
		//       BiosNudgeRequired / Security) 는 ApiExceptionHandler 가 일괄 처리 (try/catch 없음).
		if (bindingResult.hasErrors()) {
			return BiosControllerSupport.badRequestFromBinding(bindingResult);
		}
		BiosUploadIntentResponse body = biosUploadIntentService.issue(boardId, request);
		return ResponseEntity.ok(body);
	}

	/**
	 * 번들 업로드 본체. {@code uploadMode} 에 따라 {@code folderFiles[]} / {@code zipFile} / {@code singleFile}
	 * 중 정확히 하나만 실어 보낸다.
	 */
	@PostMapping(path = "/{boardId}/upload")
	@ResponseBody
	public ResponseEntity<?> uploadBundle(
			@PathVariable("boardId") Long boardId,
			@Valid @ModelAttribute BiosCreateRequest request,
			BindingResult bindingResult,
			@RequestParam("uploadMode") BiosUploadMode uploadMode,
			@RequestParam(value = "folderFiles", required = false) MultipartFile[] folderFiles,
			@RequestParam(value = "zipFile", required = false) MultipartFile zipFile,
			@RequestParam(value = "singleFile", required = false) MultipartFile singleFile,
			@RequestHeader(name = "X-Upload-Token", required = false) String uploadToken
	) {
		// MK2 — Layer A 검증 실패만 직접 응답. 도메인 예외는 advice 일괄 처리.
		if (bindingResult.hasErrors()) {
			return BiosControllerSupport.badRequestFromBinding(bindingResult);
		}
		biosUploadIntentService.consume(boardId, uploadToken);
		Long id = biosService.addBios(boardId, request, uploadMode, folderFiles, zipFile, singleFile);
		String redirect = "/management/bios?selectId=" + id;
		return ResponseEntity.ok(new BiosUploadResponse(id, redirect));
	}

	/**
	 * 기존 디렉토리 등록 — 업로드 없이 이미 콘텐츠가 차 있는 트리를 BIOS 자원으로 claim.
	 * 핸드셰이크 없이 단발 POST. 응답은 업로드와 동일한 {@link BiosUploadResponse}.
	 */
	@PostMapping(path = "/{boardId}/register-existing")
	@ResponseBody
	public ResponseEntity<?> registerExisting(
			@PathVariable("boardId") Long boardId,
			@Valid @RequestBody BiosRegisterExistingRequest request,
			BindingResult bindingResult
	) {
		if (bindingResult.hasErrors()) {
			return BiosControllerSupport.badRequestFromBinding(bindingResult);
		}
		Long id = biosService.registerExisting(boardId, request);
		String redirect = "/management/bios?selectId=" + id;
		return ResponseEntity.ok(new BiosUploadResponse(id, redirect));
	}
}
