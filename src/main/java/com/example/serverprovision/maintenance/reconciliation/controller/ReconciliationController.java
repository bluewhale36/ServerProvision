package com.example.serverprovision.maintenance.reconciliation.controller;

import com.example.serverprovision.global.orphan.service.OrphanQuarantineService;
import com.example.serverprovision.maintenance.reconciliation.dto.response.DriftReportResponse;
import com.example.serverprovision.maintenance.reconciliation.enums.ScanDepth;
import com.example.serverprovision.maintenance.reconciliation.service.DriftBulkApplyService;
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
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;


/**
 * MK1 자원 무결성 점검 페이지 컨트롤러.
 * <p>이력은 페이지네이션으로 조회. 가장 최근 보고서가 첫 페이지의 첫 행.</p>
 *
 * <p>본 컨트롤러는 페이지 렌더 전용. REST 트리거(scan/apply/dismiss) 는
 * {@link ReconciliationRestController} 에 분리.</p>
 *
 * <h3>MK4-4-2 — 정보구조 개편</h3>
 * <p>첫 화면의 주인공이 점검 회차에서 <b>지금 남은 드리프트</b>로 바뀌었다. 배치 후보 둘을 실제
 * 화면으로 만들어 견준 뒤 이쪽을 채택했고, 비교에 쓰던 질의 파라미터와 후보 템플릿은 함께
 * 지웠다. 회차 상세({@code /reports/{id}})와 드리프트 상세({@code /drifts/{id}})는 그때 만든
 * 화면이 그대로 남은 것이다.</p>
 */
@Controller
@RequestMapping("/maintenance/reconciliation")
@RequiredArgsConstructor
public class ReconciliationController {

	private static final int DEFAULT_PAGE_SIZE = 20;
	private static final String BASE_PATH = "/maintenance/reconciliation";

	private final PathReconciliationService reconciliationService;
	private final DriftBulkApplyService bulkApplyService;
	private final OrphanQuarantineService orphanQuarantineService;
	private final ReconciliationScheduler scheduler;

	/**
	 * 첫 화면 — 지금 남은 드리프트가 급한 순으로 놓인다.
	 *
	 * <p>회차 목록은 여기서 쓰지 않는다. 마지막 점검이 언제였는지만 머리에 적고, 회차를 훑는 일은
	 * {@link #reportList} 로 넘겼다 — 운영자가 실제로 하는 일이 이력 탐색이 아니라 미해결 처리라는
	 * 진단에 화면을 맞춘 결과다(MK4-4-2).</p>
	 */
	@GetMapping
	public String list(Model model) {
		// R9 최종 리뷰 — 목록의 첫 행이 아니라 전체 최신 1 건. 화면 머리의 "마지막 점검" 이
		// 페이지를 넘길 때마다 달라지던 결함이 여기서 사라졌다.
		model.addAttribute("latestReport", reconciliationService.latestReport().orElse(null));
		// MK4-1 — 미해결 수와 목록은 회차가 아니라 현재 열린 드리프트에서 온다. 지난 회차를 열어도
		// 그 회차의 사실이 그대로 남고, 이 목록만 "지금 남은 것" 을 말한다.
		model.addAttribute("openDrifts", reconciliationService.openDrifts());
		// MK4-4-2 — [전체 해결] 대상 수. 버튼에 몇 건이 걸리는지 미리 밝힌다.
		model.addAttribute("openBulkTargetCount", bulkApplyService.openTargets().size());

		addCommonModel(model);
		return "maintenance/reconciliation/list";
	}

	/**
	 * MK4-4-2 — 점검 이력 목록(별도 화면).
	 *
	 * <p>이력은 첫 화면의 자리를 미해결 목록에 내주고 여기로 밀렸다. 가끔 들어와 "지난번 이후 무슨
	 * 일이 있었나" 를 볼 때 쓴다.</p>
	 *
	 * <p>경로가 계획서의 {@code /history} 가 아닌 이유는 그 자리를 이미
	 * {@link ReconciliationRestController#history} 가 쓰고 있어서다. 같은 경로에 GET 을 하나 더
	 * 걸면 매핑이 모호해져 기동이 실패한다. {@code /reports} 는 아래 회차 상세와 짝이 맞기도 하다.</p>
	 */
	@GetMapping("/reports")
	public String reportList(
			@RequestParam(name = "page", defaultValue = "0") int page,
			@RequestParam(name = "size", defaultValue = "20") int size,
			Model model
	) {
		Page<DriftReportResponse> reportPage = reconciliationService.history(pageable(page, size));
		model.addAttribute("reports", reportPage.getContent());
		model.addAttribute("page", reportPage.getNumber());
		model.addAttribute("size", reportPage.getSize());
		model.addAttribute("totalPages", Math.max(reportPage.getTotalPages(), 1));
		model.addAttribute("totalElements", reportPage.getTotalElements());

		addCommonModel(model);
		model.addAttribute("backHref", BASE_PATH);
		return "maintenance/reconciliation/report-list";
	}

