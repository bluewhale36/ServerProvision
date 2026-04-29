package com.example.serverprovision.maintenance.reconciliation.controller;

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
 * MK1 경로 재조정 페이지 컨트롤러 (Miller Columns).
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

    @GetMapping
    public String list(@RequestParam(name = "page", defaultValue = "0") int page,
                       @RequestParam(name = "size", defaultValue = "20") int size,
                       @RequestParam(name = "selectReportId", required = false) Long selectReportId,
                       @RequestParam(name = "selectDriftId", required = false) Long selectDriftId,
                       Model model) {
        int safeSize = (size <= 0 || size > 100) ? DEFAULT_PAGE_SIZE : size;
        Pageable pageable = PageRequest.of(Math.max(page, 0), safeSize,
                Sort.by(Sort.Direction.DESC, "scannedAt"));
        Page<DriftReportResponse> reportPage = reconciliationService.history(pageable);

        List<DriftReportResponse> reports = reportPage.getContent();
        model.addAttribute("reports", reports);
        model.addAttribute("page", reportPage.getNumber());
        model.addAttribute("size", reportPage.getSize());
        model.addAttribute("totalPages", Math.max(reportPage.getTotalPages(), 1));
        model.addAttribute("totalElements", reportPage.getTotalElements());
        model.addAttribute("selectReportId", selectReportId);
        model.addAttribute("selectDriftId", selectDriftId);
        model.addAttribute("latestReport", reports.isEmpty() ? null : reports.get(0));

        return "maintenance/reconciliation/list";
    }
}
