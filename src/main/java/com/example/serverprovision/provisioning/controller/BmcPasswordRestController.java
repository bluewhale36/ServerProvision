package com.example.serverprovision.provisioning.controller;

import com.example.serverprovision.provisioning.dto.request.BmcPasswordChangeRequest;
import com.example.serverprovision.provisioning.dto.response.BmcPasswordPlanResponse;
import com.example.serverprovision.provisioning.service.BmcPasswordService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * BMC 비밀번호 변경 적용 계획 산출 (REST). 검증 실패는 전역 advice 가 400+fieldErrors 로 매핑한다.
 */
@RestController
@RequestMapping("/provisioning/bmc-password")
@RequiredArgsConstructor
public class BmcPasswordRestController {

	private final BmcPasswordService bmcPasswordService;

	@PostMapping("/plan")
	public ResponseEntity<BmcPasswordPlanResponse> plan(@Valid @RequestBody BmcPasswordChangeRequest request) {
		return ResponseEntity.ok(bmcPasswordService.plan(request));
	}
}
