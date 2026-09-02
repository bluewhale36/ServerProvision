package com.example.serverprovision.provisioning.setting.controller;

import com.example.serverprovision.provisioning.setting.enums.VdAccessPolicy;
import com.example.serverprovision.provisioning.setting.enums.VdBackgroundInit;
import com.example.serverprovision.provisioning.setting.enums.VdDriveCache;
import com.example.serverprovision.provisioning.setting.enums.VdInitialization;
import com.example.serverprovision.provisioning.setting.enums.VdIoPolicy;
import com.example.serverprovision.provisioning.setting.enums.VdReadPolicy;
import com.example.serverprovision.provisioning.setting.enums.VdStripSize;
import com.example.serverprovision.provisioning.setting.enums.VdWritePolicy;
import com.example.serverprovision.provisioning.setting.dto.request.AbstractProcessRequest;
import com.example.serverprovision.provisioning.setting.dto.request.LinuxInstallationRequest;
import com.example.serverprovision.provisioning.setting.dto.request.RootPasswordRequest;
import com.example.serverprovision.provisioning.setting.dto.request.UserRequest;
import com.example.serverprovision.provisioning.setting.dto.response.SettingDetailResponse;
import com.example.serverprovision.management.raidcard.enums.RaidLevel;
import com.example.serverprovision.provisioning.setting.dto.request.VolumePriorityRuleRequest;
import com.example.serverprovision.provisioning.setting.enums.CapacityOrder;
import com.example.serverprovision.provisioning.setting.enums.DiskCapacityUnit;
import com.example.serverprovision.provisioning.setting.enums.DiskGroupRole;
import com.example.serverprovision.provisioning.setting.enums.DiskCountMode;
import com.example.serverprovision.provisioning.setting.enums.DiskTransportRequirement;
import com.example.serverprovision.provisioning.setting.enums.DiskTypeRequirement;
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
import org.springframework.web.bind.annotation.RequestParam;

import java.util.LinkedHashMap;
import java.util.Arrays;
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
    public String list(
            @RequestParam(name = "includeDeleted", defaultValue = "false") boolean includeDeleted,
            Model model
    ) {
        // U3-2-b — 활성 전용이 기본, includeDeleted 면 삭제분 포함(휴지통 토글, os 선례 DEC-F).
        model.addAttribute("settings", settingQueryService.findAll(includeDeleted));
        model.addAttribute("includeDeleted", includeDeleted);
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
        // R11 D-R4 — 노출 여부의 SSOT 는 enum 속성. 컨트롤러는 필터만 한다.
        model.addAttribute("processTypes", Arrays.stream(SettingProcessType.values())
                .filter(SettingProcessType::isPaletteExposed).toList());
        model.addAttribute("boardOptions", settingQueryService.findBoardOptions());
        model.addAttribute("osOptions", settingQueryService.findOSOptions());
        model.addAttribute("biosTemplateOptions", settingQueryService.findBiosTemplateOptions());
        model.addAttribute("timezoneOptions", settingQueryService.findTimezoneOptions());
        model.addAttribute("fileSystems", List.of(FileSystem.values()));
        model.addAttribute("sizeUnits", List.of(SizeUnit.values()));
        // U4-1-1 — 디스크 묶음 규칙 · RAID 카드 선택지. 레벨 최소치(RaidLevel.minimumDisks)는 옵션 data-* 로 폼에 내려간다.
        var raidCardOptions = settingQueryService.findRaidCardOptions();
        model.addAttribute("raidCardOptions", raidCardOptions);
        // 판정 재료를 JSON 문자열로도 내린다 — Thymeleaf 의 JS inline 직렬화는 Jackson 2 만 감지해 record 를 {} 로 만들므로
        // initialSettingJson 과 같이 Boot ObjectMapper(Jackson 3)로 서버가 직렬화한 문자열을 넘긴다.
        model.addAttribute("raidCardMetaJson", objectMapper.writeValueAsString(raidCardOptions));
        model.addAttribute("raidLevels", List.of(RaidLevel.values()));
        model.addAttribute("diskTypes", List.of(DiskTypeRequirement.values()));
        model.addAttribute("diskTransports", List.of(DiskTransportRequirement.values()));
        model.addAttribute("diskCapacityUnits", List.of(DiskCapacityUnit.values()));
        model.addAttribute("diskCountModes", List.of(DiskCountMode.values()));
        // U4-1-2 — 역할 · 우선순위 선택지. 기본 우선순위 행은 파라미터가 없어 endpoint 대신 페이지에 한 번 싣는다(D6) —
        // 폼 첫 채움과 '기본 행으로 되돌리기' 가 같은 값을 읽는다. SSOT = VolumePriorityRuleRequest.defaults().
        model.addAttribute("diskGroupRoles", List.of(DiskGroupRole.values()));
        // E3.5-6 — VD 파라미터 8축 선택지(HII 항목 순서). 값 어휘의 SSOT 는 각 enum(cliToken).
        model.addAttribute("vdStripSizes", List.of(VdStripSize.values()));
        model.addAttribute("vdReadPolicies", List.of(VdReadPolicy.values()));
        model.addAttribute("vdWritePolicies", List.of(VdWritePolicy.values()));
        model.addAttribute("vdIoPolicies", List.of(VdIoPolicy.values()));
        model.addAttribute("vdAccessPolicies", List.of(VdAccessPolicy.values()));
        model.addAttribute("vdDriveCaches", List.of(VdDriveCache.values()));
        model.addAttribute("vdBackgroundInits", List.of(VdBackgroundInit.values()));
        model.addAttribute("vdInitializations", List.of(VdInitialization.values()));
        model.addAttribute("capacityOrders", List.of(CapacityOrder.values()));
        model.addAttribute("defaultVolumePrioritiesJson", objectMapper.writeValueAsString(VolumePriorityRuleRequest.defaults()));
        // U4-1-3 — OS 설치 카드의 대상 볼륨 안내 문구(네 분기 + 용량 줄)는 서버가 SSOT — 폼 JS 는 템플릿만 받아 채운다(CP5 F-1).
        model.addAttribute("osVolumeTargetMessagesJson", objectMapper.writeValueAsString(
                com.example.serverprovision.provisioning.setting.dto.response.OsVolumeTarget.messageTemplates()));
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
