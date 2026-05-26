package com.example.serverprovision.management.os.controller;

import com.example.serverprovision.global.marker.ResourceType;
import com.example.serverprovision.global.trash.service.TypedNameVerifier;
import com.example.serverprovision.management.os.dto.response.IsoUploadIntentResponse;
import com.example.serverprovision.management.os.service.OSNudgeService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * ISO 도메인의 nudge confirm 진입점 — 두 분기 (content nudge / intent path nudge) 를 함께 묶어 관리.
 *
 * <p>각 분기마다 proceed / replace / cancel 3 액션이라 총 6 endpoint. 두 분기의 흐름이 거의 같아
 * (require session → 도메인 검증 → 핵심 행위 → consumeSession) 한 컨트롤러로 통합.</p>
 *
 * <p>OS 메타데이터 메타 nudge ({@code /metadata-nudge/*}) 는 {@link OSMetadataNudgeController} 가 별도로 관할.
 * Service 단의 {@code OSNudgeService} vs {@code OSMetadataNudgeService} 분리 (보고서 D1) 와 동일한
 * 도메인 경계를 따른다.</p>
 *
 * <p>응답 record {@link NudgeProceedResponse} 는 본 컨트롤러 전용 — JS modal 이 redirect 후
 * toast 표시에 활용.</p>
 */
@Controller
@RequestMapping("/management/os")
@RequiredArgsConstructor
public class IsoNudgeController {

	private final OSNudgeService osNudgeService;
	private final TypedNameVerifier typedNameVerifier;

	// ==== content nudge (단계 B — 해시 검증 후) =========================

	@PostMapping(path = "/nudge/{nudgeId}/proceed")
	@ResponseBody
	public NudgeProceedResponse nudgeProceed(@PathVariable("nudgeId") UUID nudgeId) {
		Long isoId = osNudgeService.proceed(nudgeId);
		return new NudgeProceedResponse(isoId, "/management/os");
	}

	@PostMapping(path = "/nudge/{nudgeId}/replace")
	@ResponseBody
	public NudgeProceedResponse nudgeReplace(
			@PathVariable("nudgeId") UUID nudgeId,
			@RequestParam(name = "targetId") Long targetId,
			@RequestParam(value = "typedName", required = false) String typedName
	) {
		if (typedName != null && !typedName.isBlank()) {
			typedNameVerifier.verify(ResourceType.OS_ISO, targetId, typedName);
		}
		Long isoId = osNudgeService.replace(nudgeId, targetId);
		return new NudgeProceedResponse(isoId, "/management/os");
	}

	@PostMapping(path = "/nudge/{nudgeId}/cancel")
	@ResponseBody
	public ResponseEntity<Void> nudgeCancel(@PathVariable("nudgeId") UUID nudgeId) {
		osNudgeService.cancel(nudgeId);
		return ResponseEntity.noContent().build();
	}

	// ==== intent path nudge (단계 A — 사전 검증) =========================

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
		if (typedName != null && !typedName.isBlank()) {
			typedNameVerifier.verify(ResourceType.OS_ISO, targetId, typedName);
		}
		return osNudgeService.replaceIntent(nudgeId, targetId);
	}

	@PostMapping(path = "/intent-nudge/{nudgeId}/cancel")
	@ResponseBody
	public ResponseEntity<Void> intentNudgeCancel(@PathVariable("nudgeId") UUID nudgeId) {
		osNudgeService.cancelIntent(nudgeId);
		return ResponseEntity.noContent().build();
	}

	/**
	 * nudge proceed / replace 응답. content nudge 흐름의 두 액션이 공유.
	 */
	public record NudgeProceedResponse(
			Long isoId,
			String redirect
	) {

	}
}
