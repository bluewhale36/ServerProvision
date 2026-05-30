package com.example.serverprovision.management.os.controller;

import com.example.serverprovision.global.job.dto.response.JobStartResponse;
import com.example.serverprovision.management.common.dto.response.IntegrityStatusResponse;
import com.example.serverprovision.management.os.service.comps.CompsExtractionLauncher;
import com.example.serverprovision.management.os.service.iso.IsoIntegrityService;
import com.example.serverprovision.management.os.service.iso.IsoVerificationLauncher;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

/**
 * ISO 의 BackgroundJob 트리거 / 조회 endpoint 묶음 — 무결성 검증 시작 / 상태 조회 / comps 추출 시작.
 *
 * <p>세 endpoint 모두 "사용자 액션 → BackgroundJob 등록 후 jobId 반환" 또는 "단순 read" 라는 동일
 * 패턴을 공유하고, 다른 controller 와 의존성 중복이 거의 없도록 한 곳에 묶었다.</p>
 *
 * <p>의존성: {@link IsoIntegrityService} (integrity-status read), {@link IsoVerificationLauncher},
 * {@link CompsExtractionLauncher}. 단건 marker 재발급 endpoint 는 위험도가 높아 제거되었고,
 * 일괄 재발급은 PathReconciliationService 가 별도 도메인에서 관할.</p>
 */
@Controller
@RequestMapping("/management/os")
@RequiredArgsConstructor
public class IsoJobController {

	private final IsoIntegrityService isoIntegrityService;
	private final IsoVerificationLauncher isoVerificationLauncher;
	private final CompsExtractionLauncher compsExtractionLauncher;

	/**
	 * ISO sidecar 마커 무결성 검증. BackgroundJob 으로 비동기 실행 — 호출 측은 jobId 만 받고 즉시 반환,
	 * 결과(서명/해시 통과 여부) 는 알림 센터(서류가방) 의 작업 카드 색상으로 확인한다.
	 * 페이지 이탈해도 작업이 계속되므로 대용량 ISO 의 SHA-256 재계산이 사용자 흐름을 막지 않는다.
	 */
	@PostMapping(path = "/{osId}/iso/{isoId}/verify")
	@ResponseBody
	public JobStartResponse verifyIso(
			@PathVariable("osId") Long osId,
			@PathVariable("isoId") Long isoId
	) {
		String jobId = isoVerificationLauncher.startVerification(osId, isoId);
		return new JobStartResponse(jobId);
	}

	@GetMapping(path = "/{osId}/iso/{isoId}/integrity-status")
	@ResponseBody
	public IntegrityStatusResponse integrityStatus(
			@PathVariable("osId") Long osId,
			@PathVariable("isoId") Long isoId
	) {
		return isoIntegrityService.findStatus(osId, isoId);
	}

	/**
	 * 추출 시작. 동일 ISO 에 이미 활성 Job 이 있으면 새 Job 을 만들지 않고 기존 jobId 를 반환한다.
	 * 프론트는 반환된 jobId 를 알림 센터(서류가방 드롭다운) 에서 추적한다.
	 */
	@PostMapping("/{osId}/iso/{isoId}/extract")
	@ResponseBody
	public JobStartResponse startExtract(
			@PathVariable("osId") Long osId,
			@PathVariable("isoId") Long isoId
	) {
		String jobId = compsExtractionLauncher.startExtraction(osId, isoId);
		return new JobStartResponse(jobId);
	}
}
