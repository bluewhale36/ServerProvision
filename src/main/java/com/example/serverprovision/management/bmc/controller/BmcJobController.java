package com.example.serverprovision.management.bmc.controller;

import com.example.serverprovision.global.job.dto.response.JobStartResponse;
import com.example.serverprovision.management.bmc.service.BmcIntegrityService;
import com.example.serverprovision.management.bmc.service.BmcVerificationLauncher;
import com.example.serverprovision.management.common.dto.response.IntegrityStatusResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

/**
 * MA4 BMC 펌웨어 무결성 검증 job (verify 실행 / integrity-status 조회) MVC 컨트롤러.
 *
 * <p>R5-1 분할 — 단일 {@code BmcController} 에서 job 책임을 분리. R5-3 — integrity-status 조회 위임 대상을
 * {@link BmcIntegrityService} 로 전환.
 * 의존성: {@link BmcVerificationLauncher}, {@link BmcIntegrityService}.
 * Launcher / Runner 분리 + {@code IntegrityStatus.displayMessage} 도메인 이관은 R5-2 위임.</p>
 */
@Controller
@RequestMapping("/management/bmc")
@RequiredArgsConstructor
public class BmcJobController {

	private final BmcVerificationLauncher bmcVerificationLauncher;
	private final BmcIntegrityService bmcIntegrityService;

	@PostMapping(path = "/{boardId}/bmc/{bmcId}/verify")
	@ResponseBody
	public JobStartResponse verify(
			@PathVariable("boardId") Long boardId,
			@PathVariable("bmcId") Long bmcId
	) {
		String jobId = bmcVerificationLauncher.startVerification(boardId, bmcId);
		return new JobStartResponse(jobId);
	}

	@GetMapping(path = "/{boardId}/bmc/{bmcId}/integrity-status")
	@ResponseBody
	public IntegrityStatusResponse integrityStatus(
			@PathVariable("boardId") Long boardId,
			@PathVariable("bmcId") Long bmcId
	) {
		return bmcIntegrityService.findIntegrityStatus(boardId, bmcId);
	}
}
