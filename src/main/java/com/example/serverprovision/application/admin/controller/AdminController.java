package com.example.serverprovision.application.admin.controller;

import com.example.serverprovision.domain.node.service.ServerNodeService;
import com.example.serverprovision.domain.os.service.OSMetadataService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequiredArgsConstructor
@RequestMapping("/pxe/v1/admin")
public class AdminController {

    private final ServerNodeService serverNodeService;
    private final OSMetadataService osMetadataService;

    @GetMapping("/dashboard")
    public String dashboard(Model model) {
        model.addAttribute("nodes", serverNodeService.getAllNodes());
        return "admin/dashboard";
    }

    @GetMapping("/nodes/{mac}/settings")
    public String settings(@PathVariable String mac, Model model) {
        model.addAttribute("node", serverNodeService.getNodeByMac(mac));
        // 활성 OS 목록을 Model에 추가하여 Select Box 구성에 사용
        model.addAttribute("activeOsList", osMetadataService.getAllActiveOSMetadata());
        return "admin/settings";
    }
}
