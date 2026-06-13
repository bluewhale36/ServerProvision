package com.example.serverprovision.management.subprogram.controller;

import com.example.serverprovision.management.subprogram.service.SubprogramNudgeService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * MA5 Subprogram 의 중복 충돌 nudge confirm 진입점 — 두 분기 (content nudge / intent path nudge) 를
 * 함께 묶어 관리.
 *
 * <p>각 분기마다 proceed / replace / cancel 3 액션이라 총 6 endpoint. R6-1 에서 fat
 * {@code SubprogramController} 의 nudge 기능군을 분리. ISO 의 {@code IsoNudgeController} 와 동형.</p>
 */
@Controller
@RequestMapping("/management/subprogram")
@RequiredArgsConstructor
public class SubprogramNudgeController {

	private final SubprogramNudgeService subprogramNudgeService;
	private final com.example.serverprovision.global.trash.service.TypedNameVerifier typedNameVerifier;

	/* ─────────────────────────── REST: nudge confirm (MK2) ─────────────────────────── */

	@PostMapping("/nudge/{nudgeId}/proceed")
	@ResponseBody
	public ResponseEntity<Void> nudgeProceed(@PathVariable("nudgeId") UUID nudgeId) {
		subprogramNudgeService.proceed(nudgeId);
		return ResponseEntity.noContent().build();
	}

	@PostMapping("/nudge/{nudgeId}/replace")
	@ResponseBody
	public ResponseEntity<Void> nudgeReplace(
			@PathVariable("nudgeId") UUID nudgeId,
			@RequestParam("targetId") Long targetId,
			@RequestParam(value = "typedName", required = false) String typedName
	) {
		if (typedName != null && !typedName.isBlank()) {
			typedNameVerifier.verify(com.example.serverprovision.global.marker.ResourceType.SUBPROGRAM, targetId, typedName);
		}
		subprogramNudgeService.replace(nudgeId, targetId);
		return ResponseEntity.noContent().build();
	}

	@PostMapping("/nudge/{nudgeId}/cancel")
	@ResponseBody
	public ResponseEntity<Void> nudgeCancel(@PathVariable("nudgeId") UUID nudgeId) {
		subprogramNudgeService.cancel(nudgeId);
		return ResponseEntity.noContent().build();
	}

	/* ─── MK2 WAVE 2 — Intent (단계 A) Nudge confirm ─── */

	@PostMapping("/intent-nudge/{nudgeId}/proceed")
	@ResponseBody
	public com.example.serverprovision.management.subprogram.dto.response.SubprogramUploadIntentResponse intentNudgeProceed(
			@PathVariable("nudgeId") UUID nudgeId
	) {
		return subprogramNudgeService.proceedIntent(nudgeId);
	}

	@PostMapping("/intent-nudge/{nudgeId}/replace")
	@ResponseBody
	public com.example.serverprovision.management.subprogram.dto.response.SubprogramUploadIntentResponse intentNudgeReplace(
			@PathVariable("nudgeId") UUID nudgeId,
			@RequestParam("targetId") Long targetId,
			@RequestParam(value = "typedName", required = false) String typedName
	) {
		if (typedName != null && !typedName.isBlank()) {
			typedNameVerifier.verify(com.example.serverprovision.global.marker.ResourceType.SUBPROGRAM, targetId, typedName);
		}
		return subprogramNudgeService.replaceIntent(nudgeId, targetId);
	}

	@PostMapping("/intent-nudge/{nudgeId}/cancel")
	@ResponseBody
	public ResponseEntity<Void> intentNudgeCancel(@PathVariable("nudgeId") UUID nudgeId) {
		subprogramNudgeService.cancelIntent(nudgeId);
		return ResponseEntity.noContent().build();
	}
}
