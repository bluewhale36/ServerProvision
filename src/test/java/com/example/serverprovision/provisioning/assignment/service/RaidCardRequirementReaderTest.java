package com.example.serverprovision.provisioning.assignment.service;

import com.example.serverprovision.execution.engine.raid.RaidInventory;
import com.example.serverprovision.execution.entity.GuestServer;
import com.example.serverprovision.execution.entity.GuestServerDetail;
import com.example.serverprovision.management.raidcard.entity.RaidCard;
import com.example.serverprovision.management.raidcard.repository.RaidCardRepository;
import com.example.serverprovision.management.raidcard.vo.PciSubsystemId;
import com.example.serverprovision.provisioning.assignment.vo.RequiredRaidCard;
import com.example.serverprovision.provisioning.setting.dto.request.RaidConfigurationRequest;
import com.example.serverprovision.provisioning.setting.dto.request.VolumePriorityRuleRequest;
import com.example.serverprovision.provisioning.setting.entity.SettingDefinition;
import com.example.serverprovision.provisioning.setting.entity.SettingProcess;
import com.example.serverprovision.provisioning.setting.repository.SettingDefinitionRepository;
import com.example.serverprovision.provisioning.setting.vo.ProcessPayload;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;

/** R20 — RAID 카드 대조의 두 재료(지정 카드 · 저장 인벤토리)를 읽는 자리. */
@ExtendWith(MockitoExtension.class)
class RaidCardRequirementReaderTest {

    @Mock SettingDefinitionRepository definitionRepository;
    @Mock RaidCardRepository raidCardRepository;
    RaidCardRequirementReader reader;

    @BeforeEach
    void setUp() {
        reader = new RaidCardRequirementReader(definitionRepository, raidCardRepository, new ObjectMapper());
    }

    private static RaidConfigurationRequest raid(Long raidCardId) {
        return new RaidConfigurationRequest(raidCardId, List.of(), VolumePriorityRuleRequest.defaults(), null);
    }

    private static SettingDefinition definition(Long id, RaidConfigurationRequest raidOrNull) {
        SettingDefinition definition = mock(SettingDefinition.class);
        lenient().when(definition.getId()).thenReturn(id);
        List<SettingProcess> processes = raidOrNull == null ? List.of() : List.of(process(raidOrNull));
        given(definition.getProcesses()).willReturn(processes);
        return definition;
    }

    private static SettingProcess process(RaidConfigurationRequest raid) {
        SettingProcess process = mock(SettingProcess.class);
        given(process.getPayload()).willReturn(new ProcessPayload(raid));
        return process;
    }

    private static GuestServerDetail detail(String raidInventoryJson) {
        GuestServerDetail detail = mock(GuestServerDetail.class);
        lenient().when(detail.getRaidInventoryJson()).thenReturn(raidInventoryJson);
        GuestServer guest = mock(GuestServer.class);
        lenient().when(guest.getId()).thenReturn(UUID.randomUUID());
        lenient().when(detail.getGuestServer()).thenReturn(guest);
        return detail;
    }

    @Test
    @DisplayName("지정 카드 실존 — Subsystem(표시형) · 모델명 · registered")
    void requiredOf_registeredCard() {
        RaidCard card = mock(RaidCard.class);
        given(card.getPciSubsystemId()).willReturn(PciSubsystemId.parse("[1000:9361]"));
        given(card.getModelName()).willReturn("MegaRAID 9361-8i");
        given(raidCardRepository.findById(2L)).willReturn(Optional.of(card));

        assertThat(reader.requiredOf(raid(2L)))
                .isEqualTo(new RequiredRaidCard(2L, "1000:9361", "MegaRAID 9361-8i", true));
    }

    @Test
    @DisplayName("지정 카드 영구 삭제 — registered=false · 표시명은 집행과 같은 '(사라진 카드 #id)'")
    void requiredOf_purgedCard() {
        given(raidCardRepository.findById(7L)).willReturn(Optional.empty());

        RequiredRaidCard required = reader.requiredOf(raid(7L));

        assertThat(required.registered()).isFalse();
        assertThat(required.displayName()).isEqualTo("(사라진 카드 #7)");
        assertThat(required.toTarget().pciSubsystemId()).isNull();
    }

    @Test
    @DisplayName("카드 미지정 — unspecified, 카드 저장소를 묻지 않는다")
    void requiredOf_unspecified() {
        assertThat(reader.requiredOf(raid(null)).specified()).isFalse();
    }

    @Test
    @DisplayName("정의서별 맵 — RAID 구성 단계가 없는 정의서는 맵에 없다")
    void requiredByDefinition_skipsDefinitionsWithoutRaidStep() {
        RaidCard card = mock(RaidCard.class);
        given(card.getPciSubsystemId()).willReturn(PciSubsystemId.parse("[1458:3008]"));
        given(card.getModelName()).willReturn("CRA3338");
        given(raidCardRepository.findById(1L)).willReturn(Optional.of(card));
        // mock 은 스텁 인자 밖에서 먼저 만든다(안에서 만들면 UnfinishedStubbingException)
        List<SettingDefinition> definitions = List.of(definition(10L, raid(1L)), definition(11L, null));
        given(definitionRepository.findAllById(List.of(10L, 11L))).willReturn(definitions);

        Map<Long, RequiredRaidCard> byDefinition = reader.requiredByDefinition(List.of(10L, 11L));

        assertThat(byDefinition).containsOnlyKeys(10L);
        assertThat(byDefinition.get(10L).pciSubsystemId()).isEqualTo("1458:3008");
    }

    @Test
    @DisplayName("저장 인벤토리 — 미수집 · 공백 · 손상 JSON 은 null(관용), 정상은 감지 카드를 읽는다")
    void inventoryOf_tolerant() {
        assertThat(reader.inventoryOf(null)).isNull();
        assertThat(reader.inventoryOf(detail(null))).isNull();
        assertThat(reader.inventoryOf(detail("  "))).isNull();
        assertThat(reader.inventoryOf(detail("{not json"))).isNull();

        RaidInventory inventory = reader.inventoryOf(detail(
                "{\"card\":{\"chipFamily\":\"MPT_IR\",\"pciSubsystemId\":\"1458:3008\",\"model\":\"SAS3008\",\"firmware\":\"15\"},"
                        + "\"disks\":[],\"volumes\":[]}"));
        assertThat(inventory.card().pciSubsystemId()).isEqualTo("1458:3008");
        assertThat(inventory.card().model()).isEqualTo("SAS3008");
    }
}