	/**
	 * MK4-4-2 — 회차 상세. 그 점검이 언제 무엇을 어디까지 봤고 무엇이 나왔는지.
	 */
	@GetMapping("/reports/{reportId}")
	public String reportDetail(
			@PathVariable Long reportId,
			Model model
	) {
		model.addAttribute("report", reconciliationService.report(reportId));
		// 이 회차에서 지금 해결할 수 있는 것이 몇 건인가. 0 이면 [전체 해결] 을 누를 이유가 없다.
		model.addAttribute("bulkTargetCount", bulkApplyService.targetsInReport(reportId).size());

		addCommonModel(model);
		// 회차는 이력 화면에서 들어오므로 그리로 돌아간다. 실제 되돌아갈 곳은 화면 이력 스택이
		// 정하며 이 값은 직접 진입 · 새로고침의 fallback 이다.
		model.addAttribute("backHref", BASE_PATH + "/reports");
		return "maintenance/reconciliation/report-detail";
	}

	/**
	 * MK4-4-2 — 드리프트 상세. 종전 Miller 3 번째 칸의 내용이 자기 화면을 갖는다.
	 */
	@GetMapping("/drifts/{driftId}")
	public String driftDetail(
			@PathVariable Long driftId,
			Model model
	) {
		model.addAttribute("drift", reconciliationService.drift(driftId));
		// MK4-4-2 — 이 드리프트에 무슨 일이 있었나. 관측 · 처리 · 전임 · 후임이 한 시간축에 놓인다.
		// 기록은 MK4-1 부터 쌓이고 있었는데 화면이 한 번도 꺼내지 않아, 운영자가 "이 드리프트가
		// 언제부터 어떻게 흘러왔는지" 를 확인할 자리가 없었다.
		model.addAttribute("timeline", reconciliationService.timelineOf(driftId));

		addCommonModel(model);
		// 어디서 왔는지는 주소에 싣지 않는다. 그 경로를 파라미터로 옮기면 U3-4 가 폐기한
		// 방식(returnTo)이 되살아난다 — 겹이 깊어지면 표현할 자리가 없고, 짚을 행의 키가 남의
		// 목록으로 넘어간다. 실제 되돌아갈 곳은 화면 이력 스택이 알고 이 값은 fallback 이다.
		model.addAttribute("backHref", BASE_PATH);
		return "maintenance/reconciliation/drift-detail";
	}

	// ==== 공통 조립 ====================================================

	private static Pageable pageable(int page, int size) {
		int safeSize = (size <= 0 || size > 100) ? DEFAULT_PAGE_SIZE : size;
		return PageRequest.of(Math.max(page, 0), safeSize, Sort.by(Sort.Direction.DESC, "scannedAt"));
	}

	/**
	 * 어느 화면에서든 필요한 것들 — 지금 남은 문제의 수, 이번 점검이 내용까지 봤는지, 시스템 해결이
	 * 켜져 있는지. 화면마다 다시 담으면 한 곳을 고칠 때 나머지가 조용히 뒤처진다.
	 *
	 */
	private void addCommonModel(Model model) {
		// R9-2 — 전역 자동 적용 OFF 시 UI 가 버튼을 disabled+tooltip 으로 1차 차단.
		// 서버 가드(apply)와 같은 isResolutionEnabled() 를 공유 — SSOT.
		model.addAttribute("resolutionEnabled", reconciliationService.isResolutionEnabled());
		model.addAttribute("openDriftCount", reconciliationService.openDriftCount());
		// MK4-2 — 이번 점검이 파일 내용을 보았는지와 마지막으로 본 시각. 일반 점검은 내용을 보지 않아
		// 내용에 관한 문제가 목록에서 빠지는데, 화면이 이를 알리지 않으면 해결된 것으로 읽힌다.
		model.addAttribute("scanCoverage", reconciliationService.scanCoverage());
		// MK4-3-2 — "언제 이후로 내용을 안 봤는지" 다음에 "언제 다시 볼지" 가 온다. 기록이 없으면
		// null 이고 그때는 밀려 있다는 뜻이라 곧 돈다.
		model.addAttribute("nextDeepScanAt", scheduler.nextDueAt(ScanDepth.DEEP).orElse(null));
		// R9-4 — 업로드 실패 격리 대기 안내 배너 (이 페이지 렌더에만 count 조회).
		model.addAttribute("quarantinePendingCount", orphanQuarantineService.countPending());
	}

}
