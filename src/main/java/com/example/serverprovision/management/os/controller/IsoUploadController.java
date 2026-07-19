package com.example.serverprovision.management.os.controller;

import com.example.serverprovision.global.exception.ApiErrorResponse;
import com.example.serverprovision.global.exception.FieldBoundBadRequestException;
import com.example.serverprovision.management.os.dto.request.ISOCreateRequest;
import com.example.serverprovision.management.os.dto.request.IsoUploadIntentRequest;
import com.example.serverprovision.management.os.dto.response.IsoUploadIntentResponse;
import com.example.serverprovision.management.os.dto.response.IsoUploadResponse;
import com.example.serverprovision.management.os.dto.response.OSMetadataResponse;
import com.example.serverprovision.management.os.service.iso.IsoRegistrationLauncher;
import com.example.serverprovision.management.os.service.iso.IsoRegistrationService;
import com.example.serverprovision.management.os.service.iso.IsoUploadIntentService;
import com.example.serverprovision.management.os.service.metadata.OSMetadataService;
import jakarta.servlet.http.HttpServletResponse;
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
 * ISO 등록 흐름 — 폼 진입(GET) · SSR 제출(POST) · XHR Intent 핸드셰이크 · XHR 실제 업로드 4 진입점.
 *
 * <p>호출 흐름이 모두 "등록 launcher (background job) 로 위임" 이라는 동일 패턴을 공유하므로
 * 한 컨트롤러에 묶었다. lifecycle (수정/삭제/복구/deprecate 등) 흐름은
 * {@link IsoLifecycleController} 가 별도로 관할한다.</p>
 *
 * <p>의존성: {@link IsoRegistrationService} (등록 prepare, R1-4-2 분리), {@link OSMetadataService}
 * (폼 컨텍스트용 부모 조회), {@link IsoUploadIntentService} (intent token 발급/consume),
 * {@link IsoRegistrationLauncher} (job 등록).</p>
 */
@Controller
@RequestMapping("/management/os")
@RequiredArgsConstructor
public class IsoUploadController {

	private final OSMetadataService osMetadataService;
	private final IsoRegistrationService isoRegistrationService;
	private final IsoUploadIntentService isoUploadIntentService;
	private final IsoRegistrationLauncher isoRegistrationLauncher;

	@GetMapping("/{osId}/iso/new")
	public String newIsoForm(@PathVariable("osId") Long osId, Model model) {
		OSMetadataResponse os = osMetadataService.findById(osId);
		model.addAttribute("isoForm", new ISOCreateRequest("", "", false));
		OSControllerSupport.populateIsoFormContext(model, osId, null, os);
		return "management/os/iso-new";
	}

	@PostMapping("/{osId}/iso")
	public String addIso(
			@PathVariable("osId") Long osId,
			@Valid @ModelAttribute("isoForm") ISOCreateRequest request,
			BindingResult bindingResult,
			@RequestParam(value = "file", required = false) MultipartFile file,
			Model model,
			HttpServletResponse response
	) {
		if (bindingResult.hasErrors()) {
			return renderIsoNewForm(osId, model);
		}
		IsoRegistrationService.PreparedIsoRegistration prepared;
		try {
			prepared = isoRegistrationService.prepare(osId, request, file);
		} catch (FieldBoundBadRequestException ex) {
			// HF4-3 (F-4) — 필드 직결 400(디렉토리 경로 등)을 폼 재렌더로 표면화 (updateIso 의 rejectValue 선례).
			// 예외가 자기 fieldName 을 들고 오는 다형 매핑이라 sub-class 별 catch 분기가 자라지 않는다.
			bindingResult.rejectValue(ex.fieldName(), "fieldBound", ex.getMessage());
			response.setStatus(HttpStatus.BAD_REQUEST.value());
			return renderIsoNewForm(osId, model);
		}
		isoRegistrationLauncher.startRegistration(prepared);
		return OSControllerSupport.redirectToListWithSelect(osId);
	}

