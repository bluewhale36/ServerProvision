package com.example.serverprovision.management.bios.controller;

import com.example.serverprovision.global.job.dto.response.JobStartResponse;
import com.example.serverprovision.management.bios.service.BiosService;
import com.example.serverprovision.management.bios.service.BiosVerificationLauncher;
import com.example.serverprovision.management.common.dto.response.IntegrityStatusResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

/**
 * BIOS 번들 무결성 / marker 관리 진입점 — 비동기 재검증 job 시작 + 현재 무결성 상태 조회.
 *
 * <p>R4-1 — fat {@code BiosController} 6분할 결과. 두 엔드포인트 모두 JSON 응답.</p>
 *
 * <p>Verify 는 무결성 상태만 조회 (수정 안 함) 이지만 POST 로 고정 — 상태 계산 비용이 크고
 * 감사 로그에 남길 필요가 있어 단순 GET 으로 캐싱되지 않도록 함.</p>
 */
@Controller
@RequestMapping("/management/bios")
@RequiredArgsConstructor
public class BiosIntegrityController {

	private final BiosVerificationLauncher biosVerificationLauncher;
	private final BiosService biosService;

	/**
	 * 현재 트리의 무결성 재검증을 BackgroundJob 으로 비동기 실행. 호출 측은 jobId 만 받고,
	 * 결과(서명/해시 통과 여부) 는 알림 센터의 작업 카드 색상으로 확인한다.
	 * 디렉토리 트리 manifest 재계산은 파일 수에 비례해 시간이 늘어나므로 비동기화 효과가 크다.
	 */
	@PostMapping(path = "/{boardId}/bios/{biosId}/verify")
	@ResponseBody
	public JobStartResponse verify(
			@PathVariable("boardId") Long boardId,
			@PathVariable("biosId") Long biosId
	) {
		String jobId = biosVerificationLauncher.startVerification(boardId, biosId);
		return new JobStartResponse(jobId);
	}

	/**
	 * 현재 시점의 무결성 상태를 즉시 계산해 badge 렌더링용 JSON 으로 반환한다.
	 * CP2 단계에서는 persisted last status 가 아직 없으므로 조회 시마다 재계산한다.
	 */
	@GetMapping(path = "/{boardId}/bios/{biosId}/integrity-status")
	@ResponseBody
	public IntegrityStatusResponse integrityStatus(
			@PathVariable("boardId") Long boardId,
			@PathVariable("biosId") Long biosId
	) {
		return biosService.findIntegrityStatus(boardId, biosId);
	}

	// 단건 marker 재발급 endpoint 는 위험도가 높아 제거됨. 일괄 재발급은
	// PathReconciliationService.triggerReissueAllSignatures (POST /maintenance/reconciliation/reissue-all-markers) 로만 호출.
}
