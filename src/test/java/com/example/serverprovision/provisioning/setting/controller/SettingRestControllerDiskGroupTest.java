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
             "count": {"mode": "EXACT", "value": 2}}
            """;
    private static final String NO_RAID_NVME_RULE = """
            {"raidLevel": null, "diskType": "SSD", "transport": "NVME",
             "capacity": {"mode": "AUTO", "size": null, "unit": null},
             "count": {"mode": "EXACT", "value": 1}}
            """;

    /** RAID 구성 단계(flat) — raidCardId · diskGroups 만 갈아 끼운다. */
    private static String raid(String raidCardId, String diskGroups) {
        return """
                {"type": "RAID_CONFIGURATION", "raidCardId": %s, "diskGroups": [%s]}
                """.formatted(raidCardId, diskGroups);
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
    @DisplayName("POST — diskGroups · raidCardId 키 없이 type 만 → 201 (빈 목록 · null 로 읽힌다) · OS 설치와 함께 보내도 결합 규칙 없음")
    void create_bareTypeAndWithOsInstall_returns201() throws Exception {
        given(commandService.create(any())).willReturn(new SettingSaveResponse(10L, "디스크 세팅"));
        String legacy = "{\"type\": \"RAID_CONFIGURATION\"}";

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
}
