package com.example.serverprovision.management.subprogram.controller;

import com.example.serverprovision.management.subprogram.dto.response.SubprogramUploadResponse;
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

	/* ─────────────────────────── REST: nudge confirm (MK2) ─────────────────────────── */

	@PostMapping("/nudge/{nudgeId}/proceed")
	@ResponseBody
	public SubprogramUploadResponse nudgeProceed(@PathVariable("nudgeId") UUID nudgeId) {
		return uploaded(subprogramNudgeService.proceed(nudgeId));
	}

	@PostMapping("/nudge/{nudgeId}/replace")
	@ResponseBody
	public SubprogramUploadResponse nudgeReplace(
			@PathVariable("nudgeId") UUID nudgeId,
			@RequestParam("targetId") Long targetId,
			@RequestParam(value = "typedName", required = false) String typedName
	) {
		return uploaded(subprogramNudgeService.replace(nudgeId, targetId, typedName));
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
		return subprogramNudgeService.replaceIntent(nudgeId, targetId, typedName);
	}

	@PostMapping("/intent-nudge/{nudgeId}/cancel")
	@ResponseBody
	public ResponseEntity<Void> intentNudgeCancel(@PathVariable("nudgeId") UUID nudgeId) {
		subprogramNudgeService.cancelIntent(nudgeId);
		return ResponseEntity.noContent().build();
	}

	/** 일반 등록 성공과 같은 응답(HF30-3 D11). 목록은 selectId 만으로 종류와 범위를 찾아 선택을 복원한다. */
	private static SubprogramUploadResponse uploaded(Long id) {
		return new SubprogramUploadResponse(id, "/management/subprogram?selectId=" + id);
	}
}
