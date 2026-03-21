package com.example.serverprovision.application.admin.controller;

import com.example.serverprovision.domain.os.dto.OSMetadataCreateDTO;
import com.example.serverprovision.domain.os.dto.OSMetadataUpdateDTO;
import com.example.serverprovision.domain.os.service.OSMetadataService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

@Slf4j
@Controller
@RequiredArgsConstructor
@RequestMapping("/pxe/v1/admin/os")
public class OSAdminController {

    private final OSMetadataService osMetadataService;

    // OS 목록 조회 화면
    @GetMapping
    public String osMetadataList(Model model) {
        model.addAttribute("osList", osMetadataService.getAllOSMetadata());
        return "admin/os/os-list";
    }

    @GetMapping("/{id}")
    public String osMetadataDetail(@PathVariable Long id, Model model) {
        model.addAttribute("os", osMetadataService.getOSMetadataById(id));
        return "admin/os/os-detail";
    }

    @GetMapping("/{id}/edit")
    public String editOSMetadataForm(@PathVariable Long id, Model model) {
        model.addAttribute("os", osMetadataService.getOSMetadataById(id));
        return "admin/os/os-edit";
    }

    @PostMapping("/{id}/edit")
    public String updateOSMetadata(@PathVariable Long id, @RequestBody OSMetadataUpdateDTO updateDTO) {
        osMetadataService.saveOSMetadata(updateDTO);
        return "redirect:/pxe/v1/admin/os";
    }

    @GetMapping("/new")
    public String newOSMetadataForm() {
        return "admin/os/os-new";
    }

    // 신규 OS 정보 등록 처리
    @PostMapping("/new")
    public String createOSMetadata(@RequestBody OSMetadataCreateDTO osMetadataCreateDTO) {
        osMetadataService.saveOSMetadata(osMetadataCreateDTO);
        return "redirect:/pxe/v1/admin/os";
    }

    // 사용 여부(Active) 상태 변경
    @PostMapping("/{id}/toggle")
    public String toggleOSActive(@PathVariable Long id) {
        osMetadataService.toggleActive(id);
        return "redirect:/pxe/v1/admin/os";
    }
}
