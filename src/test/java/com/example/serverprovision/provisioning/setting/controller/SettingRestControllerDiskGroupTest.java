package com.example.serverprovision.provisioning.setting.controller;

import com.example.serverprovision.management.raidcard.exception.RaidCardNotFoundException;
import com.example.serverprovision.provisioning.setting.dto.request.RaidConfigurationRequest;
import com.example.serverprovision.provisioning.setting.dto.request.SettingSaveRequest;
import com.example.serverprovision.provisioning.setting.dto.response.SettingSaveResponse;
import com.example.serverprovision.provisioning.setting.exception.DisabledResourceReferenceException;
import com.example.serverprovision.provisioning.setting.exception.InvalidDiskGroupException;
import com.example.serverprovision.provisioning.setting.service.SettingCommandService;
import com.example.serverprovision.provisioning.setting.service.SettingQueryService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.data.jpa.mapping.JpaMetamodelMappingContext;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * U4-1-1 v2 CP4 — RAID 구성 단계(flat 타입 {@code RAID_CONFIGURATION})의 저장 플로우 통합 테스트.
 * Mocking 은 Service 까지만 — <b>새 타입의 Jackson 역직렬화(FLAT_SUBTYPES · 중첩 record) + Bean Validation(@AssertTrue 카드 요구 방향 ·
 * 용량 정합 · 개수 하한) + advice 매핑(400/404/409, field-bound)</b>이 실제로 실행된다.
 * 참조 검사(카드 실존/enabled)와 {@code DiskGroupRules} 는 Service 안에서 돌므로 예외를 Service mock 이 던져
 * HTTP 계층 매핑을 검증한다(단위는 {@code DiskGroupRulesTest} · {@code OSInstallationReferenceInspectorTest}).
 */
@WebMvcTest(controllers = SettingRestController.class)
class SettingRestControllerDiskGroupTest {

    @Autowired MockMvc mvc;

    @MockitoBean SettingCommandService commandService;
    @MockitoBean SettingQueryService queryService;
    @MockitoBean JpaMetamodelMappingContext jpaMetamodelMappingContext;

    private static final String RAID1_RULE = """
            {"raidLevel": "RAID1", "diskType": "SSD", "transport": "SATA",
             "capacity": {"mode": "SPECIFIED", "size": 480, "unit": "GB"},
             "count": {"mode": "EXACT", "value": 2}, "role": "BY_PRIORITY"}
            """;
    private static final String NO_RAID_NVME_RULE = """
            {"raidLevel": null, "diskType": "SSD", "transport": "NVME",
             "capacity": {"mode": "AUTO", "size": null, "unit": null},
             "count": {"mode": "EXACT", "value": 1}, "role": "BY_PRIORITY"}
            """;
    /** U4-1-2 — 우선순위 기본 5 행 중 하나만(빈 배열도 명시적 값이지만, 필드 자체는 필수). */
    private static final String PRIORITY_ROWS = """
            [{"diskType": "SSD", "transport": "NVME", "capacityOrder": "SMALLER_FIRST"}]
            """;

    /** RAID 구성 단계(flat) — raidCardId · diskGroups 만 갈아 끼운다(우선순위는 기본 1 행 고정 — U4-1-2 로 필수가 됐다). */
    private static String raid(String raidCardId, String diskGroups) {
        return """
                {"type": "RAID_CONFIGURATION", "raidCardId": %s, "diskGroups": [%s], "volumePriorities": %s}
                """.formatted(raidCardId, diskGroups, PRIORITY_ROWS);
    }

    private static String body(String process) {
        return "{\"name\": \"디스크 세팅\", \"processList\": [" + process + "]}";
    }

