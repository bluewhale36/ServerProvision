package com.example.serverprovision.maintenance.reconciliation.controller;

import com.example.serverprovision.global.job.dto.response.JobStartResponse;
import com.example.serverprovision.maintenance.reconciliation.dto.response.DriftReportResponse;
import com.example.serverprovision.maintenance.reconciliation.service.PathReconciliationService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.servlet.view.RedirectView;

import java.util.Optional;

/**
 * MK1 자원 무결성 점검 REST 엔드포인트.
 * <p>POST 액션은 페이지에서 form submit 으로도 호출되므로 redirect 로 응답한다 (PRG 패턴).
 * 스캔 트리거만 BackgroundJob jobId 를 JSON 으로 반환 — 작업 조회 아이콘에서 추적.</p>
 */
@RestController
@RequestMapping("/maintenance/reconciliation")
@RequiredArgsConstructor
public class ReconciliationRestController {

	private final PathReconciliationService reconciliationService;
	private final com.example.serverprovision.maintenance.reconciliation.service.recheck.DriftRecheckService driftRecheckService;
	private final com.example.serverprovision.maintenance.reconciliation.service.HashAcceptService hashAcceptService;
	private final com.example.serverprovision.maintenance.reconciliation.service.DuplicateResolveService duplicateResolveService;

	/**
	 * 가장 최근 보고서 1 건. 한번도 스캔된 적 없으면 204.
	 */
	@GetMapping("/latest")
	public ResponseEntity<DriftReportResponse> latest() {
		Optional<DriftReportResponse> latest = reconciliationService.latestReport();
		return latest.map(ResponseEntity::ok)
				.orElseGet(() -> ResponseEntity.noContent().build());
	}

	/**
	 * 페이지네이션 이력. 페이지 UI 가 아닌 외부 API 용도.
	 */
	@GetMapping("/history")
	public Page<DriftReportResponse> history(
			@RequestParam(name = "page", defaultValue = "0") int page,
			@RequestParam(name = "size", defaultValue = "20") int size
	) {
		int safeSize = (size <= 0 || size > 100) ? 20 : size;
		Pageable pageable = PageRequest.of(
				Math.max(page, 0), safeSize,
				Sort.by(Sort.Direction.DESC, "scannedAt")
		);
		return reconciliationService.history(pageable);
	}

	/**
	 * 수동 스캔 트리거. {@code deep=true} 면 manifestHash 재계산 포함 (수십 초~수 분).
	 */
	@PostMapping("/scan")
	public ResponseEntity<JobStartResponse> scan(
			@RequestParam(name = "deep", defaultValue = "false") boolean deep,
			@RequestParam(name = "redirect", required = false) String redirect,
			RedirectAttributes redirectAttributes
	) {
		String jobId = reconciliationService.triggerScan(deep);
		return ResponseEntity.ok(new JobStartResponse(jobId));
	}

	/**
	 * (권고1) 마커 서명 일괄 재발급 — secret 회전 후 1회 호출.
	 * 모든 활성 자원의 marker signature 만 새 secret 으로 재계산하고 manifestHash 는 그대로 둔다.
	 * 변조 의심 자원의 hash 가 굳어지지 않으므로 이후 deep scan 에서 그대로 감지된다.
	 */
	@PostMapping("/reissue-all-markers")
	public ResponseEntity<JobStartResponse> reissueAllMarkers() {
		String jobId = reconciliationService.triggerReissueAllSignatures();
		return ResponseEntity.ok(new JobStartResponse(jobId));
	}

	/**
	 * PATH_DRIFT 자동 적용 (단건). 페이지 form 호출 시 list 로 redirect.
	 */
	@PostMapping("/drifts/{driftId}/apply")
	public RedirectView apply(@PathVariable Long driftId, RedirectAttributes redirectAttributes) {
		reconciliationService.apply(driftId);
		// R9-3 — JS 경로는 async 제출(토스트+reload)이라 flash 미소비. 이 flash 는 JS 불능 native submit fallback 용.
		redirectAttributes.addFlashAttribute("flashMessage", "드리프트 적용 완료");
		return new RedirectView("/maintenance/reconciliation");
	}

	/**
	 * 단건 무시 처리 — 보고서에서 해당 drift 행 삭제.
	 */
	/**
	 * S6-3-3 — [다시 점검] : 그 자원 하나만 즉시 재확인. 해소면 카드 제거 후 resolved=true,
	 * 잔존이면 카드 불변 + resolved=false (프론트가 토스트로 구분 안내).
	 */
	@PostMapping("/drifts/{driftId}/recheck")
	@org.springframework.web.bind.annotation.ResponseBody
	public java.util.Map<String, Boolean> recheck(@PathVariable Long driftId) {
		return java.util.Map.of("resolved", driftRecheckService.recheck(driftId));
	}

	/**
	 * S6-3-4 — [현재 내용을 정본으로 수용] : 자원명 확인 통과 시 백그라운드 수용 작업 시작.
	 * 완료는 bgjob 이벤트가 카드 제거를 화면에 반영 (표준 apply 와 다른 비동기 계약이라 전용 엔드포인트).
	 */
	@PostMapping("/drifts/{driftId}/accept-hash")
	@org.springframework.web.bind.annotation.ResponseBody
	public java.util.Map<String, String> acceptHash(
			@PathVariable Long driftId,
			@org.springframework.web.bind.annotation.RequestParam String typedName) {
		return java.util.Map.of("jobId", hashAcceptService.triggerAccept(driftId, typedName));
	}

	/**
	 * HF4-5 — [자원 중복 존재] 택일 해소 : 남길 쪽(survivor)을 받아 나머지를 파일시스템에서 삭제한다.
	 * 사용자 입력을 동반하는 해결의 전용 endpoint 선례(accept-hash) — 응답은 표준 apply 계약(redirect+flash,
	 * JS 는 async 제출 + 토스트). survivor 는 enum 바인딩이라 잘못된 값은 framework 가 400 으로 거절한다.
	 */
	@PostMapping("/drifts/{driftId}/resolve-duplicate")
	public RedirectView resolveDuplicate(
			@PathVariable Long driftId,
			@RequestParam("survivor") com.example.serverprovision.maintenance.reconciliation.service.DuplicateSurvivor survivor,
			RedirectAttributes redirectAttributes
	) {
		duplicateResolveService.resolve(driftId, survivor);
		redirectAttributes.addFlashAttribute("flashMessage", "자원 중복을 해소했습니다");
		return new RedirectView("/maintenance/reconciliation");
	}

	@PostMapping("/drifts/{driftId}/dismiss")
	public RedirectView dismiss(@PathVariable Long driftId, RedirectAttributes redirectAttributes) {
		reconciliationService.dismiss(driftId);
		redirectAttributes.addFlashAttribute("flashMessage", "드리프트 보고를 닫았습니다");
		return new RedirectView("/maintenance/reconciliation");
	}
}
