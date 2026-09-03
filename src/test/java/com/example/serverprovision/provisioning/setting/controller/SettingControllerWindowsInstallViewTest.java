package com.example.serverprovision.provisioning.setting.controller;

import com.example.serverprovision.execution.wininstall.vo.WindowsImageName;
import com.example.serverprovision.provisioning.setting.dto.request.WindowsAdministratorPasswordRequest;
import com.example.serverprovision.provisioning.setting.dto.request.WindowsInstallationRequest;
import com.example.serverprovision.provisioning.setting.dto.response.ReferenceNamesResponse;
import com.example.serverprovision.provisioning.setting.dto.response.SettingDetailResponse;
import com.example.serverprovision.provisioning.setting.dto.response.SettingOSOptionGroupResponse;
import com.example.serverprovision.provisioning.setting.dto.response.SettingOSOptionResponse;
import com.example.serverprovision.provisioning.setting.dto.response.WindowsImageOptionResponse;
import com.example.serverprovision.provisioning.setting.enums.OSFamily;
import com.example.serverprovision.provisioning.setting.enums.WindowsImagePresence;
import com.example.serverprovision.provisioning.setting.service.SettingQueryService;
import com.example.serverprovision.provisioning.setting.service.reference.os.OsInstallTargetPolicy;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.data.jpa.mapping.JpaMetamodelMappingContext;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * E4-1-a-2 CP4 — Windows 설치 단계의 작성 · 수정 · 상세 화면 렌더(SSR). Mocking 은 {@code SettingQueryService} 까지만 —
 * OS 옵션의 계열 · 차단 사유(disabled + title) · 설치 이미지 select · 비밀번호 입력 · 상세 fragment(detail-windows) 의 배지 ·
 * pre-fill JSON 의 비밀값 제거가 실제 Thymeleaf · Jackson 으로 실행된다.
 */
@WebMvcTest(controllers = SettingController.class)
class SettingControllerWindowsInstallViewTest {

    private static final String STANDARD = "Windows Server 2025 SERVERSTANDARD";

    @Autowired MockMvc mvc;

    @MockitoBean SettingQueryService queryService;
    @MockitoBean JpaMetamodelMappingContext jpaMetamodelMappingContext;

    private static List<SettingOSOptionGroupResponse> windowsOptions(String installBlockReason) {
        return List.of(new SettingOSOptionGroupResponse("Windows Server", List.of(new SettingOSOptionResponse(
                2L, "WINDOWS_SERVER", "2025", OSFamily.WINDOWS, installBlockReason, false, null, null,
                List.of(new SettingOSOptionResponse.IsoOption(60L, "win2025.iso", false, null, List.of(), List.of()))))));
    }

    private static List<WindowsImageOptionResponse> imageOptions() {
        return List.of(
                new WindowsImageOptionResponse("Windows Server 2025 SERVERSTANDARDCORE", "Windows Server 2025 Standard", "Server Core", "ServerStandard", 1),
                new WindowsImageOptionResponse(STANDARD, "Windows Server 2025 Standard (데스크톱 환경)", "Server", "ServerStandard", 2));
    }

    private static WindowsInstallationRequest windows(String imageName, String password) {
        return new WindowsInstallationRequest(2L, 60L,
                imageName == null ? null : new WindowsImageName(imageName),
                password == null ? null : new WindowsAdministratorPasswordRequest(password, false));
    }

    private static SettingDetailResponse detail(WindowsInstallationRequest process, Map<String, ReferenceNamesResponse.WindowsImageReference> windowsImages) {
        return new SettingDetailResponse(1L, "윈도우 세팅", false, true, false, 0L,
                List.of(process), List.of(), List.of(),
                new ReferenceNamesResponse(Map.of(), Map.of(), Map.of(), Map.of(2L, "Windows Server 2025"), Map.of(), Map.of(),
                        Map.of(), Map.of(60L, "win2025.iso"), Map.of(), windowsImages),
                LocalDateTime.now(), LocalDateTime.now());
    }

    // ==== 작성 폼 =================================================================================

    @Test
    @DisplayName("GET /new — Windows 옵션(data-os-family=WINDOWS · 선택 가능) + 설치 이미지 select(표시명 — 설치 형태) + Administrator 비밀번호 입력")
    void newForm_rendersWindowsBlock() throws Exception {
        given(queryService.findOSOptions()).willReturn(windowsOptions(null));
        given(queryService.findWindowsImageOptions()).willReturn(imageOptions());

        mvc.perform(get("/provisioning/setting/new"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("data-os-family=\"WINDOWS\"")))
                .andExpect(content().string(not(containsString("— 선택 불가"))))
                .andExpect(content().string(containsString("id=\"oiWindowsImage\"")))
                .andExpect(content().string(containsString("Windows Server 2025 Standard (데스크톱 환경) — Server")))
                .andExpect(content().string(containsString("value=\"" + STANDARD + "\"")))
                .andExpect(content().string(containsString("id=\"oiWinAdminPassword\"")))
                .andExpect(content().string(containsString("Windows Server 설치 단계입니다")));
    }

