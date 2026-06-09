package com.example.serverprovision.provisioning.controller;

import com.example.serverprovision.provisioning.dto.request.BiosSettingsSaveRequest;
import com.example.serverprovision.provisioning.dto.response.BiosSettingsSaveResponse;
import com.example.serverprovision.provisioning.service.BiosSetupService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * BIOS 셋업 저장 (REST). 변경쌍 JSON 본문을 받아 검증·coerce·로깅 후 Redfish 프리뷰를 echo 한다.
 * 컨트롤러 try/catch 없음 — 도메인 예외는 {@code ApiExceptionHandler} 가 상태코드로 매핑한다.
 */
@RestController
@RequestMapping("/provisioning/bios-setup")
@RequiredArgsConstructor
public class BiosSetupRestController {

	private final BiosSetupService biosSetupService;

	@PostMapping("/{boardKey}/save")
	public ResponseEntity<BiosSettingsSaveResponse> save(
			@PathVariable String boardKey,
			@Valid @RequestBody BiosSettingsSaveRequest request) {
		return ResponseEntity.ok(biosSetupService.save(boardKey, request));
	}
}
