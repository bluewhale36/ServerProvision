package com.example.serverprovision.management.os.controller;

import com.example.serverprovision.management.os.dto.response.IsoUploadIntentResponse;
import com.example.serverprovision.management.os.service.OSNudgeService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * ISO 업로드 전 중복 확인 창의 3택 진입점(proceed / replace / cancel).
 *
 * <p>HF30-2 — 배경 등록 단계의 휴지통 창 진입점({@code /nudge/*})은 없앴다. 그 창을 띄우던 판정이 사라졌고, 화면에도 연결되지
 * 않아 [그래도 등록] 이 항상 실패했다(docs C-17). 자원명 확인은 서비스 쪽 영구 삭제 경로(UserPurgeService)가 한다.</p>
 */
@Controller
@RequestMapping("/management/os")
@RequiredArgsConstructor
public class IsoNudgeController {

	private final OSNudgeService osNudgeService;

	@PostMapping(path = "/intent-nudge/{nudgeId}/proceed")
	@ResponseBody
	public IsoUploadIntentResponse intentNudgeProceed(@PathVariable("nudgeId") UUID nudgeId) {
		return osNudgeService.proceedIntent(nudgeId);
	}

	@PostMapping(path = "/intent-nudge/{nudgeId}/replace")
	@ResponseBody
	public IsoUploadIntentResponse intentNudgeReplace(
			@PathVariable("nudgeId") UUID nudgeId,
			@RequestParam("targetId") Long targetId,
			@RequestParam(value = "typedName", required = false) String typedName
	) {
		return osNudgeService.replaceIntent(nudgeId, targetId, typedName);
	}

	@PostMapping(path = "/intent-nudge/{nudgeId}/cancel")
	@ResponseBody
	public ResponseEntity<Void> intentNudgeCancel(@PathVariable("nudgeId") UUID nudgeId) {
		osNudgeService.cancelIntent(nudgeId);
		return ResponseEntity.noContent().build();
	}
}
