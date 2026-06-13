package com.example.serverprovision.management.bios.controller;

import com.example.serverprovision.global.marker.ResourceType;
import com.example.serverprovision.global.trash.service.TypedNameVerifier;
import com.example.serverprovision.management.bios.dto.response.BiosUploadIntentResponse;
import com.example.serverprovision.management.bios.dto.response.BiosUploadResponse;
import com.example.serverprovision.management.bios.service.BiosNudgeService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * BIOS 도메인의 nudge confirm 진입점 — content nudge ({@code /nudge/*}) 와
 * intent path nudge ({@code /intent-nudge/*}) 두 분기를 함께 묶어 관리.
 *
 * <p>R4-1 — fat {@code BiosController} 6분할 결과. 각 분기마다 proceed / replace / cancel 3 액션이라
 * 총 6 endpoint. 모든 도메인 예외 (NudgeNotFoundException · NudgeSessionExpiredException ·
 * InvalidReplaceTargetException · DuplicateBiosVersionException · IllegalBiosStateException) 는
 * advice 가 일괄 매핑 — 컨트롤러 try/catch 추가 금지.</p>
 */
@Controller
@RequestMapping("/management/bios")
@RequiredArgsConstructor
public class BiosNudgeController {

	private final BiosNudgeService biosNudgeService;
	private final TypedNameVerifier typedNameVerifier;

	// ==== MK2 — Nudge confirm 엔드포인트 ===============================

	@PostMapping(path = "/nudge/{nudgeId}/proceed")
	@ResponseBody
	public BiosUploadResponse nudgeProceed(@PathVariable("nudgeId") UUID nudgeId) {
		Long id = biosNudgeService.proceed(nudgeId);
		return new BiosUploadResponse(id, "/management/bios?selectId=" + id);
	}

	@PostMapping(path = "/nudge/{nudgeId}/replace")
	@ResponseBody
	public BiosUploadResponse nudgeReplace(
			@PathVariable("nudgeId") UUID nudgeId,
			@RequestParam("targetId") Long targetId,
			@RequestParam(value = "typedName", required = false) String typedName
	) {
		// S5-2-4 — nudge REPLACE 의 typed-name 검증. typedName 누락 시 점진 마이그레이션 (구 클라이언트 호환).
		if (typedName != null && !typedName.isBlank()) {
			typedNameVerifier.verify(ResourceType.BIOS_BUNDLE, targetId, typedName);
		}
		Long id = biosNudgeService.replace(nudgeId, targetId);
		return new BiosUploadResponse(id, "/management/bios?selectId=" + id);
	}

	@PostMapping(path = "/nudge/{nudgeId}/cancel")
	@ResponseBody
	public ResponseEntity<Void> nudgeCancel(@PathVariable("nudgeId") UUID nudgeId) {
		biosNudgeService.cancel(nudgeId);
		return ResponseEntity.noContent().build();
	}

	// ==== MK2 WAVE 2 — Intent (단계 A) Nudge confirm 엔드포인트 =========
	//  intent 시점 메타 충돌 nudge 의 confirm. proceed/replace 는 새 upload-intent token 을 반환해
	//  클라이언트가 정상 업로드 흐름으로 즉시 복귀.

	@PostMapping(path = "/intent-nudge/{nudgeId}/proceed")
	@ResponseBody
	public BiosUploadIntentResponse intentNudgeProceed(@PathVariable("nudgeId") UUID nudgeId) {
		return biosNudgeService.proceedIntent(nudgeId);
	}

	@PostMapping(path = "/intent-nudge/{nudgeId}/replace")
	@ResponseBody
	public BiosUploadIntentResponse intentNudgeReplace(
			@PathVariable("nudgeId") UUID nudgeId,
			@RequestParam("targetId") Long targetId,
			@RequestParam(value = "typedName", required = false) String typedName
	) {
		// S5-2-4 — intent nudge REPLACE 의 typed-name 검증.
		if (typedName != null && !typedName.isBlank()) {
			typedNameVerifier.verify(ResourceType.BIOS_BUNDLE, targetId, typedName);
		}
		return biosNudgeService.replaceIntent(nudgeId, targetId);
	}

	@PostMapping(path = "/intent-nudge/{nudgeId}/cancel")
	@ResponseBody
	public ResponseEntity<Void> intentNudgeCancel(@PathVariable("nudgeId") UUID nudgeId) {
		biosNudgeService.cancelIntent(nudgeId);
		return ResponseEntity.noContent().build();
	}
}
