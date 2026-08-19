package com.example.serverprovision.provisioning.setting.controller;

import com.example.serverprovision.management.raidcard.enums.RaidLevel;
import com.example.serverprovision.provisioning.setting.dto.request.DiskCapacityRequirement;
import com.example.serverprovision.provisioning.setting.dto.request.DiskCountRequirement;
import com.example.serverprovision.provisioning.setting.dto.request.DiskGroupRuleRequest;
import com.example.serverprovision.provisioning.setting.dto.request.PartitionRequest;
import com.example.serverprovision.provisioning.setting.dto.request.RHELInstallationRequest;
import com.example.serverprovision.provisioning.setting.dto.request.RaidConfigurationRequest;
import com.example.serverprovision.provisioning.setting.dto.request.BasicSettingRequest;
import com.example.serverprovision.provisioning.setting.dto.request.RootPasswordRequest;
import com.example.serverprovision.provisioning.setting.dto.request.TimezoneRequest;
import com.example.serverprovision.provisioning.setting.dto.response.ReferenceNamesResponse;
import com.example.serverprovision.provisioning.setting.dto.response.SettingDetailResponse;
import com.example.serverprovision.provisioning.setting.dto.response.SettingRaidCardOptionGroupResponse;
import com.example.serverprovision.provisioning.setting.dto.response.SettingRaidCardOptionResponse;
import com.example.serverprovision.provisioning.setting.enums.CapacityRequirementMode;
import com.example.serverprovision.provisioning.setting.enums.DiskCapacityUnit;
import com.example.serverprovision.provisioning.setting.enums.DiskCountMode;
import com.example.serverprovision.provisioning.setting.enums.DiskTransportRequirement;
import com.example.serverprovision.provisioning.setting.enums.DiskTypeRequirement;
import com.example.serverprovision.provisioning.setting.enums.FileSystem;
import com.example.serverprovision.provisioning.setting.enums.SizeUnit;
import com.example.serverprovision.provisioning.setting.service.SettingQueryService;
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
 * U4-1-1 v2 CP4 — 상세 · 작성 · 수정 화면의 RAID 구성 단계 렌더 통합 테스트(SSR).
 * Mocking 은 {@code SettingQueryService} 까지만 — Thymeleaf 조각(카드 행 · 묶음 표 · 사라진 카드 표기) ·
 * Model 적재(선택지 7 종 + raidCardMetaJson) · initialSettingJson 직렬화가 실제로 실행된다.
 */
@WebMvcTest(controllers = SettingController.class)
class SettingControllerDiskGroupViewTest {

    @Autowired MockMvc mvc;

    @MockitoBean SettingQueryService queryService;
    @MockitoBean JpaMetamodelMappingContext jpaMetamodelMappingContext;

    private static RaidConfigurationRequest raid(Long raidCardId, List<DiskGroupRuleRequest> groups) {
        return new RaidConfigurationRequest(raidCardId, groups);
    }

    /** 순서 검증용 — BIOS 설정 · RAID 구성 · OS 설치를 함께 가진 정의서(RHEL 은 비밀번호 제거 검증 겸용). */
    private static RHELInstallationRequest rhel() {
        return new RHELInstallationRequest(1L, 100L,
                new TimezoneRequest("Asia/Seoul", true),
                List.of(new PartitionRequest("/", FileSystem.XFS, null, 0L, SizeUnit.GB, true)),
                new RootPasswordRequest("root-secret", false, false), List.of(), 1L, List.of(), false, null);
    }

    private static List<DiskGroupRuleRequest> twoRules() {
        return List.of(
                new DiskGroupRuleRequest(RaidLevel.RAID1, DiskTypeRequirement.SSD, DiskTransportRequirement.SATA,
                        new DiskCapacityRequirement(CapacityRequirementMode.SPECIFIED, 480L, DiskCapacityUnit.GB),
                        new DiskCountRequirement(DiskCountMode.EXACT, 2)),
                new DiskGroupRuleRequest(null, DiskTypeRequirement.SSD, DiskTransportRequirement.NVME,
                        new DiskCapacityRequirement(CapacityRequirementMode.AUTO, null, null),
                        new DiskCountRequirement(DiskCountMode.AT_LEAST, 1)));
    }

    private static SettingDetailResponse detail(RaidConfigurationRequest raid, Map<Long, String> raidCards) {
        return detail(List.of(raid), raidCards);
    }

    private static SettingDetailResponse detail(List<? extends com.example.serverprovision.provisioning.setting.dto.request.AbstractProcessRequest> processes,
                                                Map<Long, String> raidCards) {
        return new SettingDetailResponse(1L, "디스크 세팅", false, true, false, 0L,
                List.copyOf(processes), List.of(), List.of(),
                new ReferenceNamesResponse(Map.of(), Map.of(), Map.of(), Map.of(1L, "Rocky Linux 9.4"), Map.of(), Map.of(),
                        Map.of(), Map.of(), raidCards),
                LocalDateTime.now(), LocalDateTime.now());
    }

    // ==== 상세 렌더 =============================================================================

