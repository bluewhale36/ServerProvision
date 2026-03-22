package com.example.serverprovision.application.setting.controller;

import com.example.serverprovision.application.setting.model.enums.SettingProcessStep;
import com.example.serverprovision.application.setting.service.SettingService;
import com.example.serverprovision.domain.board.service.BoardModelService;
import com.example.serverprovision.domain.os.service.OSMetadataService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

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
        model.addAttribute("settingProcessStepList", List.of(SettingProcessStep.values()));
        model.addAttribute("boardModelList", boardModelService.getAllBoardModel());
        model.addAttribute("osList", osMetadataService.getAllActiveOSMetadata());
        return "setting/new";
    }

}
