package com.example.serverprovision.management.raidcard.controller;

import com.example.serverprovision.global.marker.ResourceType;
import com.example.serverprovision.global.trash.service.TypedNameVerifier;
import com.example.serverprovision.management.raidcard.dto.response.RaidCardCreateResponse;
import com.example.serverprovision.management.raidcard.service.RaidCardNudgeService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * MA7. RAID 카드 메타 nudge confirm 진입점 — proceed / replace / cancel (XHR JSON).
 *
 * <p>메타 충돌 시 등록 흐름을 nudge confirm 으로 분기해 사용자가 진행 / 교체 / 취소를 선택한다
 * (MK2 WAVE 1 · BoardModelNudgeController 선례 — replace 의 typed-name 검증이 controller 에 있는
 * 잔존 비대칭도 선례와 같은 자리이며, 위치 통일은 그쪽 R 캠페인이 움직일 때 함께 움직인다).</p>
 */
@Controller
@RequestMapping("/management/raidcard")
@RequiredArgsConstructor
public class RaidCardNudgeController {

	private final RaidCardNudgeService raidCardNudgeService;
	private final TypedNameVerifier typedNameVerifier;

	@PostMapping(path = "/nudge/{nudgeId}/proceed")
	@ResponseBody
	public RaidCardCreateResponse nudgeProceed(@PathVariable("nudgeId") UUID nudgeId) {
		Long id = raidCardNudgeService.proceed(nudgeId);
		return new RaidCardCreateResponse(id, "/management/raidcard?selectId=" + id);
	}

	@PostMapping(path = "/nudge/{nudgeId}/replace")
	@ResponseBody
	public RaidCardCreateResponse nudgeReplace(
			@PathVariable("nudgeId") UUID nudgeId,
			@RequestParam("targetId") Long targetId,
			@RequestParam(value = "typedName", required = false) String typedName
	) {
		if (typedName != null && !typedName.isBlank()) {
			typedNameVerifier.verify(ResourceType.RAID_CARD, targetId, typedName);
		}
		Long id = raidCardNudgeService.replace(nudgeId, targetId);
		return new RaidCardCreateResponse(id, "/management/raidcard?selectId=" + id);
	}

	@PostMapping(path = "/nudge/{nudgeId}/cancel")
	@ResponseBody
	public ResponseEntity<Void> nudgeCancel(@PathVariable("nudgeId") UUID nudgeId) {
		raidCardNudgeService.cancel(nudgeId);
		return ResponseEntity.noContent().build();
	}
}
