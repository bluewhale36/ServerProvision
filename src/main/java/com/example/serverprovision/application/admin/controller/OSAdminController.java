package com.example.serverprovision.application.admin.controller;

import com.example.serverprovision.domain.os.dto.OSMetadataCreateDTO;
import com.example.serverprovision.domain.os.dto.OSMetadataDTO;
import com.example.serverprovision.domain.os.dto.OSMetadataUpdateDTO;
import com.example.serverprovision.domain.os.service.OSMetadataService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
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
        // Validation 에러로 인해 다시 뷰로 돌아온 경우가 아닐 때만 기존 DB 데이터를 로드합니다.
        if (!model.containsAttribute("osDto")) {
            OSMetadataDTO metadata = osMetadataService.getOSMetadataById(id);
            OSMetadataUpdateDTO osDto = OSMetadataUpdateDTO.builder()
                    .targetId(metadata.id())
                    .osName(metadata.osName())
                    .osVersion(metadata.osVersion())
                    .isoMountPath(metadata.isoMountPath())
                    .ksTemplatePath(metadata.ksTemplatePath())
                    .isEnabled(metadata.isEnabled())
                    .build();
            model.addAttribute("osDto", osDto);
        }
        return "admin/os/os-edit";
    }

    @PostMapping("/{id}/edit")
    public String updateOSMetadata(@PathVariable Long id, @Valid @ModelAttribute("osDto") OSMetadataUpdateDTO osDto, BindingResult bindingResult, Model model) {
        if (bindingResult.hasErrors()) {
            model.addAttribute("id", id); // 템플릿의 th:action에서 사용할 id 강제 주입
            return "admin/os/os-edit";
        }
        osMetadataService.saveOSMetadata(osDto);
        return "redirect:/pxe/v1/admin/os";
    }

    // GET: 폼 화면 접근 시 빈 DTO 객체를 Model 에 담아 전달해야 th:object 가 동작합니다.
    @GetMapping("/new")
    public String newOSMetadataForm(Model model) {
        model.addAttribute("osDto", OSMetadataCreateDTO.builder().isEnabled(true).build());
        return "admin/os/os-new";
    }

    // POST: 순수 Form Submit 방식. @RequestBody 대신 @ModelAttribute 사용
    @PostMapping("/new")
    public String createOSMetadata(@Valid @ModelAttribute("osDto") OSMetadataCreateDTO osDto, BindingResult bindingResult) {
        // 유효성 검증 실패 시, 에러 정보를 담은 채로 다시 폼 화면을 렌더링합니다.

        if (bindingResult.hasErrors()) {
            return "admin/os/os-new";
        }

        osMetadataService.saveOSMetadata(osDto);
        return "redirect:/pxe/v1/admin/os";
    }

    // 사용 여부(Active) 상태 변경
    @PostMapping("/{id}/toggle")
    public String toggleOSActive(@PathVariable Long id) {
        osMetadataService.toggleActive(id);
        return "redirect:/pxe/v1/admin/os";
    }
}
