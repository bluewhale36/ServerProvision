package com.example.serverprovision.maintenance.reconciliation.controller;

import com.example.serverprovision.global.job.dto.response.JobStartResponse;
import com.example.serverprovision.maintenance.reconciliation.dto.request.DriftSnoozeRequest;
import com.example.serverprovision.maintenance.reconciliation.dto.response.BulkApplyResponse;
import com.example.serverprovision.maintenance.reconciliation.dto.response.DriftReportResponse;
import jakarta.validation.Valid;
import com.example.serverprovision.maintenance.reconciliation.service.PathReconciliationService;
import com.example.serverprovision.maintenance.reconciliation.enums.ScanDepth;
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
	private final com.example.serverprovision.maintenance.reconciliation.service.DriftBulkApplyService bulkApplyService;
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
		// 요청 파라미터는 boolean 이지만 서비스 경계에서는 타입으로 올린다(MK4-3-2).
		String jobId = reconciliationService.triggerScan(ScanDepth.of(deep));
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
	 * MK4-4-2 — 후보 A 의 [전체 해결] : 그 회차에서 보인 문제 중 지금 해결할 수 있는 것 전부.
	 *
	 * <p>건별로 독립 트랜잭션이라 한 건의 실패가 나머지를 되돌리지 않는다(자세한 이유는
	 * {@link com.example.serverprovision.maintenance.reconciliation.service.DriftBulkApplyService}).
	 * 그래서 응답이 "성공/실패" 가 아니라 <b>몇 건 중 몇 건</b>이다.</p>
	 */
	@PostMapping("/reports/{reportId}/apply-all")
	public BulkApplyResponse applyAllInReport(@PathVariable Long reportId) {
		return bulkApplyService.applyAll(bulkApplyService.targetsInReport(reportId));
	}

	/**
	 * MK4-4-2 — 후보 B 의 [전체 해결] : 지금 목록에 떠 있는 문제 중 해결할 수 있는 것 전부.
	 *
	 * <p>대상이 회차가 아니라 현재 상태라는 점만 위와 다르다. 이 범위 차이 자체가 두 후보의 비교
	 * 대상이다 — 첫 화면이 무엇이냐에 따라 "전체" 의 뜻이 자연히 갈린다.</p>
	 */
	@PostMapping("/drifts/apply-all")
	public BulkApplyResponse applyAllOpen() {
		return bulkApplyService.applyAll(bulkApplyService.openTargets());
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
		redirectAttributes.addFlashAttribute("flashMessage", "자원 중복을 해결했습니다");
		return new RedirectView("/maintenance/reconciliation");
	}

	/**
	 * MK4-1 — 종전 '보고 닫기' 를 대신하는 '보관'. 보관 기간(또는 조건)과 사유를 받는다.
	 *
	 * <p>{@code BindingResult} 를 받지 않는다 — 받으면 검증 실패를 이 메서드가 분기로 처리해야 하고,
	 * 그 분기는 필드가 늘 때마다 같이 자란다. 대신 Spring 이 {@code MethodArgumentNotValidException}
	 * 을 던지게 두고 {@code ApiExceptionHandler} 가 필드 오류를 담은 400 으로 옮긴다.</p>
	 */
	@PostMapping("/drifts/{driftId}/snooze")
	public RedirectView snooze(
			@PathVariable Long driftId,
			@Valid @ModelAttribute DriftSnoozeRequest request,
			RedirectAttributes redirectAttributes
	) {
		reconciliationService.snooze(driftId, request.window(), request.reason());
		redirectAttributes.addFlashAttribute("flashMessage",
				request.window().getLabel() + " 보관했습니다");
		return new RedirectView("/maintenance/reconciliation");
	}

	/**
	 * MK4-4-3 — 보관을 앞당겨 푼다. 기간 만료를 기다리지 않고 지금 처리하겠다는 뜻이다.
	 *
	 * <p>거절은 보관 중이 아닌 것을 풀려 할 때뿐이고, 보관 목록에만 버튼이 있으므로 정상 흐름에서는
	 * 도달하지 않는다({@code Drift.unsnoozeBlockReason} 이 화면과 서버 가드의 단일 소스).</p>
	 */
	@PostMapping("/drifts/{driftId}/unsnooze")
	public RedirectView unsnooze(@PathVariable Long driftId, RedirectAttributes redirectAttributes) {
		reconciliationService.unsnooze(driftId);
		redirectAttributes.addFlashAttribute("flashMessage", "보관을 해제했습니다");
		return new RedirectView("/maintenance/reconciliation/snoozed");
	}
}