    private org.springframework.test.web.servlet.ResultActions send(String json) throws Exception {
        return mvc.perform(post("/provisioning/setting").contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON).content(json));
    }

    // ==== 성공 2xx ============================================================================

    @Test
    @DisplayName("POST — 카드 + RAID1 묶음 + RAID 없음 NVMe 묶음 → 201, 서비스가 받은 계약에 두 필드가 실려 있다")
    void create_withCardAndRules_returns201_andCarriesFields() throws Exception {
        given(commandService.create(any())).willReturn(new SettingSaveResponse(8L, "디스크 세팅"));

        send(body(raid("1", RAID1_RULE + "," + NO_RAID_NVME_RULE)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(8));

        ArgumentCaptor<SettingSaveRequest> captor = ArgumentCaptor.forClass(SettingSaveRequest.class);
        verify(commandService).create(captor.capture());
        RaidConfigurationRequest install = (RaidConfigurationRequest) captor.getValue().processList().get(0);
        assertThat(install.getRaidCardId()).isEqualTo(1L);
        assertThat(install.getDiskGroups()).hasSize(2);
        assertThat(install.getDiskGroups().get(0).buildsRaid()).isTrue();
        assertThat(install.getDiskGroups().get(0).capacity().toDisplay()).isEqualTo("480 GB");
        assertThat(install.getDiskGroups().get(1).buildsRaid()).isFalse();
        assertThat(install.requiresRaidCard()).isTrue();
    }

    @Test
    @DisplayName("POST — RAID 없음 묶음만 · 카드 null → 201 (카드는 RAID 를 구성할 때만 요구)")
    void create_noRaidRuleWithoutCard_returns201() throws Exception {
        given(commandService.create(any())).willReturn(new SettingSaveResponse(9L, "디스크 세팅"));

        send(body(raid("null", NO_RAID_NVME_RULE))).andExpect(status().isCreated());
    }

    @Test
    @DisplayName("POST — diskGroups · raidCardId 키 없이 type + volumePriorities 만 → 201 (빈 목록 · null 로 읽힌다) · OS 설치와 함께 보내도 결합 규칙 없음")
    void create_bareTypeAndWithOsInstall_returns201() throws Exception {
        given(commandService.create(any())).willReturn(new SettingSaveResponse(10L, "디스크 세팅"));
        // U4-1-2 — volumePriorities 는 필수(@NotNull)라 키가 있어야 한다. 빈 배열은 "우선순위 없음 = 열거 순서" 라는 명시적 값.
        String legacy = "{\"type\": \"RAID_CONFIGURATION\", \"volumePriorities\": []}";

        send(body(legacy)).andExpect(status().isCreated());

        ArgumentCaptor<SettingSaveRequest> captor = ArgumentCaptor.forClass(SettingSaveRequest.class);
        verify(commandService).create(captor.capture());
        RaidConfigurationRequest install = (RaidConfigurationRequest) captor.getValue().processList().get(0);
        assertThat(install.getRaidCardId()).isNull();
        assertThat(install.getDiskGroups()).isEmpty();
    }

    // ==== 400 ================================================================================

    @Test
    @DisplayName("POST — RAID 묶음 + 카드 null → 400 fieldErrors[processList[0].raidCardPresentWhenRequired] · 서비스 미호출")
    void create_raidRuleWithoutCard_returns400() throws Exception {
        send(body(raid("null", RAID1_RULE)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[?(@.field == 'processList[0].raidCardPresentWhenRequired')]").exists());
        verify(commandService, never()).create(any());
    }

    @Test
    @DisplayName("POST — 카드가 못 만드는 레벨(DiskGroupRules 1) → 400 fieldErrors[diskGroups] 에 blockReasonFor 문구")
    void create_unsupportedLevel_returns400_fieldBound() throws Exception {
        given(commandService.create(any())).willThrow(InvalidDiskGroupException.unsupportedLevel(1,
                "RAID5 를 만들 수 없는 카드입니다 — 지원하는 RAID 레벨은 RAID0 · RAID1 입니다."));

        send(body(raid("1", RAID1_RULE)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[?(@.field == 'diskGroups')].message")
                        .value(org.hamcrest.Matchers.hasItem(org.hamcrest.Matchers.containsString("RAID5 를 만들 수 없는 카드"))));
    }

    @Test
    @DisplayName("POST — 최소 디스크 미달(DiskGroupRules 2) · 중복 규칙(4) · HDD×NVMe(6) → 400 fieldErrors[diskGroups]")
    void create_tooFewDisksAndDuplicate_return400() throws Exception {
        given(commandService.create(any())).willThrow(InvalidDiskGroupException.tooFewDisks(1,
                com.example.serverprovision.management.raidcard.enums.RaidLevel.RAID0, 2, 1));
        send(body(raid("1", RAID1_RULE)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[?(@.field == 'diskGroups')].message")
                        .value(org.hamcrest.Matchers.hasItem(org.hamcrest.Matchers.containsString("디스크 2개 이상"))));

        // 이미 던지도록 스터빙된 mock 은 given() 안에서 호출하면 그 예외가 튀어나온다 — willThrow().given() 형태로 재스터빙.
        org.mockito.BDDMockito.willThrow(InvalidDiskGroupException.duplicateRule(3, 1)).given(commandService).create(any());
        send(body(raid("1", RAID1_RULE)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[?(@.field == 'diskGroups')].message")
                        .value(org.hamcrest.Matchers.hasItem(org.hamcrest.Matchers.containsString("3번 묶음이 1번 묶음과 같은 규칙"))));

        // 규칙 6(CP7 검수) — HDD × NVMe
        org.mockito.BDDMockito.willThrow(InvalidDiskGroupException.incompatibleTransport(1, "HDD", "NVMe")).given(commandService).create(any());
        send(body(raid("1", RAID1_RULE)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[?(@.field == 'diskGroups')].message")
                        .value(org.hamcrest.Matchers.hasItem(org.hamcrest.Matchers.containsString("HDD 에는 NVMe 전송 방식이 없습니다"))));
    }

    @Test
    @DisplayName("POST — 용량 SPECIFIED 인데 크기 없음 → @AssertTrue 400 (중첩 record 경로) · 개수 0 → @Min 400")
    void create_capacityAndCountLayerA_return400() throws Exception {
        String noSize = RAID1_RULE.replace("\"size\": 480, \"unit\": \"GB\"", "\"size\": null, \"unit\": null");
        send(body(raid("1", noSize)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[?(@.field == 'processList[0].diskGroups[0].capacity.modeConsistent')]").exists());

        String zeroCount = RAID1_RULE.replace("\"value\": 2", "\"value\": 0");
        send(body(raid("1", zeroCount)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[?(@.field == 'processList[0].diskGroups[0].count.value')]").exists());
        verify(commandService, never()).create(any());
    }

    @Test
    @DisplayName("POST — 잘못된 enum 값(transport=USB) → 역직렬화 실패 400")
    void create_badEnum_returns400() throws Exception {
        send(body(raid("1", RAID1_RULE.replace("\"SATA\"", "\"USB\"")))).andExpect(status().isBadRequest());
        verify(commandService, never()).create(any());
    }

    // ==== 404 · 409 ==========================================================================

    @Test
    @DisplayName("POST — 없는/삭제된 카드 id → 404 (RaidCardNotFoundException 재사용)")
    void create_unknownCard_returns404() throws Exception {
        given(commandService.create(any())).willThrow(new RaidCardNotFoundException(99L));
        send(body(raid("99", RAID1_RULE))).andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("POST — disabled 카드 → 409 fieldErrors[raidCardId] (DisabledResourceReferenceException 재사용)")
    void create_disabledCard_returns409_fieldBound() throws Exception {
        given(commandService.create(any())).willThrow(new DisabledResourceReferenceException("raidCardId", "RAID 카드 GIGABYTE CRA3338"));
        send(body(raid("1", RAID1_RULE)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.fieldErrors[?(@.field == 'raidCardId')]").exists());
    }

    @Test
    @DisplayName("POST — RAID_CONFIGURATION 을 두 번 → @AssertTrue processTypeUnique 400 (v2 B8)")
    void create_duplicateRaidStep_returns400() throws Exception {
        send(body(raid("null", NO_RAID_NVME_RULE) + "," + raid("null", NO_RAID_NVME_RULE)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[?(@.field == 'processTypeUnique')]").exists());
        verify(commandService, never()).create(any());
    }

    // ==== U4-1-2 — 역할 · 볼륨 우선순위 · 정의서 수준 OS 판정 ======================================

    private static final String RHEL_INSTALL = """
            {"type": "OS_INSTALLATION", "osFamily": "RHEL_BASED", "isoId": 100, "osMetadataId": 1,
             "timezone": {"timezone": "Asia/Seoul", "isUTC": true},
             "partitions": [{"mountPoint": "/", "fileSystem": "EXT4", "size": 0, "sizeUnit": "GB", "isGrow": true}],
             "rootPassword": {"password": "pw1"}, "users": [], "environmentId": 1, "packageGroupIds": []}
            """;

    /** RAID 구성 단계 — 우선순위 행까지 갈아 끼우는 변형. */
    private static String raidWithPriorities(String raidCardId, String diskGroups, String priorities) {
        return """
                {"type": "RAID_CONFIGURATION", "raidCardId": %s, "diskGroups": [%s], "volumePriorities": %s}
                """.formatted(raidCardId, diskGroups, priorities);
    }

    private static String ruleWithRole(String role) {
        return RAID1_RULE.replace("\"role\": \"BY_PRIORITY\"", "\"role\": \"" + role + "\"");
    }

    @Test
    @DisplayName("POST — 역할 4 종 중 '영역 할당 없음' · 기본 5 행 → 201, 계약에 role · volumePriorities 가 그대로 실린다")
    void create_withRoleNoneAndDefaultPriorities_returns201() throws Exception {
        given(commandService.create(any())).willReturn(new SettingSaveResponse(11L, "디스크 세팅"));
        String defaults = """
                [{"diskType": "SSD", "transport": "NVME", "capacityOrder": "SMALLER_FIRST"},
                 {"diskType": "SSD", "transport": "SAS",  "capacityOrder": "SMALLER_FIRST"},
                 {"diskType": "SSD", "transport": "SATA", "capacityOrder": "LARGER_FIRST"},
                 {"diskType": "HDD", "transport": "SAS",  "capacityOrder": "SMALLER_FIRST"},
                 {"diskType": "HDD", "transport": "SATA", "capacityOrder": "SMALLER_FIRST"}]
                """;

        send(body(raidWithPriorities("1", ruleWithRole("NONE") + "," + NO_RAID_NVME_RULE, defaults)))
                .andExpect(status().isCreated());

        ArgumentCaptor<SettingSaveRequest> captor = ArgumentCaptor.forClass(SettingSaveRequest.class);
        verify(commandService).create(captor.capture());
        RaidConfigurationRequest raid = (RaidConfigurationRequest) captor.getValue().processList().get(0);
        assertThat(raid.getDiskGroups().get(0).role()).isEqualTo(com.example.serverprovision.provisioning.setting.enums.DiskGroupRole.NONE);
        assertThat(raid.getDiskGroups().get(0).isOsFixed()).isFalse();
        assertThat(raid.getVolumePriorities()).hasSize(5);
        assertThat(raid.getVolumePriorities().get(2).toDisplay()).isEqualTo("SSD · SATA · 큰 용량부터");
    }

    @Test
    @DisplayName("POST — role 누락 · volumePriorities 누락 → 400 (processList[0].diskGroups[0].role · processList[0].volumePriorities)")
    void create_missingRoleOrPriorities_returns400() throws Exception {
        String noRole = RAID1_RULE.replace(", \"role\": \"BY_PRIORITY\"", "");
        send(body(raidWithPriorities("1", noRole, "[]")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[?(@.field == 'processList[0].diskGroups[0].role')]").exists());

        String noPriorities = """
                {"type": "RAID_CONFIGURATION", "raidCardId": 1, "diskGroups": [%s]}
                """.formatted(RAID1_RULE);
        send(body(noPriorities))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[?(@.field == 'processList[0].volumePriorities')]").exists());
        verify(commandService, never()).create(any());
    }

    @Test
    @DisplayName("POST — 우선순위 행: AUTO(concrete) · HDD×NVMe(transportCompatible) · 중복(volumePriorityDistinct) → 400 각 경로")
    void create_badPriorityRows_returns400() throws Exception {
        String auto = "[{\"diskType\": \"AUTO\", \"transport\": \"SATA\", \"capacityOrder\": \"SMALLER_FIRST\"}]";
        send(body(raidWithPriorities("1", RAID1_RULE, auto)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[?(@.field == 'processList[0].volumePriorities[0].concrete')]").exists());

        String hddNvme = "[{\"diskType\": \"HDD\", \"transport\": \"NVME\", \"capacityOrder\": \"SMALLER_FIRST\"}]";
        send(body(raidWithPriorities("1", RAID1_RULE, hddNvme)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[?(@.field == 'processList[0].volumePriorities[0].transportCompatible')]").exists());

        String dup = "[{\"diskType\": \"SSD\", \"transport\": \"SATA\", \"capacityOrder\": \"SMALLER_FIRST\"},"
                + " {\"diskType\": \"SSD\", \"transport\": \"SATA\", \"capacityOrder\": \"LARGER_FIRST\"}]";
        send(body(raidWithPriorities("1", RAID1_RULE, dup)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[?(@.field == 'processList[0].volumePriorityDistinct')]").exists());
        verify(commandService, never()).create(any());
    }

    @Test
    @DisplayName("POST — OS 고정 묶음 둘(DiskGroupRules 7) → 400 fieldErrors[diskGroups] 에 '이미 OS 영역으로 고정' 문구")
    void create_multipleOsRules_returns400_fieldBound() throws Exception {
        given(commandService.create(any())).willThrow(InvalidDiskGroupException.multipleOsRules(2, 1));

        send(body(raid("1", ruleWithRole("OS") + "," + NO_RAID_NVME_RULE.replace("\"role\": \"BY_PRIORITY\"", "\"role\": \"OS\""))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[?(@.field == 'diskGroups')].message")
                        .value(org.hamcrest.Matchers.hasItem(org.hamcrest.Matchers.containsString("1번 묶음이 이미 OS 영역으로 고정"))));
    }

    @Test
    @DisplayName("POST — OS 설치 단계 + 우선순위 0 행 + OS 고정 없음 → 400 fieldErrors[osVolumeDeterminable] · OS 고정이 있거나 OS 설치가 없으면 201")
    void create_osVolumeDeterminable() throws Exception {
        given(commandService.create(any())).willReturn(new SettingSaveResponse(12L, "디스크 세팅"));

        // 사용자 확정(OQ2): OS 설치가 있으면 어느 볼륨이 OS 인지 정의서에서 정해져야 한다.
        send(body(raidWithPriorities("1", RAID1_RULE, "[]") + "," + RHEL_INSTALL))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[?(@.field == 'osVolumeDeterminable')]").exists());
        // OQ3(권장 채택): 행이 있어도 OS 후보 규칙이 없으면(전부 Data/없음) 같은 400.
        send(body(raidWithPriorities("1", ruleWithRole("DATA"), PRIORITY_ROWS) + "," + RHEL_INSTALL))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[?(@.field == 'osVolumeDeterminable')]").exists());
        verify(commandService, never()).create(any());

        // OS 고정 묶음이 있으면 0 행이어도 통과 · OS 설치 단계가 없으면 0 행 + 우선순위에 따름도 통과.
        send(body(raidWithPriorities("1", ruleWithRole("OS"), "[]") + "," + RHEL_INSTALL)).andExpect(status().isCreated());
        send(body(raidWithPriorities("1", RAID1_RULE, "[]"))).andExpect(status().isCreated());
    }
}