    @Test
    @DisplayName("GET /{id} — 카드명 · 묶음 표(RAID1 배지 · RAID 없음 배지 · 480 GB · 2개 · 1개 이상 · 자동 탐지) 렌더")
    void detail_rendersCardAndRules() throws Exception {
        given(queryService.findDetail(1L)).willReturn(detail(raid(7L, twoRules()), Map.of(7L, "GIGABYTE CRA3338")));

        mvc.perform(get("/provisioning/setting/{id}", 1L))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("GIGABYTE CRA3338")))
                .andExpect(content().string(containsString(">RAID1<")))
                .andExpect(content().string(containsString(">RAID 없음<")))
                .andExpect(content().string(containsString("480 GB")))
                .andExpect(content().string(containsString(">2개<")))
                .andExpect(content().string(containsString(">1개 이상<")))
                .andExpect(content().string(containsString("자동 탐지")))
                .andExpect(content().string(not(containsString("(사라진 카드 #"))));
    }

    @Test
    @DisplayName("GET /{id} — 참조하던 카드가 사라졌으면 #id 폴백이 아니라 '(사라진 카드 #7)' 을 그린다 (소프트참조)")
    void detail_rendersGoneCardExplicitly() throws Exception {
        given(queryService.findDetail(1L)).willReturn(detail(raid(7L, twoRules()), Map.of()));

        mvc.perform(get("/provisioning/setting/{id}", 1L))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("(사라진 카드 #7)")));
    }

    @Test
    @DisplayName("GET /{id} — 카드 · 묶음이 없는 구 형식 정의서는 '지정 안 함' · '설정 안 함' 문구")
    void detail_rendersUnsetState() throws Exception {
        given(queryService.findDetail(1L)).willReturn(detail(raid(null, List.of()), Map.of()));

        mvc.perform(get("/provisioning/setting/{id}", 1L))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("지정 안 함")))
                .andExpect(content().string(containsString("설정 안 함")));
    }

    // ==== 작성 · 수정 폼 =======================================================================

    @Test
    @DisplayName("GET /new — 카드 선택지 optgroup · 판정 재료 JSON(raidCardMetaJson) · 레벨 옵션 data-min-disks(-cached) 렌더")
    void newForm_rendersRaidCardOptionsAndJudgmentMaterial() throws Exception {
        given(queryService.findRaidCardOptions()).willReturn(List.of(new SettingRaidCardOptionGroupResponse("GIGABYTE",
                List.of(new SettingRaidCardOptionResponse(1L, "CRA3338", "GIGABYTE CRA3338", false, "없음",
                        List.of(RaidLevel.RAID0, RaidLevel.RAID1), "RAID0 · RAID1",
                        Map.of(RaidLevel.RAID5, "RAID5 를 만들 수 없는 카드입니다 — 지원하는 RAID 레벨은 RAID0 · RAID1 입니다."),
                        false, null, null)))));

        mvc.perform(get("/provisioning/setting/new"))
                .andExpect(status().isOk())
                .andExpect(model().attributeExists("raidCardOptions", "raidCardMetaJson", "raidLevels",
                        "diskTypes", "diskTransports", "diskCapacityUnits", "diskCountModes"))
                .andExpect(content().string(containsString("GIGABYTE CRA3338 — 캐시 없음 · RAID0 · RAID1")))
                .andExpect(content().string(containsString("RAID_CARD_META_JSON")))
                .andExpect(content().string(containsString("blockReasons")))
                // RAID0 은 캐시 없는 카드 2 · 캐시 카드 1 — RaidLevel.minimumDisks 가 옵션 data-* 로 내려간다.
                .andExpect(content().string(containsString("data-min-disks=\"2\"")))
                .andExpect(content().string(containsString("data-min-disks-cached=\"1\"")))
                .andExpect(content().string(containsString("tplDiskGroupRow")));
    }

    @Test
    @DisplayName("GET /{id}/edit — initialSettingJson 에 type=RAID_CONFIGURATION · raidCardId · diskGroups 가 실린다(pre-fill 재료)")
    void editForm_initialJsonCarriesDiskFields() throws Exception {
        given(queryService.findDetail(1L)).willReturn(detail(List.of(raid(7L, twoRules()), rhel()), Map.of(7L, "GIGABYTE CRA3338")));

        mvc.perform(get("/provisioning/setting/{id}/edit", 1L))
                .andExpect(status().isOk())
                .andExpect(model().attribute("initialSettingJson", containsString("\"type\":\"RAID_CONFIGURATION\"")))
                .andExpect(model().attribute("initialSettingJson", containsString("\"raidCardId\":7")))
                .andExpect(model().attribute("initialSettingJson", containsString("\"diskGroups\":[")))
                .andExpect(model().attribute("initialSettingJson", containsString("\"raidLevel\":\"RAID1\"")))
                .andExpect(model().attribute("initialSettingJson", not(containsString("root-secret"))));
    }

    @Test
    @DisplayName("GET /{id} — 단계 카드 순서: BIOS 설정 → RAID 구성 → OS 설치 (enum 선언 순 · v2 D15)")
    void detail_rendersRaidCardBetweenBiosSettingAndOsInstall() throws Exception {
        given(queryService.findDetail(1L)).willReturn(detail(
                List.of(new BasicSettingRequest(List.of(9L)), raid(7L, twoRules()), rhel()), Map.of(7L, "GIGABYTE CRA3338")));

        String html = mvc.perform(get("/provisioning/setting/{id}", 1L))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        int bios = html.indexOf(">BIOS 설정<");
        int raid = html.indexOf(">RAID 구성<");
        int os = html.indexOf(">OS 설치<");
        org.assertj.core.api.Assertions.assertThat(bios).isGreaterThan(-1);
        org.assertj.core.api.Assertions.assertThat(raid).isGreaterThan(bios);
        org.assertj.core.api.Assertions.assertThat(os).isGreaterThan(raid);
    }
}
