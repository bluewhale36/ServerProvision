package com.example.serverprovision.management.board.controller;

import com.example.serverprovision.global.marker.ResourceType;
import com.example.serverprovision.global.trash.service.TypedNameVerifier;
import com.example.serverprovision.management.board.dto.response.BoardModelCreateResponse;
import com.example.serverprovision.management.board.service.BoardModelNudgeService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * A2. 메인보드 모델 메타 nudge confirm 진입점 — proceed / replace / cancel (XHR JSON).
 *
 * <p>R3-2 — 단일 fat {@code BoardModelController} 분할. 메타 CRUD 는 {@link BoardModelMetadataController},
 * lifecycle 상태 전이는 {@link BoardModelLifecycleController} 가 관할.</p>
 *
 * <p>MK2 WAVE 1 — 메타 충돌 시 등록 흐름을 nudge confirm 으로 분기해 사용자가 진행 / 교체 / 취소를 선택.</p>
 *
 * <p>잔존 책임 (R3-5 이월) — {@link #nudgeReplace} 의 {@code typedNameVerifier.verify(...)} 직접 호출은
 * 도메인 검증이 controller 로 누수된 형태다. 본 슬라이스는 동작 보존을 위해 호출을 그대로 이동만 하고,
 * 검증 위치를 service 층으로 끌어올리는 SSOT 통일은 R3-5 에서 처리한다.</p>
 */
@Controller
@RequestMapping("/management/board")
@RequiredArgsConstructor
public class BoardModelNudgeController {

	private final BoardModelNudgeService boardModelNudgeService;
	private final TypedNameVerifier typedNameVerifier;

	// ==== MK2 WAVE 1 — BoardModel 메타 nudge confirm ===================

	@PostMapping(path = "/nudge/{nudgeId}/proceed")
	@ResponseBody
	public BoardModelCreateResponse nudgeProceed(@PathVariable("nudgeId") UUID nudgeId) {
		Long id = boardModelNudgeService.proceed(nudgeId);
		return new BoardModelCreateResponse(id, "/management/board?selectId=" + id);
	}

	@PostMapping(path = "/nudge/{nudgeId}/replace")
	@ResponseBody
	public BoardModelCreateResponse nudgeReplace(
			@PathVariable("nudgeId") UUID nudgeId,
			@RequestParam("targetId") Long targetId,
			@RequestParam(value = "typedName", required = false) String typedName
	) {
		if (typedName != null && !typedName.isBlank()) {
			typedNameVerifier.verify(ResourceType.BOARD_MODEL, targetId, typedName);
		}
		Long id = boardModelNudgeService.replace(nudgeId, targetId);
		return new BoardModelCreateResponse(id, "/management/board?selectId=" + id);
	}

	@PostMapping(path = "/nudge/{nudgeId}/cancel")
	@ResponseBody
	public ResponseEntity<Void> nudgeCancel(@PathVariable("nudgeId") UUID nudgeId) {
		boardModelNudgeService.cancel(nudgeId);
		return ResponseEntity.noContent().build();
	}
}