    @Test
    @DisplayName("GET /new — 설치 소스 미준비면 Windows 옵션이 disabled + title(정책 문장) 로 1차 차단된다")
    void newForm_blocksWindowsWhenSourceNotReady() throws Exception {
        given(queryService.findOSOptions()).willReturn(windowsOptions(OsInstallTargetPolicy.SOURCE_BLOCK_REASON));
        given(queryService.findWindowsImageOptions()).willReturn(List.of());

        mvc.perform(get("/provisioning/setting/new"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("WINDOWS_SERVER 2025 — 선택 불가")))
                // 옵션 title = 정책 문장(SSOT). 따옴표는 Thymeleaf 가 이스케이프하므로 문장 앞부분으로 대조한다.
                .andExpect(content().string(containsString("title=\"Windows 설치 소스가 준비되지 않았습니다.")))
                .andExpect(content().string(containsString("disabled")));
    }

    // ==== 상세 ===================================================================================

    @Test
    @DisplayName("GET /{id} — Windows 카드: 대상 OS · ISO · 설치 이미지(이름 + 표시명) · '설치 소스 일치' · 비밀번호 설정됨(값 미노출)")
    void detail_rendersMatched() throws Exception {
        given(queryService.findDetail(1L)).willReturn(detail(windows(STANDARD, "Qw3rty!Edit1"),
                Map.of(STANDARD, new ReferenceNamesResponse.WindowsImageReference("Windows Server 2025 Standard (데스크톱 환경)", WindowsImagePresence.MATCHED))));

        mvc.perform(get("/provisioning/setting/{id}", 1L))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Windows 계열")))
                .andExpect(content().string(containsString("Windows Server 2025")))
                .andExpect(content().string(containsString("win2025.iso")))
                .andExpect(content().string(containsString(STANDARD)))
                .andExpect(content().string(containsString("Windows Server 2025 Standard (데스크톱 환경)")))
                .andExpect(content().string(containsString("설치 소스 일치")))
                .andExpect(content().string(containsString(">설정됨<")))
                .andExpect(content().string(not(containsString("Qw3rty!Edit1"))));
    }

    @Test
    @DisplayName("GET /{id} — 소스에 없는 이미지는 '설치 소스에 없음' 배지 + 다음 행동 안내")
    void detail_rendersNotInSource() throws Exception {
        given(queryService.findDetail(1L)).willReturn(detail(windows(STANDARD, "Qw3rty!Edit1"),
                Map.of(STANDARD, new ReferenceNamesResponse.WindowsImageReference(STANDARD, WindowsImagePresence.NOT_IN_SOURCE))));

        mvc.perform(get("/provisioning/setting/{id}", 1L))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("설치 소스에 없음")))
                .andExpect(content().string(containsString("운영자가 설치 소스를 확인한 뒤 정의서를 수정 · 저장하십시오.")));
    }

    @Test
    @DisplayName("GET /{id} — 구 저장본(이미지 · 비밀번호 없음)은 '설치 이미지 미지정' · '미설정' 과 수정 안내")
    void detail_rendersLegacyUnspecified() throws Exception {
        given(queryService.findDetail(1L)).willReturn(detail(windows(null, null), Map.of()));

        mvc.perform(get("/provisioning/setting/{id}", 1L))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("설치 이미지 미지정")))
                .andExpect(content().string(containsString("정의서를 수정해 설치 이미지를 선택하십시오.")))
                .andExpect(content().string(containsString(">미설정<")))
                .andExpect(content().string(containsString("정의서를 수정해 비밀번호를 입력하십시오.")));
    }

    // ==== 수정 폼 ================================================================================

    @Test
    @DisplayName("GET /{id}/edit — pre-fill JSON 에 비밀번호 값은 없고 keepExistingPassword=true · 설치 이미지 이름은 실린다")
    void editForm_prefillStripsPassword() throws Exception {
        given(queryService.findDetail(1L)).willReturn(detail(windows(STANDARD, "Qw3rty!Edit1"),
                Map.of(STANDARD, new ReferenceNamesResponse.WindowsImageReference(STANDARD, WindowsImagePresence.MATCHED))));
        given(queryService.findOSOptions()).willReturn(windowsOptions(null));
        given(queryService.findWindowsImageOptions()).willReturn(imageOptions());

        // 선례(SettingControllerDiskGroupViewTest)처럼 Model 의 initialSettingJson 을 직접 대조한다 — 렌더 시 JS 인라인 이스케이프와 무관.
        mvc.perform(get("/provisioning/setting/{id}/edit", 1L))
                .andExpect(status().isOk())
                .andExpect(model().attribute("initialSettingJson", containsString("\"keepExistingPassword\":true")))
                .andExpect(model().attribute("initialSettingJson", containsString("\"osFamily\":\"WINDOWS\"")))
                .andExpect(model().attribute("initialSettingJson", containsString(STANDARD)))
                .andExpect(model().attribute("initialSettingJson", not(containsString("Qw3rty!Edit1"))))
                .andExpect(content().string(not(containsString("Qw3rty!Edit1"))));
    }
}
