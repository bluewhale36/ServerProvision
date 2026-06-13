package com.example.serverprovision.management.subprogram.controller;

import com.example.serverprovision.global.exception.ApiErrorResponse;
import com.example.serverprovision.global.job.dto.response.JobStartResponse;
import com.example.serverprovision.management.subprogram.dto.request.SubprogramCreateRequest;
import com.example.serverprovision.management.subprogram.dto.request.SubprogramRegisterExistingRequest;
import com.example.serverprovision.management.subprogram.dto.request.SubprogramUploadIntentRequest;
import com.example.serverprovision.management.subprogram.dto.response.SubprogramUploadIntentResponse;
import com.example.serverprovision.management.subprogram.dto.response.SubprogramUploadResponse;
import com.example.serverprovision.management.subprogram.enums.SubprogramKind;
import com.example.serverprovision.management.subprogram.enums.SubprogramUploadMode;
import com.example.serverprovision.management.subprogram.service.SubprogramService;
import com.example.serverprovision.management.subprogram.service.SubprogramUploadIntentService;
import com.example.serverprovision.management.subprogram.service.SubprogramVerificationLauncher;
import com.example.serverprovision.management.subprogram.vo.BoardScope;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

/**
 * MA5 Subprogram 업로드 진입점 — 업로드 2-phase (intent → upload) + 기존 디렉토리 claim + 무결성 검증 job
 * + soft-delete intent XHR.
 *
 * <p>R6-1 — fat {@code SubprogramController} 에서 업로드 기능군을 분리. 업로드 직후 검증 (verify) 흐름이
 * 인접하므로 단독 JobController 를 신설하지 않고 본 컨트롤러에 합류. soft-delete reject modal 의 2 차
 * 호출 (delete-intent) 도 업로드 의도 검증과 같은 의존 (DeleteIntentRegistry) 을 쓰므로 합류.</p>
 *
 * <p>도메인 예외 → HTTP 매핑은 {@code ApiExceptionHandler} / {@code WebExceptionHandler} 가 일괄 책임.</p>
 */
@Controller
@RequestMapping("/management/subprogram")
@RequiredArgsConstructor
public class SubprogramUploadController {

	private final SubprogramService subprogramService;
	private final SubprogramUploadIntentService subprogramUploadIntentService;
	private final SubprogramVerificationLauncher subprogramVerificationLauncher;
	private final com.example.serverprovision.global.lifecycle.DeleteIntentRegistry deleteIntentRegistry;

	@PostMapping("/{id:[0-9]+}/verify")
	@ResponseBody
	public JobStartResponse verify(@PathVariable("id") Long id) {
		String jobId = subprogramVerificationLauncher.startVerification(id);
		return new JobStartResponse(jobId);
	}

	/**
	 * MK3-2 (DCM3-2.3) — softDelete reject modal 의 두 번째 호출 (XHR JSON).
	 */
	@PostMapping(path = "/{id:[0-9]+}/delete-intent/{token}", produces = "application/json")
	@ResponseBody
	public ResponseEntity<Void> deleteWithIntent(
			@PathVariable("id") Long id,
			@PathVariable("token") String token,
			@Valid @RequestBody com.example.serverprovision.management.common.dto.request.DeleteIntentRequest request
	) {
		com.example.serverprovision.global.lifecycle.DeleteIntentToken parsed =
				com.example.serverprovision.global.lifecycle.DeleteIntentToken.parse(token);
		deleteIntentRegistry.consume(
				parsed,
				com.example.serverprovision.global.marker.ResourceType.SUBPROGRAM, id
		);
		subprogramService.softDeleteWithIntent(id, request.action());
		return ResponseEntity.noContent().build();
	}

	/* ─────────────────────────── REST: 업로드 intent / upload ─────────────────────────── */

	@PostMapping("/{kind}/{boardScope}/upload-intent")
	@ResponseBody
	public ResponseEntity<?> intent(
			@PathVariable("kind") String kindToken,
			@PathVariable("boardScope") String boardScopeToken,
			@Valid @RequestBody SubprogramUploadIntentRequest request,
			BindingResult bindingResult
	) {
		if (bindingResult.hasErrors()) {
			return ResponseEntity.badRequest().body(new ApiErrorResponse(SubprogramControllerSupport.firstFieldError(bindingResult)));
		}
		SubprogramKind kind = SubprogramKind.fromPathToken(kindToken);
		BoardScope scope = BoardScope.fromPathToken(boardScopeToken);
		SubprogramUploadIntentResponse body = subprogramUploadIntentService.issue(kind, scope, request);
		return ResponseEntity.ok(body);
	}

	@PostMapping("/{kind}/{boardScope}/upload")
	@ResponseBody
	public ResponseEntity<?> uploadBundle(
			@PathVariable("kind") String kindToken,
			@PathVariable("boardScope") String boardScopeToken,
			@Valid @ModelAttribute SubprogramCreateRequest request,
			BindingResult bindingResult,
			@RequestParam("uploadMode") SubprogramUploadMode uploadMode,
			@RequestParam(value = "folderFiles", required = false) MultipartFile[] folderFiles,
			@RequestParam(value = "zipFile", required = false) MultipartFile zipFile,
			@RequestParam(value = "singleFile", required = false) MultipartFile singleFile,
			@RequestHeader(name = "X-Upload-Token", required = false) String uploadToken
	) {
		if (bindingResult.hasErrors()) {
			return ResponseEntity.badRequest().body(new ApiErrorResponse(SubprogramControllerSupport.firstFieldError(bindingResult)));
		}
		SubprogramKind kind = SubprogramKind.fromPathToken(kindToken);
		BoardScope scope = BoardScope.fromPathToken(boardScopeToken);
		subprogramUploadIntentService.consume(kind, scope, uploadToken);
		Long id = subprogramService.addSubprogram(kind, scope, request, uploadMode, folderFiles, zipFile, singleFile);
		return ResponseEntity.ok(new SubprogramUploadResponse(id, "/management/subprogram?selectId=" + id));
	}

	/**
	 * 기존 디렉토리 등록 — 업로드 없이 이미 콘텐츠가 차 있는 트리를 Subprogram 자원으로 claim.
	 */
	@PostMapping("/{kind}/{boardScope}/register-existing")
	@ResponseBody
	public ResponseEntity<?> registerExisting(
			@PathVariable("kind") String kindToken,
			@PathVariable("boardScope") String boardScopeToken,
			@Valid @RequestBody SubprogramRegisterExistingRequest request,
			BindingResult bindingResult
	) {
		if (bindingResult.hasErrors()) {
			return ResponseEntity.badRequest().body(new ApiErrorResponse(SubprogramControllerSupport.firstFieldError(bindingResult)));
		}
		SubprogramKind kind = SubprogramKind.fromPathToken(kindToken);
		BoardScope scope = BoardScope.fromPathToken(boardScopeToken);
		Long id = subprogramService.registerExisting(kind, scope, request);
		return ResponseEntity.ok(new SubprogramUploadResponse(id, "/management/subprogram?selectId=" + id));
	}
}
