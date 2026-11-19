package com.example.serverprovision.provisioning.setting.controller;

import com.example.serverprovision.provisioning.setting.dto.response.SettingOSOptionGroupResponse;
import com.example.serverprovision.provisioning.setting.dto.response.SettingOSOptionResponse;
import com.example.serverprovision.provisioning.setting.enums.OSFamily;
import com.example.serverprovision.provisioning.setting.service.SettingQueryService;
import com.example.serverprovision.support.FixedClockTestConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.mapping.JpaMetamodelMappingContext;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * E4-1-b-2 — 정의서 작성 화면의 OS · ISO 셀렉트(OSName 별 optgroup · 화면 이름 · 막힌 OS disabled + 사유 · 등록 실패 ISO disabled)와
 * 리눅스 입력 렌더. Mocking 은 {@code SettingQueryService} 까지만 — 실제 Thymeleaf 로 그려진다.
 */
@Import(FixedClockTestConfig.class)
@WebMvcTest(controllers = SettingController.class)
class SettingControllerOsSelectViewTest {

    @Autowired MockMvc mvc;

    @MockitoBean SettingQueryService queryService;
    @MockitoBean JpaMetamodelMappingContext jpaMetamodelMappingContext;

    private static SettingOSOptionResponse os(long id, String name, String display, String version, String blockReason,
                                              boolean rootSsh, SettingOSOptionResponse.IsoOption... isos) {
        return new SettingOSOptionResponse(id, name, display, version, OSFamily.RHEL_BASED, blockReason, rootSsh,
                false, null, null, List.of(isos));
    }

    /** failureBody = 도메인이 기록한 등록 실패 사유 본문(ISO.registrationFailure). 거절 문장은 도메인처럼 접두 + 본문으로 조립한다. */
    private static SettingOSOptionResponse.IsoOption iso(long id, String name, String failureBody) {
        String blockReason = failureBody == null ? null : "등록에 실패한 ISO 입니다. " + failureBody;
        return new SettingOSOptionResponse.IsoOption(id, name, "/isos/" + name, false, null, null, null, null,
                List.of(), List.of(), blockReason, failureBody);
    }

    @Test
    @DisplayName("GET /new — OS 셀렉트: OSName 별 optgroup · 화면 이름(enum 원문 아님) · 막힌 OS 는 disabled + 사유(title) · root SSH 여부")
    void newForm_osSelect() throws Exception {
        given(queryService.findOSOptions()).willReturn(List.of(
                new SettingOSOptionGroupResponse("Rocky Linux", List.of(
                        os(1L, "ROCKY_LINUX", "Rocky Linux", "10.0", null, true, iso(10L, "Rocky-10.0.iso", null)),
                        os(2L, "ROCKY_LINUX", "Rocky Linux", "9.6", null, false, iso(20L, "Rocky-9.6.iso", null)))),
                new SettingOSOptionGroupResponse("CentOS", List.of(
                        os(3L, "CENTOS", "CentOS", "7.9", "Rocky Linux 8, 9, 10 만 설치할 수 있습니다.", false, iso(30L, "CentOS-7.9.iso", null))))));

        mvc.perform(get("/provisioning/setting/new"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("<optgroup label=\"Rocky Linux\">")))
                .andExpect(content().string(containsString("<optgroup label=\"CentOS\">")))
                .andExpect(content().string(containsString(">Rocky Linux 10.0</option>")))
                .andExpect(content().string(not(containsString(">ROCKY_LINUX 10.0"))))
                .andExpect(content().string(containsString("CentOS 7.9 (선택 불가)")))
                .andExpect(content().string(containsString("title=\"Rocky Linux 8, 9, 10 만 설치할 수 있습니다.\"")))
                .andExpect(content().string(containsString("data-root-ssh-supported=\"true\"")))
                .andExpect(content().string(containsString("data-os-family=\"RHEL_BASED\"")));
    }

