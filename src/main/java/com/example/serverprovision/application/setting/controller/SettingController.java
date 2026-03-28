package com.example.serverprovision.application.setting.controller;

import com.example.serverprovision.application.setting.model.SettingProcess;
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

        // OS 설치, 세팅
        model.addAttribute("osList", osMetadataService.getAllActiveOSMetadata());
        model.addAttribute("fileSystems", FileSystem.values());
        return "setting/new";
    }

    // JSON 본문을 받아 jakarta.validation(@Valid)으로 완벽하게 검증합니다.
    @PostMapping("/api/new")
    @ResponseBody
    public ResponseEntity<?> createSettingProcess(@RequestBody SettingProcess settingProcess) {
        log.info("수신된 세팅 프로세스: {}", settingProcess);
        // 에러가 발생하면 @Valid 에 의해 자동으로 400 Bad Request 와 FieldError 가 반환됩니다.
        // settingService.save(settingProcess);
        return ResponseEntity.ok().build();
    }

}
