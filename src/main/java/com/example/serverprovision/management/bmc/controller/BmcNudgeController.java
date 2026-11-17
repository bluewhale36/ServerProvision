package com.example.serverprovision.management.bmc.controller;

import com.example.serverprovision.management.bmc.dto.response.BmcUploadIntentResponse;
import com.example.serverprovision.management.bmc.service.BmcNudgeService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * MA4 BMC 펌웨어 nudge confirm (3택) 진입점 — 두 분기 (content nudge / intent path nudge) 를
 * 함께 묶어 관리.
 *
 * <p>R5-1 분할 — 단일 {@code BmcController} 에서 nudge 책임을 분리.
 * 각 분기마다 proceed / replace / cancel 3 액션이라 총 6 endpoint.
 * 의존성: {@link BmcNudgeService}. 자원명 확인은 서비스 쪽 영구 삭제 경로(UserPurgeService)가 한다.</p>
 */
@Controller
@RequestMapping("/management/bmc")
@RequiredArgsConstructor
public class BmcNudgeController {

	private final BmcNudgeService bmcNudgeService;

	// ==== MK2 nudge confirm (3택) — JSON, advice 가 예외 → 응답 매핑 =====

	@PostMapping(path = "/nudge/{nudgeId}/proceed")
	@ResponseBody
	public ResponseEntity<Void> nudgeProceed(@PathVariable("nudgeId") UUID nudgeId) {
		bmcNudgeService.proceed(nudgeId);
		return ResponseEntity.noContent().build();
	}

	@PostMapping(path = "/nudge/{nudgeId}/replace")
	@ResponseBody
	public ResponseEntity<Void> nudgeReplace(
			@PathVariable("nudgeId") UUID nudgeId,
			@RequestParam("replaceTargetId") Long replaceTargetId,
			@RequestParam(value = "typedName", required = false) String typedName
	) {
		bmcNudgeService.replace(nudgeId, replaceTargetId, typedName);
		return ResponseEntity.noContent().build();
	}

	@PostMapping(path = "/nudge/{nudgeId}/cancel")
	@ResponseBody
	public ResponseEntity<Void> nudgeCancel(@PathVariable("nudgeId") UUID nudgeId) {
		bmcNudgeService.cancel(nudgeId);
		return ResponseEntity.noContent().build();
	}

	// ==== MK2 WAVE 2 — Intent (단계 A) Nudge confirm =====================

	@PostMapping(path = "/intent-nudge/{nudgeId}/proceed")
	@ResponseBody
	public BmcUploadIntentResponse intentNudgeProceed(
			@PathVariable("nudgeId") UUID nudgeId
	) {
		return bmcNudgeService.proceedIntent(nudgeId);
	}

	@PostMapping(path = "/intent-nudge/{nudgeId}/replace")
	@ResponseBody
	public BmcUploadIntentResponse intentNudgeReplace(
			@PathVariable("nudgeId") UUID nudgeId,
			@RequestParam("targetId") Long targetId,
			@RequestParam(value = "typedName", required = false) String typedName
	) {
		return bmcNudgeService.replaceIntent(nudgeId, targetId, typedName);
	}

	@PostMapping(path = "/intent-nudge/{nudgeId}/cancel")
	@ResponseBody
	public ResponseEntity<Void> intentNudgeCancel(@PathVariable("nudgeId") UUID nudgeId) {
		bmcNudgeService.cancelIntent(nudgeId);
		return ResponseEntity.noContent().build();
	}
}
