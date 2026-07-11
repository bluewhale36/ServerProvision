package com.example.serverprovision.provisioning.setting.controller;

import com.example.serverprovision.provisioning.setting.dto.request.AbstractProcessRequest;
import com.example.serverprovision.provisioning.setting.dto.request.LinuxInstallationRequest;
import com.example.serverprovision.provisioning.setting.dto.request.RootPasswordRequest;
import com.example.serverprovision.provisioning.setting.dto.request.UserRequest;
import com.example.serverprovision.provisioning.setting.dto.response.SettingDetailResponse;
import com.example.serverprovision.provisioning.setting.enums.FileSystem;
import com.example.serverprovision.provisioning.setting.enums.SettingProcessType;
import com.example.serverprovision.provisioning.setting.enums.SizeUnit;
import com.example.serverprovision.provisioning.setting.service.SettingQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import tools.jackson.databind.ObjectMapper;

/**
 * 세팅 정의서 SSR 페이지 (사용자 영역). 쓰기(XHR JSON)는 {@link SettingRestController} 가 담당한다.
 * <ul>
 *   <li>{@code GET /provisioning/setting}           — 목록</li>
 *   <li>{@code GET /provisioning/setting/new}       — 작성 폼 (선택지 Model 적재)</li>
 *   <li>{@code GET /provisioning/setting/{id}}      — 상세</li>
 *   <li>{@code GET /provisioning/setting/{id}/edit} — 수정 폼 (initialSettingJson pre-fill)</li>
 * </ul>
 */
@Controller
@RequestMapping("/provisioning/setting")
@RequiredArgsConstructor
public class SettingController {

    private final SettingQueryService settingQueryService;
    private final ObjectMapper objectMapper;

    @GetMapping
    public String list(Model model) {
        model.addAttribute("settings", settingQueryService.findAll());
        return "provisioning/setting-list";
    }

    @GetMapping("/new")
    public String newForm(Model model) {
        addFormOptions(model);
        return "provisioning/setting-new";
    }

    @GetMapping("/{id}")
    public String detail(@PathVariable("id") Long id, Model model) {
        model.addAttribute("setting", settingQueryService.findDetail(id));
        return "provisioning/setting-detail";
    }

    @GetMapping("/{id}/edit")
    public String editForm(@PathVariable("id") Long id, Model model) {
        // PENDING 외 상태의 수정 진입 차단(UI disabled + 서버 가드)은 상태 전이가 실체화되는 U2-2 에서 도입.
        SettingDetailResponse setting = settingQueryService.findDetail(id);
        addFormOptions(model);
        model.addAttribute("setting", setting);
        model.addAttribute("initialSettingJson", buildInitialJson(setting));
        return "provisioning/setting-edit";
    }

    /** 작성/수정 폼 공용 선택지 — 단계 타입·보드/OS 옵션·파티션 입력 보조 enum. */
    private void addFormOptions(Model model) {
        model.addAttribute("processTypes", List.of(SettingProcessType.values()));
        model.addAttribute("boardOptions", settingQueryService.findBoardOptions());
        model.addAttribute("osOptions", settingQueryService.findOSOptions());
        model.addAttribute("biosTemplateOptions", settingQueryService.findBiosTemplateOptions());
        model.addAttribute("timezoneOptions", settingQueryService.findTimezoneOptions());
        model.addAttribute("fileSystems", List.of(FileSystem.values()));
        model.addAttribute("sizeUnits", List.of(SizeUnit.values()));
    }

    /**
     * 수정 폼 pre-fill JSON — 저장된 계약을 그대로 직렬화하되 <b>비밀번호는 서버 밖으로 내보내지 않는다</b>
     * (기존-유지 플래그로 대체). 직렬화 실패는 우리 데이터의 버그이므로 삼키지 않고 전파한다.
     */
    private String buildInitialJson(SettingDetailResponse setting) {
        List<AbstractProcessRequest> patched = setting.processList().stream()
                .map(this::withoutPasswords)
                .toList();
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("name", setting.name());
        payload.put("processList", patched);
        return objectMapper.writeValueAsString(payload);
    }

    private AbstractProcessRequest withoutPasswords(AbstractProcessRequest process) {
        if (!(process instanceof LinuxInstallationRequest linux)) return process;
        List<UserRequest> patchedUsers = linux.getUsers() == null
                ? null
                : linux.getUsers().stream()
                        .map(u -> new UserRequest(u.getUsername(), null, u.getIsSudoer(), false, true))
                        .toList();
        // root 비밀번호 패치는 소유 계층(RHEL)의 withPatchedPasswords 가 스스로 수행한다.
        return linux.withPatchedPasswords(patchedUsers);
    }
}
