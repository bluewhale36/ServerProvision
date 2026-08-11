package com.example.serverprovision.maintenance.reconciliation.controller;

import com.example.serverprovision.global.orphan.service.OrphanQuarantineService;
import com.example.serverprovision.maintenance.reconciliation.dto.response.DriftReportResponse;
import com.example.serverprovision.maintenance.reconciliation.dto.response.DriftResponse;
import com.example.serverprovision.maintenance.reconciliation.enums.ScanDepth;
import com.example.serverprovision.maintenance.reconciliation.service.PathReconciliationService;
import com.example.serverprovision.maintenance.reconciliation.service.ReconciliationScheduler;
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

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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
	private final ReconciliationScheduler scheduler;

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
		// MK4-1 — 미해결 수와 목록은 보고서가 아니라 현재 열린 문제에서 온다. 지난 보고서를 열어도
		// 그 회차의 사실이 그대로 남고, 요약줄만 "지금 남은 것" 을 말한다.
		model.addAttribute("openDriftCount", reconciliationService.openDriftCount());
		model.addAttribute("openDrifts", reconciliationService.openDrifts());
		// MK4-1 — 상세 패널은 회차가 아니라 '문제' 단위다. 한 문제가 여러 회차에서 관측되면 가운데
		// 목록에는 회차마다 나타나지만 상세는 하나여야 한다. 종전에는 회차마다 별도 drift 행이라
		// 같은 id 가 두 번 나올 수 없었고, 그래서 보고서별로 패널을 찍어도 문제가 없었다.
		model.addAttribute("detailDrifts", distinctDrifts(reports));
		// MK4-2 — 이번 점검이 파일 내용을 보았는지와 마지막으로 본 시각. 일반 점검은 내용을 보지 않아
		// 내용에 관한 문제가 목록에서 빠지는데, 화면이 이를 알리지 않으면 해결된 것으로 읽힌다.
		model.addAttribute("scanCoverage", reconciliationService.scanCoverage());
		// MK4-3-2 — "언제 이후로 내용을 안 봤는지" 다음에 "언제 다시 볼지" 가 온다. 예정 시각이
		// 마지막 정밀 점검 + 주기로 계산되는 구조라 화면이 답할 수 있게 됐다. 기록이 없으면 null 이고
		// 그때는 밀려 있다는 뜻이라 곧 돈다.
		model.addAttribute("nextDeepScanAt",
				scheduler.nextDueAt(ScanDepth.DEEP).orElse(null));
		// R9-4 — 업로드 실패 격리 대기 안내 배너 (이 페이지 렌더에만 count 조회).
		model.addAttribute("quarantinePendingCount", orphanQuarantineService.countPending());

		return "maintenance/reconciliation/list";
	}

	/**
	 * MK4-1 — 이 페이지에 실린 회차들이 관측한 문제를 신원(id) 기준으로 한 번씩만 추린다.
	 *
	 * <p>같은 문제의 관측이 여러 회차에 걸쳐 있어도 상세에 실리는 값은 문제의 현재 상태 하나뿐이라,
	 * 어느 회차의 관측을 통해 왔든 내용이 같다. 그래서 먼저 만난 것을 쓰고 나머지는 버린다
	 * (최신 회차가 앞에 오므로 첫 항목이 가장 최근 관측 경유분이다).</p>
	 */
	private static List<DriftResponse> distinctDrifts(List<DriftReportResponse> reports) {
		Map<Long, DriftResponse> byId = new LinkedHashMap<>();
		for (DriftReportResponse report : reports) {
			for (DriftResponse drift : report.drifts()) {
				byId.putIfAbsent(drift.id(), drift);
			}
		}
		return List.copyOf(byId.values());
	}
}