    @Test
    @DisplayName("GET /new — ISO 는 Miller 2 열: 항목 = radio + 상세 버튼(형제) · 등록 실패는 radio disabled + 항목 안 사유 + 위험 표지 · 상세에 경로 전체")
    void newForm_isoMiller() throws Exception {
        given(queryService.findOSOptions()).willReturn(List.of(
                new SettingOSOptionGroupResponse("Rocky Linux", List.of(
                        os(1L, "ROCKY_LINUX", "Rocky Linux", "10.0", null, true,
                                iso(10L, "Rocky-10.0.iso", null),
                                iso(11L, "Rocky-11.0-fake.iso", "ISO 의 실제 버전(Rocky Linux 11.0)과 등록한 OS 버전(Rocky Linux 10.0)이 다릅니다."))))));

        mvc.perform(get("/provisioning/setting/new"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("data-iso-miller")))
                .andExpect(content().string(containsString("n-miller-item n-miller-item-choice")))
                .andExpect(content().string(containsString("name=\"oiIsoRadio\" value=\"10\"")))
                .andExpect(content().string(containsString("class=\"n-miller-choice\" id=\"oiIsoName10\"")))
                .andExpect(content().string(containsString("n-miller-status-tag n-miller-status-tag-danger")))
                .andExpect(content().string(containsString("n-miller-choice-reason\">ISO 의 실제 버전(Rocky Linux 11.0)")))   // 표지와 겹치는 앞 문장 없이 본문만
                .andExpect(content().string(containsString("data-blocked=\"true\"")))
                .andExpect(content().string(containsString("data-iso-group hidden data-os-id=\"1\"")))
                .andExpect(content().string(containsString("/isos/Rocky-10.0.iso")))
                .andExpect(content().string(containsString("/css/miller.css")))
                .andExpect(content().string(not(containsString("id=\"oiIsoSelect\""))));
    }

    @Test
    @DisplayName("GET /new — 막힌 OS 의 사유가 셀렉트 아래 보이는 글자로(option title 만이 아님)")
    void newForm_osBlockReasonVisible() throws Exception {
        given(queryService.findOSOptions()).willReturn(List.of(
                new SettingOSOptionGroupResponse("CentOS", List.of(
                        os(3L, "CENTOS", "CentOS", "7.9", "Rocky Linux 8, 9, 10 만 설치할 수 있습니다.", false, iso(30L, "CentOS-7.9.iso", null))))));

        mvc.perform(get("/provisioning/setting/new"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("data-os-block-reason")))
                .andExpect(content().string(containsString("CentOS 7.9: Rocky Linux 8, 9, 10 만 설치할 수 있습니다.")));
    }

    @Test
    @DisplayName("GET /new — 리눅스 입력(시간대 · 파티션 · OS 영역 안내 · root · 사용자) + KDump · RTC UTC 기본 체크 · 암호화 체크 없음 · 검색 선택기 없음")
    void newForm_rendersLinuxInputs() throws Exception {
        given(queryService.findOSOptions()).willReturn(List.of());

        mvc.perform(get("/provisioning/setting/new"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("id=\"oiOsSelect\"")))
                .andExpect(content().string(containsString("id=\"oiLinuxBlock\"")))
                .andExpect(content().string(containsString("id=\"oiTzRegion\"")))
                .andExpect(content().string(containsString("id=\"oiPartitionTable\"")))
                .andExpect(content().string(containsString("id=\"oiRootPassword\"")))
                .andExpect(content().string(containsString("data-root-ssh-box")))
                .andExpect(content().string(containsString("id=\"oiUserTable\"")))
                .andExpect(content().string(containsString("id=\"oiKdump\" checked")))
                .andExpect(content().string(containsString("id=\"oiIsUtc\" checked")))
                .andExpect(content().string(not(containsString("oiRootEncrypted"))))
                .andExpect(content().string(not(containsString("uEncrypted"))))
                .andExpect(content().string(not(containsString("data-lookup-mode"))));
    }
}
