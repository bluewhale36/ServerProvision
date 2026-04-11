package com.example.serverprovision.application.admin.controller;

import com.example.serverprovision.domain.os.dto.OSMetadataCreateDTO;
import com.example.serverprovision.domain.os.dto.OSMetadataDTO;
import com.example.serverprovision.domain.os.dto.OSMetadataUpdateDTO;
import com.example.serverprovision.domain.os.model.enums.OSName;
import com.example.serverprovision.domain.os.service.ExtractionTask;
import com.example.serverprovision.domain.os.service.ExtractionTaskService;
import com.example.serverprovision.domain.os.service.OSMetadataService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Slf4j
@Controller
@RequiredArgsConstructor
@RequestMapping("/pxe/v1/admin/os")
public class OSAdminController {

    private final OSMetadataService osMetadataService;
    private final ExtractionTaskService extractionTaskService;

    // OS 목록 조회 화면 — OSName 기준 그룹핑 + 그룹 내 활성/최신순 정렬
    // selectId 가 전달되면 해당 메타데이터를 밀러 컬럼의 상세 패널에 자동 선택한다.
    @GetMapping
    public String osMetadataList(@RequestParam(required = false) Long selectId, Model model) {
        model.addAttribute("osGroups", osMetadataService.getGroupedOSMetadata());
        model.addAttribute("selectId", selectId);
        return "admin/os/os-list";
    }

    @GetMapping("/{id}")
    public String osMetadataDetail(@PathVariable Long id, Model model) {
        model.addAttribute("os", osMetadataService.getOSMetadataById(id));
        return "admin/os/os-detail";
    }

    // 컨트롤러 내의 모든 뷰에서 "osNames"라는 이름으로 OSName.values()를 접근할 수 있도록 설정
    @ModelAttribute("osNames")
    public OSName[] populateOSNames() {
        return OSName.values();
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

    // 사용 여부(Active) 상태 변경 — 토글 후 동일 메타데이터가 리스트 화면에서 계속 선택되도록 selectId 전달
    @PostMapping("/{id}/toggle")
    public String toggleOSActive(@PathVariable Long id) {
        osMetadataService.toggleActive(id);
        return "redirect:/pxe/v1/admin/os?selectId=" + id;
    }

    // 환경·패키지 그룹 자동 추출 시작 (비동기)
    // 즉시 taskId 를 반환하고, 클라이언트는 상태 엔드포인트로 폴링하여 진행률을 받아간다.
    @PostMapping("/{id}/extract-packages")
    @ResponseBody
    public ResponseEntity<Map<String, String>> startExtractPackages(@PathVariable Long id) {
        String taskId = extractionTaskService.startExtraction(id);
        return ResponseEntity.accepted().body(Map.of("taskId", taskId));
    }

    // 추출 태스크 상태 조회 — 프런트엔드 폴링용
    @GetMapping("/extract-packages/tasks/{taskId}")
    @ResponseBody
    public ResponseEntity<ExtractionTask> getExtractionTaskStatus(@PathVariable String taskId) {
        ExtractionTask task = extractionTaskService.getTask(taskId);
        if (task == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(task);
    }
}
