package com.example.serverprovision.application.admin.controller;

import com.example.serverprovision.domain.node.model.enums.JobType;
import com.example.serverprovision.domain.node.service.ServerNodeService;
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

    @GetMapping("/dashboard")
    public String dashboard(Model model) {
        model.addAttribute("nodes", serverNodeService.getAllNodes());
        return "dashboard";
    }

    @GetMapping("/nodes/{mac}/settings")
    public String settings(@PathVariable String mac, Model model) {
        model.addAttribute("node", serverNodeService.getNodeByMac(mac));
        model.addAttribute("jobTypes", JobType.values());
        return "settings";
    }
}
