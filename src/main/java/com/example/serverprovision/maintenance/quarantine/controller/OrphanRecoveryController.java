package com.example.serverprovision.maintenance.quarantine.controller;

import com.example.serverprovision.global.orphan.dto.OrphanQuarantineResponse;
import com.example.serverprovision.global.orphan.dto.OrphanRetryResponse;
import com.example.serverprovision.global.orphan.service.OrphanQuarantineService;
import com.example.serverprovision.global.orphan.service.OrphanRecoveryService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

/**
 * 격리된 오펀 자원의 복구(재시도/폐기) endpoint. 도메인 무관 saga 의 두 서비스
 * ({@code OrphanQuarantineService} 조회 / {@code OrphanRecoveryService} 액션)에 위임하며,
 * try/catch 없이 도메인 예외를 전역 advice 에 맡긴다(NotFound→404, Conflict→409, TypedNameMismatch→400).
 *
 * <p>R1-4-4 — 구 {@code management/os/controller/OrphanIsoRecoveryController} 를 도메인 무관 saga 의 UI 진입점으로
 * {@code maintenance/quarantine} 으로 이전(global/trash + maintenance/trash 선례와 정합). URL 은 불변.
 * 오펀은 in-memory job 이 prune 되면 알림 센터에서 사라지므로, 본 durable 페이지가 fallback 진입점이다.
 * recoveryId 가 nudgeId 역할(모달/복구 액션의 키)을 한다.</p>
 */
@Controller
@RequestMapping("/maintenance/quarantine")
@RequiredArgsConstructor
public class OrphanRecoveryController {

	private final OrphanQuarantineService quarantineService;
	private final OrphanRecoveryService recoveryService;

	/** durable 목록 페이지 — PENDING 격리 자원을 표로 노출 (재시도/폐기 진입점). */
	@GetMapping
	public String page(Model model) {
		model.addAttribute("orphans", quarantineService.listPending());
		return "maintenance/quarantine/list";
	}

	/** 단일 격리 레코드 JSON — 복구 모달이 recoveryId 로 상세를 채울 때. */
	@GetMapping("/{recoveryId}")
	@ResponseBody
	public OrphanQuarantineResponse detail(@PathVariable String recoveryId) {
		return quarantineService.get(recoveryId);
	}

	/** 재시도 — 격리 파일 복원 + SPI 재등록 위임. */
	@PostMapping("/{recoveryId}/retry")
	@ResponseBody
	public OrphanRetryResponse retry(@PathVariable String recoveryId) {
		return recoveryService.retry(recoveryId);
	}

	/** 폐기 — 격리 파일 삭제. typedName(파일명) 일치 필요(파괴적 가드). */
	@PostMapping("/{recoveryId}/discard")
	@ResponseBody
	public ResponseEntity<Void> discard(@PathVariable String recoveryId,
	                                    @RequestParam(value = "typedName", required = false) String typedName) {
		recoveryService.discard(recoveryId, typedName);
		return ResponseEntity.noContent().build();
	}
}
