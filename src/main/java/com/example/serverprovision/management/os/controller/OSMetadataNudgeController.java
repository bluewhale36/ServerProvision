package com.example.serverprovision.management.os.controller;

import com.example.serverprovision.global.marker.ResourceType;
import com.example.serverprovision.global.trash.service.TypedNameVerifier;
import com.example.serverprovision.management.os.dto.response.OSMetadataCreateResponse;
import com.example.serverprovision.management.os.service.metadata.OSMetadataNudgeService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * OS 메타데이터 (메타) nudge confirm 진입점. {@code POST /management/os} 가 메타 충돌 시
 * 던지는 {@code OSMetadataNudgeRequiredException} → 사용자 modal 의 3 액션 (proceed / replace / cancel).
 *
 * <p>ISO 도메인 nudge (content / intent) 는 {@link IsoNudgeController} 가 분리 관할 — 도메인이
 * 다르고 payload 형태도 다르다 (메타는 IntentMetaNudgePayload, ISO 는 ContentNudgePayload).</p>
 *
 * <p>{@link TypedNameVerifier} 는 본 컨트롤러와 {@link IsoNudgeController} 가 공유하는 유일한 의존성
 * — replace 시 사용자 typed-name 입력 일치 검증. 두 도메인 모두 필요해 중복은 불가피.</p>
 */
@Controller
@RequestMapping("/management/os/metadata-nudge")
@RequiredArgsConstructor
public class OSMetadataNudgeController {

	private final OSMetadataNudgeService osMetadataNudgeService;
	private final TypedNameVerifier typedNameVerifier;

	@PostMapping("/{nudgeId}/proceed")
	@ResponseBody
	public OSMetadataCreateResponse proceed(@PathVariable("nudgeId") UUID nudgeId) {
		Long id = osMetadataNudgeService.proceed(nudgeId);
		return new OSMetadataCreateResponse(id, "/management/os?selectId=" + id);
	}

	@PostMapping("/{nudgeId}/replace")
	@ResponseBody
	public OSMetadataCreateResponse replace(
			@PathVariable("nudgeId") UUID nudgeId,
			@RequestParam(name = "targetId") Long targetId,
			@RequestParam(value = "typedName", required = false) String typedName
	) {
		if (typedName != null && !typedName.isBlank()) {
			typedNameVerifier.verify(ResourceType.OS_IMAGE, targetId, typedName);
		}
		Long id = osMetadataNudgeService.replace(nudgeId, targetId);
		return new OSMetadataCreateResponse(id, "/management/os?selectId=" + id);
	}

	@PostMapping("/{nudgeId}/cancel")
	@ResponseBody
	public ResponseEntity<Void> cancel(@PathVariable("nudgeId") UUID nudgeId) {
		osMetadataNudgeService.cancel(nudgeId);
		return ResponseEntity.noContent().build();
	}
}
