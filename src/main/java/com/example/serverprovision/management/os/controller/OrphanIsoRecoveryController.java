package com.example.serverprovision.management.os.controller;

import com.example.serverprovision.management.os.dto.response.OrphanIsoQuarantineResponse;
import com.example.serverprovision.management.os.dto.response.OrphanRetryResponse;
import com.example.serverprovision.management.os.service.iso.OrphanIsoRecoveryService;
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
 * 격리된 오펀 ISO 의 복구(재시도/폐기) endpoint. nudge 컨트롤러와 동일하게 hybrid 이며
 * try/catch 없이 도메인 예외를 전역 advice 에 위임한다(NotFound→404, Conflict→409, TypedNameMismatch→400).
 *
 * <p>오펀은 in-memory job 이 10분 후 prune 되면 알림 센터에서 사라지므로, durable 페이지가 fallback 진입점이다.
 * recoveryId 가 nudgeId 역할(모달/복구 액션의 키)을 한다.</p>
 */
@Controller
@RequestMapping("/maintenance/quarantine")
@RequiredArgsConstructor
public class OrphanIsoRecoveryController {

	private final OrphanIsoRecoveryService orphanIsoRecoveryService;

	/** durable 목록 페이지 — PENDING 격리 자원을 표로 노출 (재시도/폐기 진입점). */
	@GetMapping
	public String page(Model model) {
		model.addAttribute("orphans", orphanIsoRecoveryService.listPending());
		return "maintenance/quarantine/list";
	}

	/** 단일 격리 레코드 JSON — 복구 모달이 recoveryId 로 상세를 채울 때. */
	@GetMapping("/{recoveryId}")
	@ResponseBody
	public OrphanIsoQuarantineResponse detail(@PathVariable String recoveryId) {
		return orphanIsoRecoveryService.get(recoveryId);
	}

	/** 재시도 — 격리 파일 복원 + 새 등록 job 시작. */
	@PostMapping("/{recoveryId}/retry")
	@ResponseBody
	public OrphanRetryResponse retry(@PathVariable String recoveryId) {
		return orphanIsoRecoveryService.retry(recoveryId);
	}

	/** 폐기 — 격리 파일 삭제. typedName(파일명) 일치 필요(파괴적 가드). */
	@PostMapping("/{recoveryId}/discard")
	@ResponseBody
	public ResponseEntity<Void> discard(@PathVariable String recoveryId,
	                                    @RequestParam(value = "typedName", required = false) String typedName) {
		orphanIsoRecoveryService.discard(recoveryId, typedName);
		return ResponseEntity.noContent().build();
	}
}
