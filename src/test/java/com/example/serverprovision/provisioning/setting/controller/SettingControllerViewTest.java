package com.example.serverprovision.provisioning.setting.controller;

import com.example.serverprovision.provisioning.setting.dto.request.BasicUpdateRequest;
import com.example.serverprovision.provisioning.setting.dto.request.BoardModelSelectionRequest;
import com.example.serverprovision.provisioning.setting.dto.request.FirmwareSelectionRequest;
import com.example.serverprovision.provisioning.setting.dto.request.PartitionRequest;
import com.example.serverprovision.provisioning.setting.dto.request.RHELInstallationRequest;
import com.example.serverprovision.provisioning.setting.dto.request.RootPasswordRequest;
import com.example.serverprovision.provisioning.setting.dto.request.TimezoneRequest;
import com.example.serverprovision.provisioning.setting.dto.request.UserRequest;
import com.example.serverprovision.provisioning.setting.dto.response.SettingDetailResponse;
import com.example.serverprovision.provisioning.setting.dto.response.SettingSummaryResponse;
import com.example.serverprovision.provisioning.setting.enums.BoardModelSelectionMode;
import com.example.serverprovision.provisioning.setting.enums.FileSystem;
import com.example.serverprovision.provisioning.setting.enums.FirmwareSelectionMode;
import com.example.serverprovision.provisioning.setting.enums.SettingProcessType;
import com.example.serverprovision.provisioning.setting.enums.SizeUnit;
import com.example.serverprovision.provisioning.setting.exception.SettingNotFoundException;
import com.example.serverprovision.provisioning.setting.service.SettingQueryService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.data.jpa.mapping.JpaMetamodelMappingContext;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

/**
 * U2-1 CP4 — {@link SettingController} SSR 통합 테스트 (목록 / 작성폼 / 상세 / 수정폼).
 * Mocking 은 {@code SettingQueryService} 까지만 — 뷰 선택·Model 적재·initialSettingJson 직렬화(비밀번호 제거)·
 * advice 404 매핑은 실제로 실행된다.
 */
@WebMvcTest(controllers = SettingController.class)
class SettingControllerViewTest {

    @Autowired MockMvc mvc;

    @MockitoBean SettingQueryService queryService;
    @MockitoBean JpaMetamodelMappingContext jpaMetamodelMappingContext;

    private SettingSummaryResponse summary(long id) {
        return new SettingSummaryResponse(id, "표준 세팅",
                List.of(SettingProcessType.BASIC_UPDATE), LocalDateTime.now());
    }

    /** 비밀번호가 실제로 담긴 RHEL 설치 단계 — initialSettingJson 의 비밀번호 제거를 검증하는 데 쓴다. */
    private SettingDetailResponse detailWithPasswords(long id) {
        RHELInstallationRequest rhel = new RHELInstallationRequest(
                1L, 100L,
                new TimezoneRequest("Asia/Seoul", true),
                List.of(new PartitionRequest("/", FileSystem.XFS, null, 0L, SizeUnit.GB, true)),
                new RootPasswordRequest("root-secret-pw", false, false),
                List.of(new UserRequest("admin", "user-secret-pw", true, false, false)),
                1L, List.of(1L), true, null);
        return new SettingDetailResponse(id, "표준 세팅",
                List.of(new BasicUpdateRequest(
                                new BoardModelSelectionRequest(BoardModelSelectionMode.SPECIFIED, 1L),
                                new FirmwareSelectionRequest(FirmwareSelectionMode.LATEST, null),
                                new FirmwareSelectionRequest(FirmwareSelectionMode.SPECIFIED, 1L)),
                        rhel),
                List.of(),
                List.of(),
                com.example.serverprovision.provisioning.setting.dto.response.ReferenceNamesResponse.empty(),
                LocalDateTime.now(), LocalDateTime.now());
    }

    // ==== 성공 2xx ====================================================

    @Test
    @DisplayName("GET /provisioning/setting — 목록 200 + list 뷰 + settings")
    void list_returns200() throws Exception {
        given(queryService.findAll()).willReturn(List.of(summary(1L)));

        mvc.perform(get("/provisioning/setting"))
                .andExpect(status().isOk())
                .andExpect(view().name("provisioning/setting-list"))
                .andExpect(model().attributeExists("settings"));
    }

    @Test
    @DisplayName("GET /new — 작성 폼 200 + 선택지 Model (processTypes/boardOptions/osOptions/fileSystems/sizeUnits)")
    void newForm_returns200_withOptions() throws Exception {
        given(queryService.findBoardOptions()).willReturn(List.of());
        given(queryService.findOSOptions()).willReturn(List.of());

        mvc.perform(get("/provisioning/setting/new"))
                .andExpect(status().isOk())
                .andExpect(view().name("provisioning/setting-new"))
                .andExpect(model().attributeExists(
                        "processTypes", "boardOptions", "osOptions", "fileSystems", "sizeUnits"));
    }

    @Test
    @DisplayName("GET /{id} — 상세 200 + detail 뷰 + setting")
    void detail_returns200() throws Exception {
        given(queryService.findDetail(1L)).willReturn(detailWithPasswords(1L));

        mvc.perform(get("/provisioning/setting/{id}", 1L))
                .andExpect(status().isOk())
                .andExpect(view().name("provisioning/setting-detail"))
                .andExpect(model().attributeExists("setting"));
    }

    @Test
    @DisplayName("GET /{id}/edit — 수정 폼 200 + initialSettingJson 에 판별자 포함·비밀번호 미포함")
    void editForm_returns200_initialJsonExcludesPasswords() throws Exception {
        given(queryService.findDetail(1L)).willReturn(detailWithPasswords(1L));
        given(queryService.findBoardOptions()).willReturn(List.of());
        given(queryService.findOSOptions()).willReturn(List.of());

        mvc.perform(get("/provisioning/setting/{id}/edit", 1L))
                .andExpect(status().isOk())
                .andExpect(view().name("provisioning/setting-edit"))
                .andExpect(model().attributeExists("setting", "initialSettingJson"))
                // 다형 직렬화 — 1단/2단 판별자가 pre-fill JSON 에 실려야 폼이 카드를 복원한다.
                .andExpect(model().attribute("initialSettingJson", containsString("\"type\":\"OS_INSTALLATION\"")))
                .andExpect(model().attribute("initialSettingJson", containsString("\"osFamily\":\"RHEL_BASED\"")))
                // 비밀번호는 서버 밖으로 다시 내보내지 않는다 (기존-유지 플래그로 대체).
                .andExpect(model().attribute("initialSettingJson", not(containsString("root-secret-pw"))))
                .andExpect(model().attribute("initialSettingJson", not(containsString("user-secret-pw"))))
                .andExpect(model().attribute("initialSettingJson", containsString("\"keepExistingPassword\":true")));
    }

    // ==== 404 ========================================================

    @Test
    @DisplayName("GET /{id} — 없는 id → SettingNotFound 404 (advice, silent redirect 폐기)")
    void detail_notFound_returns404() throws Exception {
        willThrow(new SettingNotFoundException(99L)).given(queryService).findDetail(99L);

        mvc.perform(get("/provisioning/setting/{id}", 99L).accept(MediaType.TEXT_HTML))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("GET /{id}/edit — 없는 id → SettingNotFound 404 (advice)")
    void editForm_notFound_returns404() throws Exception {
        willThrow(new SettingNotFoundException(99L)).given(queryService).findDetail(99L);

        mvc.perform(get("/provisioning/setting/{id}/edit", 99L).accept(MediaType.TEXT_HTML))
                .andExpect(status().isNotFound());
    }
}
