package com.example.serverprovision.maintenance.reconciliation.controller;

import com.example.serverprovision.global.orphan.service.OrphanQuarantineService;
import com.example.serverprovision.maintenance.reconciliation.dto.response.DriftReportResponse;
import com.example.serverprovision.maintenance.reconciliation.service.PathReconciliationService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

/**
 * MK1 자원 무결성 점검 페이지 컨트롤러 (Miller Columns).
 * <p>이력은 페이지네이션으로 조회. 가장 최근 보고서가 첫 페이지의 첫 행.</p>
 *
 * <p>본 컨트롤러는 페이지 렌더 전용. REST 트리거(scan/apply/dismiss) 는
 * {@link ReconciliationRestController} 에 분리.</p>
 */
@Controller
@RequestMapping("/maintenance/reconciliation")
@RequiredArgsConstructor
public class ReconciliationController {

	private static final int DEFAULT_PAGE_SIZE = 20;

	private final PathReconciliationService reconciliationService;
	private final OrphanQuarantineService orphanQuarantineService;

	@GetMapping
	public String list(
			@RequestParam(name = "page", defaultValue = "0") int page,
			@RequestParam(name = "size", defaultValue = "20") int size,
			@RequestParam(name = "selectReportId", required = false) Long selectReportId,
			@RequestParam(name = "selectDriftId", required = false) Long selectDriftId,
			@RequestParam(name = "selectKey", required = false) Long selectKey,
			@RequestParam(name = "selectId", required = false) Long selectId,
			Model model
	) {
		int safeSize = (size <= 0 || size > 100) ? DEFAULT_PAGE_SIZE : size;
		Pageable pageable = PageRequest.of(
				Math.max(page, 0), safeSize,
				Sort.by(Sort.Direction.DESC, "scannedAt")
		);
		Page<DriftReportResponse> reportPage = reconciliationService.history(pageable);

		List<DriftReportResponse> reports = reportPage.getContent();
		model.addAttribute("reports", reports);
		model.addAttribute("page", reportPage.getNumber());
		model.addAttribute("size", reportPage.getSize());
		model.addAttribute("totalPages", Math.max(reportPage.getTotalPages(), 1));
		model.addAttribute("totalElements", reportPage.getTotalElements());
		// R9-1 — os-list.js 의 Miller URL 동기화 파라미터(selectKey/selectId) alias 수용.
		// 동기화된 URL 로 reload 해도 선택 위치가 보존된다. 명시 파라미터가 우선.
		model.addAttribute("selectReportId", selectReportId != null ? selectReportId : selectKey);
		model.addAttribute("selectDriftId", selectDriftId != null ? selectDriftId : selectId);
		// R9 최종 리뷰 — 현재 페이지 첫 행이 아닌 전체 최신 1건. 2페이지 이후에서 "마지막 점검" 이
		// 그 페이지의 첫 행(더 오래된 보고서)으로 잘못 표시되던 결함 수정.
		model.addAttribute("latestReport", reconciliationService.latestReport().orElse(null));
		// R9-2 — 전역 자동 적용 OFF 시 UI 가 버튼을 disabled+tooltip 으로 1차 차단.
		// 서버 가드(apply)와 같은 isResolutionEnabled() 를 공유 — SSOT.
		model.addAttribute("resolutionEnabled", reconciliationService.isResolutionEnabled());
		// R9-4 — 업로드 실패 격리 대기 안내 배너 (이 페이지 렌더에만 count 조회).
		model.addAttribute("quarantinePendingCount", orphanQuarantineService.countPending());

		return "maintenance/reconciliation/list";
	}
}
