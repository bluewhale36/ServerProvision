package com.example.serverprovision.application.admin.controller;

import com.example.serverprovision.application.setting.service.SettingService;
import com.example.serverprovision.domain.node.service.ServerNodeService;
import com.example.serverprovision.domain.os.service.OSMetadataService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequiredArgsConstructor
@RequestMapping("/pxe/v1/admin")
public class AdminController {

    private final ServerNodeService serverNodeService;
    private final OSMetadataService osMetadataService;
    private final SettingService settingService;

    @GetMapping("/dashboard")
    public String dashboard(Model model) {
        model.addAttribute("nodes", serverNodeService.getAllNodes());
        model.addAttribute("settings", settingService.findAll());
        return "admin/dashboard";
    }

    /**
     * 서버 노드에 세팅 주문서를 할당한다.
     *
     * @param nodeId    대상 서버 노드 ID
     * @param settingId 할당할 세팅 주문서 ID
     * @return 대시보드로 리다이렉트
     */
    @PostMapping("/nodes/{nodeId}/assign-setting")
    public String assignSetting(@PathVariable Long nodeId,
                                @RequestParam Long settingId,
                                RedirectAttributes redirectAttributes) {
        try {
            serverNodeService.assignSetting(nodeId, settingId);
            redirectAttributes.addFlashAttribute("successMessage", "세팅 할당이 완료되었습니다.");
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/pxe/v1/admin/dashboard";
    }

    @GetMapping("/nodes/{mac}/settings")
    public String settings(@PathVariable String mac, Model model) {
        model.addAttribute("node", serverNodeService.getNodeByMac(mac));
        // 활성 OS 목록을 Model에 추가하여 Select Box 구성에 사용
        model.addAttribute("activeOsList", osMetadataService.getAllActiveOSMetadata());
        return "admin/settings";
    }
}
