package com.example.serverprovision.execution.asset.controller;

import com.example.serverprovision.execution.asset.dto.SealResult;
import com.example.serverprovision.execution.asset.enums.DiagnosticAsset;
import com.example.serverprovision.execution.asset.exception.DiagnosticAssetSlotNotFoundException;
import com.example.serverprovision.execution.asset.service.DiagnosticAssetIntegrityService;
import com.example.serverprovision.execution.asset.service.DiagnosticAssetReplaceService;
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
 * 진단 리눅스 자산 현황·무결성 대시보드(E1-I-1). "교체 전용 시스템 자산" 화면의 조회 절반이다 —
 * 자산 파일을 바꾸는 경로는 없고(교체는 E1-I-2), 무결성 봉인(마커 기록)과 재검증만 쓴다.
 *
 * <p>접근 격리(DEC-14)는 전용 URL 네임스페이스 {@code /system/**} 로 구현한다. 이 앱에는 인증·역할
 * 체계가 없어 격리는 역할 기반이 아니라 구조적(전용 영역 + 교체/삭제 없음)·행위적(강한 확인) 이다.</p>
 * <ul>
 *   <li>{@code GET  /system/diagnostic-asset}          — 대시보드(6 슬롯 현황 + 서빙 상태). 순수 조회</li>
 *   <li>{@code POST /system/diagnostic-asset/seal}     — 무결성 봉인(현재 자산을 신뢰 기준으로 기록). PRG</li>
 *   <li>{@code POST /system/diagnostic-asset/recheck}  — 재검증(디스크 재대조 후 새로고침). PRG</li>
 *   <li>{@code POST /system/diagnostic-asset/{slot}/replace} — 단일 파일 자산 교체(E1-I-2-a). PRG</li>
 * </ul>
 */
@Controller
@RequestMapping("/system/diagnostic-asset")
@RequiredArgsConstructor
public class DiagnosticAssetController {

    private final DiagnosticAssetIntegrityService integrityService;
    private final DiagnosticAssetReplaceService replaceService;

    @GetMapping
    public String dashboard(Model model) {
        model.addAttribute("dashboard", integrityService.loadDashboard());
        return "system/diagnostic-asset/dashboard";
    }

    @PostMapping("/seal")
    public String seal(RedirectAttributes redirectAttributes) {
        SealResult result = integrityService.seal();   // 서빙 비활성이면 409 (UI 1차 차단의 안전망)
        String message = "무결성 봉인 완료 — " + result.sealed() + "건 기록"
                + (result.skipped() > 0 ? ", " + result.skipped() + "건 건너뜀(자산 없음)" : "");
        redirectAttributes.addFlashAttribute("flashMessage", message);
        return "redirect:/system/diagnostic-asset";
    }

    @PostMapping("/recheck")
    public String recheck(RedirectAttributes redirectAttributes) {
        // 재검증의 실제 대조는 리다이렉트 후 GET 이 매번 수행한다(전체 검증). 이 액션은 운영자에게
        // "지금 다시 검증" 이라는 명시적 affordance 와 확인 문구를 준다(reconciliation 의 수동 스캔과 동형).
        redirectAttributes.addFlashAttribute("flashMessage", "재검증 완료 — 현재 디스크 상태로 무결성을 다시 대조했습니다.");
        return "redirect:/system/diagnostic-asset";
    }

    @PostMapping("/{slot}/replace")
    public String replace(@PathVariable("slot") String slotKey,
                          @RequestParam(value = "file", required = false) MultipartFile file,
                          RedirectAttributes redirectAttributes) {
        DiagnosticAsset slot = parseSlot(slotKey);   // 없는 슬롯 이름 → 404
        replaceService.replace(slot, file);          // 서빙(409)·대상(409)·빈파일(400)·스왑(500) 가드
        redirectAttributes.addFlashAttribute("flashMessage",
                slot.label() + " 교체 완료 — 새 파일로 갱신하고 무결성을 재봉인했습니다.");
        return "redirect:/system/diagnostic-asset";
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