	/**
	 * iso-new 폼 재렌더 공통 조립 — Layer A 검증 실패와 필드 직결 도메인 예외(rejectValue 흡수) 두 경로가 공유.
	 */
	private String renderIsoNewForm(Long osId, Model model) {
		OSMetadataResponse os = osMetadataService.findById(osId);
		OSControllerSupport.populateIsoFormContext(model, osId, null, os);
		return "management/os/iso-new";
	}

	/**
	 * 업로드 Intent 핸드셰이크 — 실제 XHR 전송 전 사전 검증.
	 * 하드 조건 (동일 isoPath 의 활성 ISO 가 이미 있음) 에 걸리면 409 로 즉시 거절되어
	 * 바이트 전송 자체가 일어나지 않는다. 성공 시 응답의 {@code uploadToken} 을
	 * 실제 업로드 요청의 {@code X-Upload-Token} 헤더에 실어야 한다.
	 * <p>모든 예외 분기는 JSON {@link ApiErrorResponse} 로 감싸서 반환한다 — 전역 핸들러의
	 * HTML 에러 뷰 경로로 빠지면 클라이언트가 실제 사유 메시지를 파싱하지 못해
	 * "사전 검증 실패" 같은 모호한 fallback 만 보이게 된다.</p>
	 */
	@PostMapping(path = "/{osId}/iso/upload-intent")
	@ResponseBody
	public ResponseEntity<?> intent(
			@PathVariable("osId") Long osId,
			@Valid @RequestBody IsoUploadIntentRequest request,
			BindingResult bindingResult
	) {
		if (bindingResult.hasErrors()) {
			String msg = bindingResult.getFieldErrors().stream()
					.findFirst()
					.map(err -> err.getField() + ": " + err.getDefaultMessage())
					.orElse("입력값이 올바르지 않습니다.");
			return ResponseEntity.badRequest().body(new ApiErrorResponse(msg));
		}
		IsoUploadIntentResponse body = isoUploadIntentService.issue(osId, request);
		return ResponseEntity.ok(body);
	}

	/**
	 * JS XHR 업로드 전용 REST 엔드포인트. 클라이언트가 foreground XHR 로 바이트를 먼저 올리고,
	 * 이후 무거운 등록 후처리는 background job 으로 넘긴다.
	 * <ul>
	 *   <li>파일 업로드가 있는 경우에만 {@code X-Upload-Token} 헤더의 intent 토큰을 1회용으로 소비한다.</li>
	 *   <li>요청 스레드는 파일 저장/경로 검증까지만 수행하고, SHA-256 계산 · marker 발급 · DB 저장은
	 *       {@link IsoRegistrationLauncher} 의 background job 으로 이어진다.</li>
	 * </ul>
	 */
	@PostMapping(path = "/{osId}/iso/upload")
	@ResponseBody
	public ResponseEntity<?> uploadIso(
			@PathVariable("osId") Long osId,
			@Valid @ModelAttribute ISOCreateRequest request,
			BindingResult bindingResult,
			@RequestParam(value = "file", required = false) MultipartFile file,
			@RequestHeader(name = "X-Upload-Token", required = false) String uploadToken
	) {
		if (bindingResult.hasErrors()) {
			String msg = bindingResult.getFieldErrors().stream()
					.findFirst()
					.map(err -> err.getField() + ": " + err.getDefaultMessage())
					.orElse("입력값이 올바르지 않습니다.");
			return ResponseEntity.badRequest().body(new ApiErrorResponse(msg));
		}
		boolean hasFile = file != null && !file.isEmpty();
		String clientHash = null;
		if (hasFile) {
			IsoUploadIntentService.Intent intent = isoUploadIntentService.consume(osId, uploadToken);
			if (intent != null) clientHash = intent.clientHash();
		}
		IsoRegistrationService.PreparedIsoRegistration prepared =
				isoRegistrationService.prepare(osId, request, file, clientHash);
		String jobId = isoRegistrationLauncher.startRegistration(prepared);
		String redirect = "/management/os?selectId=" + osId;
		return ResponseEntity.ok(new IsoUploadResponse(jobId, redirect));
	}
}
