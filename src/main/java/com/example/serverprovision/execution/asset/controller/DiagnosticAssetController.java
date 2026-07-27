package com.example.serverprovision.execution.asset.controller;

import com.example.serverprovision.execution.asset.enums.DiagnosticAsset;
import com.example.serverprovision.execution.asset.exception.DiagnosticAssetSlotNotFoundException;
import com.example.serverprovision.execution.asset.service.DiagnosticAssetActivationService;
import com.example.serverprovision.execution.asset.service.DiagnosticAssetIntegrityService;
import com.example.serverprovision.execution.asset.service.DiagnosticAssetVersionService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * 진단 리눅스 자산의 <b>상세</b> 화면(교체·롤백·이력). 현황 대시보드·운영 설정·전역 봉인은 E1-I-3-a 에서 여러
 * 자원 종류를 함께 집계하는 통합 시스템 자산 화면({@code /system/asset})으로 승격돼 이 컨트롤러에서 빠졌다 —
 * 진단 상세는 여전히 진단 고유의 슬롯 URL 네임스페이스({@code /system/diagnostic-asset/{slot}})에 남는다.
 *
 * <ul>
 *   <li>{@code GET  /system/diagnostic-asset}                            — 구 대시보드 URL 보존 → {@code /system/asset} 리다이렉트</li>
 *   <li>{@code GET  /system/diagnostic-asset/{slot}}                     — 자산 상세(교체·롤백·이력)</li>
 *   <li>{@code POST /system/diagnostic-asset/{slot}/replace}            — 업로드 교체. → 상세 PRG</li>
 *   <li>{@code POST /system/diagnostic-asset/{slot}/versions/{id}/rollback} — 버전 롤백. → 상세 PRG</li>
 * </ul>
 *
 * <p>{@code GET ""}(리터럴)과 {@code GET "/{slot}"}(경로변수)은 세그먼트 수가 달라 매칭이 겹치지 않는다.</p>
 */
@Controller
@RequestMapping("/system/diagnostic-asset")
@RequiredArgsConstructor
public class DiagnosticAssetController {

    private final DiagnosticAssetIntegrityService integrityService;
    private final DiagnosticAssetActivationService activationService;
    private final DiagnosticAssetVersionService versionService;

    // ── 조회 화면 ────────────────────────────────────────────────────────────

    /** 구 진단 대시보드 URL 보존 — 현황은 통합 시스템 자산 화면으로 옮겨갔다(즐겨찾기·기존 링크 무해화). */
    @GetMapping
    public String dashboard() {
        return "redirect:/system/asset";
    }

    @GetMapping("/{slot}")
    public String detail(@PathVariable("slot") String slotKey, Model model) {
        DiagnosticAsset slot = parseSlot(slotKey);   // 없는 슬롯 이름(forging) → 404
        model.addAttribute("slot", integrityService.loadSlot(slot));         // 이 슬롯 하나만 해시(6종 전량 재해시 회피)
        model.addAttribute("versions", versionService.listVersions(slot));   // 이 슬롯 이력만 조회
        model.addAttribute("servingActive", integrityService.isServing());
        return "system/diagnostic-asset/detail";
    }

    // ── 액션(PRG) ────────────────────────────────────────────────────────────

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

    /** 경로변수를 고정 슬롯으로 해석. 없는 이름(forging)은 404. */
    private static DiagnosticAsset parseSlot(String slotKey) {
        try {
            return DiagnosticAsset.valueOf(slotKey);
        } catch (IllegalArgumentException e) {
            throw DiagnosticAssetSlotNotFoundException.of(slotKey);
        }
    }
}
