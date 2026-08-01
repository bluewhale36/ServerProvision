package com.example.serverprovision.execution.asset.controller;

import com.example.serverprovision.execution.asset.dto.SealResult;
import com.example.serverprovision.execution.asset.service.SealedFileInspector;
import com.example.serverprovision.execution.asset.service.SystemAssetDashboardService;
import com.example.serverprovision.global.history.AssetHistorySettingsService;
import com.example.serverprovision.global.history.dto.request.AssetHistorySettingsRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * 통합 시스템 자산 화면(E1-I-3-a). 격리된 시스템 자산 영역(진단 리눅스·TFTP)을 한 대시보드로 묶는다 —
 * 영역별 슬롯 현황·집계·봉인을 여기서 다루고, 자산 하나의 교체·롤백·이력 같은 상세 액션은 각 영역의 기존
 * 상세 경로({@code /system/diagnostic-asset/{slot}})에 그대로 남는다(회귀 안전망 보존).
 *
 * <ul>
 *   <li>{@code GET  /system/asset}                — 통합 대시보드(영역별 표 + 집계, 봉인 버튼)</li>
 *   <li>{@code GET  /system/asset/settings}       — 운영 설정(이력 보존 개수 + 영역별 봉인 카드)</li>
 *   <li>{@code POST /system/asset/settings}       — 보존 개수 변경. self-PRG</li>
 *   <li>{@code POST /system/asset/{area}/seal}    — 영역 단위 무결성 봉인. → 대시보드 PRG</li>
 *   <li>{@code POST /system/asset/recheck}        — 재검증(비변경 새로고침). → 대시보드 PRG</li>
 * </ul>
 *
 * <p>봉인만 영역 인자를 받아 다중화한다. 없는 영역 키(주소 위조)는 서비스가 404, 봉인 미지원·서빙 비활성은
 * 409 로 거절하며 전역 advice 가 응답으로 변환한다 — 컨트롤러는 분기하지 않는다.</p>
 *
 * <p>{@code @GetMapping("/settings")}(리터럴)·{@code POST /settings}·{@code POST /recheck} 는 단일 세그먼트라
 * 2 세그먼트 {@code /{area}/seal} 와 매칭이 겹치지 않는다.</p>
 */
@Controller
@RequestMapping("/system/asset")
@RequiredArgsConstructor
public class SystemAssetController {

    private final SystemAssetDashboardService dashboardService;
    private final AssetHistorySettingsService settingsService;
    private final SealedFileInspector sealedFileInspector;

    // ── 조회 화면 ────────────────────────────────────────────────────────────

    @GetMapping
    public String dashboard(Model model) {
        model.addAttribute("overview", dashboardService.loadOverview());
        return "system/asset/dashboard";
    }

    @GetMapping("/settings")
    public String settings(Model model) {
        model.addAttribute("retentionForm", new AssetHistorySettingsRequest(settingsService.getRetentionCount()));
        populateSettingsContext(model);
        return "system/asset/settings";
    }

    // ── 액션(PRG) ────────────────────────────────────────────────────────────

    @PostMapping("/settings")
    public String updateSettings(@Valid @ModelAttribute("retentionForm") AssetHistorySettingsRequest form,
                                 BindingResult binding, RedirectAttributes redirectAttributes, Model model) {
        if (binding.hasErrors()) {
            populateSettingsContext(model);   // 폼 값(retentionForm)은 @ModelAttribute 로 이미 모델에 있다
            return "system/asset/settings";
        }
        settingsService.update(form.retentionCount());
        redirectAttributes.addFlashAttribute("flashMessage",
                "이력 보존 개수를 " + form.retentionCount() + "개로 변경했습니다.");
        return "redirect:/system/asset/settings";
    }

    @PostMapping("/{area}/seal")
    public String seal(@PathVariable("area") String areaKey, RedirectAttributes redirectAttributes) {
        SealResult result = dashboardService.seal(areaKey);   // 없는 영역 404 / 미지원·미구성 409 (UI 1차 차단의 안전망)
        String message = "무결성 봉인 완료 — " + result.sealed() + "건 기록"
                + (result.skipped() > 0 ? ", " + result.skipped() + "건 건너뜀(자산 없음)" : "");
        redirectAttributes.addFlashAttribute("flashMessage", message);
        return "redirect:/system/asset";
    }

    @PostMapping("/recheck")
    public String recheck(RedirectAttributes redirectAttributes) {
        // 진입 최적화(해시 캐시)로 평상시 대시보드는 안 바뀐 파일의 해시를 재사용한다. 재검증은 그 캐시를 비워
        // 리다이렉트 후 GET 이 전량 재해시하게 만든다 — mtime 을 보존한 변조까지 강제로 다시 잡는다(비변경 액션).
        sealedFileInspector.invalidateHashCache();
        redirectAttributes.addFlashAttribute("flashMessage", "재검증 완료 — 캐시를 비우고 현재 디스크 상태로 무결성을 다시 대조했습니다.");
        return "redirect:/system/asset";
    }

    // ── 헬퍼 ─────────────────────────────────────────────────────────────────

    /** 설정 화면 렌더에 필요한 부가 모델(현재 보존값 + 영역별 봉인 카드 맥락). GET settings 와 POST 에러 재렌더가 공유. */
    private void populateSettingsContext(Model model) {
        model.addAttribute("retentionSettings", settingsService.current());
        model.addAttribute("overview", dashboardService.loadOverview());
    }
}
