package com.example.serverprovision.application.setting.controller;

import com.example.serverprovision.application.setting.dto.SettingCreateRequest;
import com.example.serverprovision.application.setting.model.enums.SettingProcessStep;
import com.example.serverprovision.application.setting.service.SettingService;
import com.example.serverprovision.domain.board.service.BoardModelService;
import com.example.serverprovision.domain.os.model.enums.FileSystem;
import com.example.serverprovision.domain.os.service.OSMetadataService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@Controller
@RequiredArgsConstructor
@RequestMapping("/pxe/v1/setting")
public class SettingController {

    private final SettingService settingService;
    private final BoardModelService boardModelService;
    private final OSMetadataService osMetadataService;

    @GetMapping("/new")
    public String newSetting(Model model) {

        // 전체 설정 프로세스
        model.addAttribute("settingProcessStepList", List.of(SettingProcessStep.values()));

        // BIOS/BMC 업데이트
        model.addAttribute("boardViewList", boardModelService.getViewModelList());

        // OS 설치, 세팅 — 환경·패키지 그룹 포함 뷰 데이터
        model.addAttribute("osInstallationViewList", osMetadataService.getInstallationViewList());
        model.addAttribute("fileSystems", FileSystem.values());
        return "setting/new";
    }

    @PostMapping("/api/new")
    @ResponseBody
    public ResponseEntity<?> createSetting(@Valid @RequestBody SettingCreateRequest request) {
        log.info("[SettingController] 세팅 주문서 수신. name={}, 단계 수={}", request.name(), request.processList().size());
        settingService.save(request);
        return ResponseEntity.ok().build();
    }

}
