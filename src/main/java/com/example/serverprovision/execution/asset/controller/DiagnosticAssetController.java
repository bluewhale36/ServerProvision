package com.example.serverprovision.execution.asset.controller;

import com.example.serverprovision.execution.asset.dto.SealResult;
import com.example.serverprovision.execution.asset.enums.DiagnosticAsset;
import com.example.serverprovision.execution.asset.exception.DiagnosticAssetSlotNotFoundException;
import com.example.serverprovision.execution.asset.service.DiagnosticAssetActivationService;
import com.example.serverprovision.execution.asset.service.DiagnosticAssetIntegrityService;
import com.example.serverprovision.execution.asset.service.DiagnosticAssetVersionService;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * 진단 리눅스 자산 화면(E1-I-2-b-2 재구성). 세 화면으로 분리한다 — 현황만 보는 <b>대시보드</b>, 한 자산의 액션을
 * 모은 <b>상세</b>, 전역 튜너블인 <b>운영 설정</b>. 인증·역할 체계가 없어 접근 격리는 전용 URL 네임스페이스
 * {@code /system/**} 로 구현한다.
 *
 * <ul>
 *   <li>{@code GET  /system/diagnostic-asset}                            — 대시보드(현황만, 행 클릭 → 상세)</li>
 *   <li>{@code GET  /system/diagnostic-asset/{slot}}                     — 자산 상세(교체·롤백·이력)</li>
 *   <li>{@code GET  /system/diagnostic-asset/settings}                  — 운영 설정(보존 개수 + 무결성 봉인)</li>
 *   <li>{@code POST /system/diagnostic-asset/settings}                  — 보존 개수 변경. self-PRG</li>
 *   <li>{@code POST /system/diagnostic-asset/seal}                      — 전 슬롯 무결성 봉인. → 설정 PRG</li>
 *   <li>{@code POST /system/diagnostic-asset/recheck}                   — 재검증(비변경 새로고침). → 대시보드 PRG</li>
 *   <li>{@code POST /system/diagnostic-asset/{slot}/replace}            — 업로드 교체. → 상세 PRG</li>
 *   <li>{@code POST /system/diagnostic-asset/{slot}/versions/{id}/rollback} — 버전 롤백. → 상세 PRG</li>
 * </ul>
 *
 * <p>{@code @GetMapping("/settings")}(리터럴)이 {@code @GetMapping("/{slot}")}(경로변수)보다 우선 매칭되므로
 * {@code settings} 가 슬롯 이름으로 새지 않는다.</p>
 */
@Controller
@RequestMapping("/system/diagnostic-asset")
@RequiredArgsConstructor
public class DiagnosticAssetController {

    private final DiagnosticAssetIntegrityService integrityService;
    private final DiagnosticAssetActivationService activationService;
    private final DiagnosticAssetVersionService versionService;
    private final AssetHistorySettingsService settingsService;

    // ── 조회 화면 ────────────────────────────────────────────────────────────

    @GetMapping
    public String dashboard(Model model) {
        model.addAttribute("dashboard", integrityService.loadDashboard());   // 현황만
        return "system/diagnostic-asset/dashboard";
    }

    @GetMapping("/{slot}")
    public String detail(@PathVariable("slot") String slotKey, Model model) {
        DiagnosticAsset slot = parseSlot(slotKey);   // 없는 슬롯 이름(forging) → 404
        model.addAttribute("slot", integrityService.loadSlot(slot));         // 이 슬롯 하나만 해시(6종 전량 재해시 회피)
        model.addAttribute("versions", versionService.listVersions(slot));   // 이 슬롯 이력만 조회
        model.addAttribute("servingActive", integrityService.isServing());
        return "system/diagnostic-asset/detail";
    }

    @GetMapping("/settings")
    public String settings(Model model) {
        model.addAttribute("retentionForm", new AssetHistorySettingsRequest(settingsService.getRetentionCount()));
        populateSettingsContext(model);
        return "system/diagnostic-asset/settings";
    }

    // ── 액션(PRG) ────────────────────────────────────────────────────────────

    @PostMapping("/settings")
    public String updateSettings(@Valid @ModelAttribute("retentionForm") AssetHistorySettingsRequest form,
                                 BindingResult binding, RedirectAttributes redirectAttributes, Model model) {
        if (binding.hasErrors()) {
            populateSettingsContext(model);   // 폼 값(retentionForm)은 @ModelAttribute 로 이미 모델에 있다
            return "system/diagnostic-asset/settings";
        }
        settingsService.update(form.retentionCount());
        redirectAttributes.addFlashAttribute("flashMessage",
                "이력 보존 개수를 " + form.retentionCount() + "개로 변경했습니다.");
        return "redirect:/system/diagnostic-asset/settings";
    }

    @PostMapping("/seal")
    public String seal(RedirectAttributes redirectAttributes) {
        SealResult result = integrityService.seal();   // 서빙 비활성이면 409 (UI 1차 차단의 안전망)
        String message = "무결성 봉인 완료 — " + result.sealed() + "건 기록"
                + (result.skipped() > 0 ? ", " + result.skipped() + "건 건너뜀(자산 없음)" : "");
        redirectAttributes.addFlashAttribute("flashMessage", message);
        return "redirect:/system/diagnostic-asset/settings";
    }

    @PostMapping("/recheck")
    public String recheck(RedirectAttributes redirectAttributes) {
        // 재검증의 실제 대조는 리다이렉트 후 GET 이 매번 수행한다(전체 검증). 이 액션은 "지금 다시 검증" 이라는
        // 명시적 affordance 와 확인 문구를 준다. 자원 상태를 바꾸지 않는 비변경 새로고침이라 대시보드에 남는다.
        redirectAttributes.addFlashAttribute("flashMessage", "재검증 완료 — 현재 디스크 상태로 무결성을 다시 대조했습니다.");
        return "redirect:/system/diagnostic-asset";
    }

    @PostMapping("/{slot}/replace")
    public String replace(@PathVariable("slot") String slotKey,
                          @RequestParam(value = "file", required = false) MultipartFile file,
                          RedirectAttributes redirectAttributes) {
        DiagnosticAsset slot = parseSlot(slotKey);   // 없는 슬롯 이름 → 404
        activationService.replace(slot, file);       // 서빙(409)·대상(409)·빈파일(400)·스왑(500) 가드
        redirectAttributes.addFlashAttribute("flashMessage",
                slot.label() + " 교체 완료 — 새 파일로 갱신하고 무결성을 재봉인했습니다.");
        return "redirect:/system/diagnostic-asset/" + slot.name();
    }

    @PostMapping("/{slot}/versions/{versionId}/rollback")
    public String rollback(@PathVariable("slot") String slotKey,
                           @PathVariable("versionId") Long versionId,
                           RedirectAttributes redirectAttributes) {
        DiagnosticAsset slot = parseSlot(slotKey);   // 없는 슬롯 이름 → 404
        activationService.rollback(slot, versionId); // 서빙(409)·대상(409)·없는·타슬롯 버전(404)·스왑(500) 가드
        redirectAttributes.addFlashAttribute("flashMessage",
                slot.label() + " 롤백 완료 — 선택한 버전으로 되돌리고 무결성을 재봉인했습니다.");
        return "redirect:/system/diagnostic-asset/" + slot.name();
    }

    // ── 헬퍼 ─────────────────────────────────────────────────────────────────

    /** 설정 화면 렌더에 필요한 부가 모델(현재 보존값 + 봉인 카드 맥락). GET settings 와 POST 에러 재렌더가 공유. */
    private void populateSettingsContext(Model model) {
        model.addAttribute("retentionSettings", settingsService.current());
        model.addAttribute("dashboard", integrityService.loadDashboard());
    }

    /** 경로변수를 고정 슬롯으로 해석. 없는 이름(forging)은 404. */
    private static DiagnosticAsset parseSlot(String slotKey) {
        try {
            return DiagnosticAsset.valueOf(slotKey);
        } catch (IllegalArgumentException e) {
            throw DiagnosticAssetSlotNotFoundException.of(slotKey);
        }
    }
}
