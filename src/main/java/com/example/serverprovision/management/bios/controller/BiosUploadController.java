package com.example.serverprovision.management.bios.controller;

import com.example.serverprovision.management.bios.dto.request.BiosCreateRequest;
import com.example.serverprovision.management.bios.dto.request.BiosUploadIntentRequest;
import com.example.serverprovision.management.bios.dto.response.BiosUploadIntentResponse;
import com.example.serverprovision.management.bios.dto.response.BiosUploadResponse;
import com.example.serverprovision.management.bios.service.BiosRegistrationService;
import com.example.serverprovision.management.bios.service.BiosUploadIntentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

/**
 * BIOS 펌웨어 등록 진입점 — {@code /upload-intent} → {@code /upload} 2단 핸드셰이크.
 *
 * <p>R12-1 — 번들(폴더 · zip) 업로드와 별도 기존 디렉토리 등록({@code /register-existing})을 폐지하고
 * ISO 와 동일한 단일 흐름으로 통합했다. {@code /upload} 의 {@code firmwareFile} 은 선택이다 —
 * 파일이 있으면 intent 토큰을 소비하고 해석된 경로에 저장하며, 없으면 토큰 없이 그 경로의
 * 기존 파일을 자원으로 등록(claim)한다.</p>
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
	private final BiosRegistrationService biosRegistrationService;

	/**
	 * 업로드 Intent 핸드셰이크 — 파일 바이트 전송 이전 하드 검증(금지 파일명 포함) + 토큰 발급.
	 * 업로드 파일이 없는 등록(claim)은 이 핸드셰이크를 거치지 않는다.
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
	 * 등록 본체. {@code firmwareFile} 이 있으면 업로드 저장(토큰 소비), 없으면 기존 파일 claim(토큰 불요).
	 */
	@PostMapping(path = "/{boardId}/upload")
	@ResponseBody
	public ResponseEntity<?> register(
			@PathVariable("boardId") Long boardId,
			@Valid @ModelAttribute BiosCreateRequest request,
			BindingResult bindingResult,
			@RequestParam(value = "firmwareFile", required = false) MultipartFile firmwareFile,
			@RequestHeader(name = "X-Upload-Token", required = false) String uploadToken
	) {
		// MK2 — Layer A 검증 실패만 직접 응답. 도메인 예외는 advice 일괄 처리.
		if (bindingResult.hasErrors()) {
			return BiosControllerSupport.badRequestFromBinding(bindingResult);
		}
		boolean hasFile = firmwareFile != null && !firmwareFile.isEmpty();
		if (hasFile) {
			biosUploadIntentService.consume(boardId, uploadToken);
		}
		Long id = biosRegistrationService.addBios(boardId, request, firmwareFile);
		String redirect = "/management/bios?selectId=" + id;
		return ResponseEntity.ok(new BiosUploadResponse(id, redirect));
	}
}
