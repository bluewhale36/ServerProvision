package com.example.serverprovision.provisioning.biossetting.controller;

import com.example.serverprovision.provisioning.biossetting.service.BiosSettingTemplateQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * BIOS 세팅 템플릿 SSR 페이지. 쓰기(XHR JSON)는 {@link BiosSettingTemplateRestController} 가 담당한다.
 * <ul>
 *   <li>{@code GET /provisioning/bios-setting}                — 템플릿 목록</li>
 *   <li>{@code GET /provisioning/bios-setting/new}            — 보드 선택 랜딩 (BoardModel 실데이터)</li>
 *   <li>{@code GET /provisioning/bios-setting/new/{boardModelId}} — 작성 편집기 (없는 보드/카탈로그 미보유 404)</li>
 *   <li>{@code GET /provisioning/bios-setting/{id}}           — 상세 (재조인 그룹 + Redfish 프리뷰)</li>
 *   <li>{@code GET /provisioning/bios-setting/{id}/edit}      — 수정 편집기 (생성 화면 재사용 + pre-fill)</li>
 * </ul>
 */
@Controller
@RequestMapping("/provisioning/bios-setting")
@RequiredArgsConstructor
public class BiosSettingTemplateController {

    private final BiosSettingTemplateQueryService biosSettingTemplateQueryService;

    @GetMapping
    public String list(Model model) {
        model.addAttribute("templates", biosSettingTemplateQueryService.findAll());
        return "provisioning/bios-setting-list";
    }

    @GetMapping("/new")
    public String boardSelect(Model model) {
        model.addAttribute("boards", biosSettingTemplateQueryService.findBoards());
        return "provisioning/bios-setting-board-select";
    }

    @GetMapping("/new/{boardModelId}")
    public String editor(@PathVariable("boardModelId") Long boardModelId, Model model) {
        model.addAttribute("bios", biosSettingTemplateQueryService.editorView(boardModelId));
        model.addAttribute("boardModelId", boardModelId);
        return "provisioning/bios-setting-editor";
    }

    @GetMapping("/{id}")
    public String detail(@PathVariable("id") Long id, Model model) {
        model.addAttribute("template", biosSettingTemplateQueryService.findDetail(id));
        return "provisioning/bios-setting-detail";
    }

    /** 수정 — 생성과 동일한 편집기 화면에 템플릿 메타 + 저장값 overlay 를 주입한다. */
    @GetMapping("/{id}/edit")
    public String editForm(@PathVariable("id") Long id, Model model) {
        var editView = biosSettingTemplateQueryService.editorViewFor(id);
        model.addAttribute("bios", editView.bios());
        model.addAttribute("boardModelId", editView.boardModelId());
        model.addAttribute("editTemplate", editView);
        return "provisioning/bios-setting-editor";
    }
}
