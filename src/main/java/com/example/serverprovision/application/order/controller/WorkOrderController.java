package com.example.serverprovision.application.order.controller;

import com.example.serverprovision.application.setting.model.enums.SettingStatus;
import com.example.serverprovision.application.setting.service.SettingService;
import com.example.serverprovision.domain.node.service.ServerNodeService;
import com.example.serverprovision.domain.order.service.WorkOrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * 작업 지시서 MVC 컨트롤러이다.
 *
 * <p>역할: 작업 지시서 목록 조회, 생성 폼, 생성 처리, 취소 처리 엔드포인트를 제공한다.</p>
 */
@Controller
@RequiredArgsConstructor
@RequestMapping("/pxe/v1/order")
public class WorkOrderController {

    private final WorkOrderService workOrderService;
    private final ServerNodeService serverNodeService;
    private final SettingService settingService;

    /**
     * 작업 지시서 목록 페이지를 반환한다.
     */
    @GetMapping
    public String list(Model model) {
        model.addAttribute("orders", workOrderService.findAll());
        return "order/order-dashboard";
    }

    /**
     * 작업 지시서 생성 폼 페이지를 반환한다.
     * 노드 목록과 PENDING 상태의 세팅 목록을 모델에 주입한다.
     */
    @GetMapping("/new")
    public String newForm(Model model) {
        model.addAttribute("nodes", serverNodeService.getAllNodes());
        // 전체 세팅 목록에서 PENDING 상태만 필터링
        model.addAttribute("settings", settingService.findAll().stream()
                .filter(s -> s.getStatus() == SettingStatus.PENDING)
                .toList());
        return "order/new";
    }

    /**
     * 작업 지시서를 생성하고 목록 페이지로 리다이렉트한다.
     */
    @PostMapping
    public String create(@RequestParam Long nodeId,
                         @RequestParam Long settingId,
                         @RequestParam(required = false) String memo,
                         RedirectAttributes redirectAttributes) {
        try {
            workOrderService.create(nodeId, settingId, memo);
            redirectAttributes.addFlashAttribute("successMessage", "작업 지시서가 생성되었습니다.");
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/pxe/v1/order";
    }

    /**
     * 작업 지시서를 취소하고 목록 페이지로 리다이렉트한다.
     */
    @PostMapping("/{id}/cancel")
    public String cancel(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        try {
            workOrderService.cancel(id);
            redirectAttributes.addFlashAttribute("successMessage", "작업 지시서가 취소되었습니다.");
        } catch (IllegalArgumentException | IllegalStateException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/pxe/v1/order";
    }
}
