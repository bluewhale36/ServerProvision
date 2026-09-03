package com.example.serverprovision.provisioning.setting.controller;

import com.example.serverprovision.management.raidcard.enums.RaidLevel;
import com.example.serverprovision.provisioning.setting.dto.request.VolumePriorityRuleRequest;
import com.example.serverprovision.provisioning.setting.enums.DiskGroupRole;
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
        // E3.5-4 — 축 명시 픽스처. 구 저장본(축 null · "미지정" 표기) 검증은 legacy 케이스가 3-인자 생성자로 따로 만든다.
        return new RaidConfigurationRequest(raidCardId, groups, VolumePriorityRuleRequest.defaults(),
                com.example.serverprovision.provisioning.setting.enums.ExistingRaidConfigPolicy.DESTROY);
    }

    /** 순서 검증용 — BIOS 설정 · RAID 구성 · OS 설치를 함께 가진 정의서(RHEL 은 비밀번호 제거 검증 겸용). */
    private static RHELInstallationRequest rhel() {
        return new RHELInstallationRequest(1L, 100L,
                new TimezoneRequest("Asia/Seoul", true),
                List.of(new PartitionRequest("/", FileSystem.XFS, 0L, SizeUnit.GB, true)),
                new RootPasswordRequest("root-secret", false, false), List.of(), 1L, List.of(), false, null);
    }

    private static List<DiskGroupRuleRequest> twoRules() {
        return List.of(
                new DiskGroupRuleRequest(RaidLevel.RAID1, DiskTypeRequirement.SSD, DiskTransportRequirement.SATA,
                        new DiskCapacityRequirement(CapacityRequirementMode.SPECIFIED, 480L, DiskCapacityUnit.GB),
                        new DiskCountRequirement(DiskCountMode.EXACT, 2), DiskGroupRole.OS),
                new DiskGroupRuleRequest(null, DiskTypeRequirement.SSD, DiskTransportRequirement.NVME,
                        new DiskCapacityRequirement(CapacityRequirementMode.AUTO, null, null),
                        new DiskCountRequirement(DiskCountMode.AT_LEAST, 1), DiskGroupRole.BY_PRIORITY));
    }

    private static SettingDetailResponse detail(RaidConfigurationRequest raid, Map<Long, String> raidCards) {
        return detail(List.of(raid), raidCards);
    }

    private static SettingDetailResponse detail(List<? extends com.example.serverprovision.provisioning.setting.dto.request.AbstractProcessRequest> processes,
                                                Map<Long, String> raidCards) {
        return new SettingDetailResponse(1L, "디스크 세팅", false, true, false, 0L,
                List.copyOf(processes), List.of(), List.of(),
                new ReferenceNamesResponse(Map.of(), Map.of(), Map.of(), Map.of(1L, "Rocky Linux 9.4"), Map.of(), Map.of(),
                        Map.of(), Map.of(), raidCards, Map.of()),
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
    @DisplayName("GET /{id} — E3.5-7-a: 순위 배지 · 적용 순서 안내 · '3개씩'(EACH) 표기 렌더")
    void detail_rendersRankAndEachMode() throws Exception {
        List<DiskGroupRuleRequest> rules = List.of(
                new DiskGroupRuleRequest(RaidLevel.RAID1, DiskTypeRequirement.SSD, DiskTransportRequirement.SATA,
                        new DiskCapacityRequirement(CapacityRequirementMode.AUTO, null, null),
                        new DiskCountRequirement(DiskCountMode.EXACT, 2), DiskGroupRole.OS),
                new DiskGroupRuleRequest(RaidLevel.RAID5, DiskTypeRequirement.SSD, DiskTransportRequirement.SATA,
                        new DiskCapacityRequirement(CapacityRequirementMode.AUTO, null, null),
                        new DiskCountRequirement(DiskCountMode.EACH, 3), DiskGroupRole.BY_PRIORITY));
        given(queryService.findDetail(1L)).willReturn(detail(raid(7L, rules), Map.of(7L, "GIGABYTE CRA3338")));

        mvc.perform(get("/provisioning/setting/{id}", 1L))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("<th>순위</th>")))
                .andExpect(content().string(containsString("class=\"n-rank\">1</span>")))
                .andExpect(content().string(containsString("class=\"n-rank\">2</span>")))
                .andExpect(content().string(containsString("위 규칙부터 순서대로 디스크를 가져가고")))
                .andExpect(content().string(containsString(">2개<")))
                .andExpect(content().string(containsString(">3개씩<")));
    }

    @Test
    @DisplayName("GET /new — E3.5-7-a: 개수 모드 select 3옵션(개 · 개씩 · 개 이상, 첫 옵션 '개') · 순위 열 · 적용 순서 안내 · 행 템플릿 순위 배지")
    void form_rendersCountModesAndRankColumn() throws Exception {
        String html = mvc.perform(get("/provisioning/setting/new"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        int exact = html.indexOf("<option value=\"EXACT\">개</option>");
        int each = html.indexOf("<option value=\"EACH\">개씩</option>");
        int atLeast = html.indexOf("<option value=\"AT_LEAST\">개 이상</option>");
        org.assertj.core.api.Assertions.assertThat(exact).isPositive();
        org.assertj.core.api.Assertions.assertThat(each).isGreaterThan(exact);        // 선언 순서 = select 순서 · 첫 옵션 = 기본 '개'
        org.assertj.core.api.Assertions.assertThat(atLeast).isGreaterThan(each);
        org.assertj.core.api.Assertions.assertThat(html)
                .contains("<th>순위</th>")
                .contains("n-rank dgRank")
                .contains("위 규칙부터 순서대로 디스크를 가져가고, 남은 디스크는 아래 규칙으로 흐릅니다.")
                .contains("행 순서 = 적용 순서");
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
                        false, null, null, "MPT_IR", false)))));

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
                .andExpect(content().string(containsString("tplDiskGroupRow")))
                // E3.5-6 — VD 파라미터 서브 행 템플릿 · 8축 select · 메타의 supportsVdParameters 렌더
                .andExpect(content().string(containsString("tplVdParamsRow")))
                .andExpect(content().string(containsString("vdWritePolicy")))
                .andExpect(content().string(containsString("Write Back (기본값)")))
                .andExpect(content().string(containsString("supportsVdParameters")));
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

    // ==== U4-1-2 — 역할 열 · 볼륨 우선순위 ==========================================================

    @Test
    @DisplayName("GET /{id} — 역할 열(OS 영역 초록 배지 · 우선순위에 따름 텍스트) · 볼륨 우선순위 목록 5 행 렌더")
    void detail_rendersRoleAndPriorities() throws Exception {
        given(queryService.findDetail(1L)).willReturn(detail(raid(7L, twoRules()), Map.of(7L, "GIGABYTE CRA3338")));

        mvc.perform(get("/provisioning/setting/{id}", 1L))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("<th>역할</th>")))
                .andExpect(content().string(containsString("n-badge-green\">OS 영역<")))
                .andExpect(content().string(containsString(">우선순위에 따름<")))
                .andExpect(content().string(containsString("볼륨 우선순위")))
                // 순위 열이 숫자로 명시된다(CP6 검수) — 1 순위 SSD · NVMe, 5 순위 HDD · SATA
                .andExpect(content().string(containsString("<th>순위</th>")))
                .andExpect(content().string(containsString("n-rank\">1<")))
                .andExpect(content().string(containsString("n-rank\">5<")))
                .andExpect(content().string(containsString(">NVMe<")))
                .andExpect(content().string(containsString(">작은 용량부터<")))
                .andExpect(content().string(not(containsString("미지정"))));
    }

    @Test
    @DisplayName("GET /{id} — 우선순위 빈 목록은 '없음 — 열거 순서', null(구 저장본)은 '미지정' · 역할 null 은 '미지정'")
    void detail_rendersEmptyAndLegacyPriorities() throws Exception {
        var legacyRule = new DiskGroupRuleRequest(RaidLevel.RAID1, DiskTypeRequirement.SSD, DiskTransportRequirement.SATA,
                new DiskCapacityRequirement(CapacityRequirementMode.SPECIFIED, 480L, DiskCapacityUnit.GB),
                new DiskCountRequirement(DiskCountMode.EXACT, 2), null);
        given(queryService.findDetail(1L)).willReturn(detail(new RaidConfigurationRequest(7L, List.of(legacyRule), null), Map.of(7L, "GIGABYTE CRA3338")));
        mvc.perform(get("/provisioning/setting/{id}", 1L))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("미지정 (구 저장본")))
                .andExpect(content().string(containsString("n-table-muted\">미지정<")));

        given(queryService.findDetail(2L)).willReturn(detail(new RaidConfigurationRequest(7L, twoRules(), List.of()), Map.of(7L, "GIGABYTE CRA3338")));
        mvc.perform(get("/provisioning/setting/{id}", 2L))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("없음 — 볼륨은 열거 순서대로 놓입니다")));
    }

    @Test
    @DisplayName("GET /new — 역할 · 용량 순서 선택지 · 기본 우선순위 JSON(defaultVolumePrioritiesJson) · 우선순위 표 · 행 템플릿 렌더, 우선순위 select 에는 AUTO 없음")
    void newForm_rendersRoleAndPriorityMaterial() throws Exception {
        given(queryService.findRaidCardOptions()).willReturn(List.of());

        mvc.perform(get("/provisioning/setting/new"))
                .andExpect(status().isOk())
                .andExpect(model().attributeExists("diskGroupRoles", "capacityOrders", "defaultVolumePrioritiesJson"))
                .andExpect(content().string(containsString("DEFAULT_VOLUME_PRIORITIES_JSON")))
                .andExpect(content().string(containsString("rcPriorityTable")))
                .andExpect(content().string(containsString("tplPriorityRow")))
                .andExpect(content().string(containsString("vpRank")))
                .andExpect(content().string(containsString("rcResetPriority")))
                .andExpect(content().string(containsString(">영역 할당 없음<")))
                .andExpect(content().string(containsString(">큰 용량부터<")))
                // 우선순위 행 템플릿의 종류 · 전송 select 는 AUTO 를 내리지 않는다(th:if) — 묶음 행 템플릿엔 남아 있다.
                .andExpect(content().string(containsString("class=\"n-select vpType\">\n                        <option value=\"SSD\"")))
                .andExpect(content().string(containsString("class=\"n-page-lg\"")));
    }

    // ==== U4-1-3 — OS 설치 카드의 대상 볼륨 안내 · 디스크명 열 제거 ====================================

    @Test
    @DisplayName("GET /{id} — RAID 구성(OS 고정 480 GB × 2) + OS 설치: 안내 'RAID 구성 1번 묶음이 OS 영역' · '최소 용량 480 GB', 파티션 표에 '디스크' 헤더 없음")
    void detail_osVolumeTargetFixed() throws Exception {
        given(queryService.findDetail(1L)).willReturn(detail(List.of(raid(7L, twoRules()), rhel()), Map.of(7L, "GIGABYTE CRA3338")));

        mvc.perform(get("/provisioning/setting/{id}", 1L))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("RAID 구성 1번 묶음(RAID1 · SSD · SATA · 480 GB · 2개)이 OS 영역입니다")))
                .andExpect(content().string(containsString("OS 영역 최소 용량 480 GB · 고정 파티션 합 0 GiB(+ grow)")))
                .andExpect(content().string(not(containsString("<th>디스크</th>"))));
    }

    @Test
    @DisplayName("GET /{id} — RAID 구성 없이 OS 설치만: 안내 'RAID 구성 단계가 없어 설치기가 디스크를 자동 선택' · 용량 줄 없음")
    void detail_osVolumeTargetNone() throws Exception {
        given(queryService.findDetail(2L)).willReturn(detail(List.of(rhel()), Map.of()));

        mvc.perform(get("/provisioning/setting/{id}", 2L))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("RAID 구성 단계가 없어 설치기가 디스크를 자동 선택합니다")))
                .andExpect(content().string(not(containsString("OS 영역 최소 용량"))));
    }

    @Test
    @DisplayName("GET /new — 파티션 행 템플릿에 pDiskName 없음 · 레벨 옵션 data-usable-a/b(RAID5 = 1 · −1). OS 파티션 안내 줄은 R11 축소로 화면 제외")
    void newForm_noDiskNameAndUsableCoefficients() throws Exception {
        given(queryService.findRaidCardOptions()).willReturn(List.of());

        mvc.perform(get("/provisioning/setting/new"))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("pDiskName"))))
                .andExpect(content().string(not(containsString("<th>디스크명</th>"))))
                // R11 — OS 설치 카드가 식별 전용으로 축소되어 파티션 UI(대상 안내 줄 · 에러 필드 ·
                // 메시지 사전)는 화면에서 제외됐다. U4-1-3 의 안내는 E4 부활 시 git 이력으로 복원되며,
                // 여기서는 부재를 단언해 잔존 마크업의 재유입(CP5 D1 계열)을 막는다.
                .andExpect(content().string(not(containsString("oiOsVolumeTargetKind"))))
                .andExpect(content().string(not(containsString("data-error-field=\"partitionsWithinOsVolume\""))))
                .andExpect(content().string(containsString("data-usable-a=\"1.0\"")))
                .andExpect(content().string(containsString("data-usable-b=\"-1\"")))
                .andExpect(content().string(containsString("data-usable-a=\"0.5\"")));
    }
}
