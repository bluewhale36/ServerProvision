package com.example.serverprovision.provisioning.assignment.service;

import com.example.serverprovision.execution.engine.raid.RaidConfigurationTarget;
import com.example.serverprovision.management.raidcard.entity.RaidCard;
import com.example.serverprovision.management.raidcard.repository.RaidCardRepository;
import com.example.serverprovision.management.raidcard.vo.PciSubsystemId;
import com.example.serverprovision.provisioning.assignment.entity.AssignedProcessSnapshot;
import com.example.serverprovision.provisioning.assignment.entity.SettingAssignmentSnapshot;
import com.example.serverprovision.provisioning.assignment.repository.SettingAssignmentSnapshotRepository;
import com.example.serverprovision.provisioning.setting.dto.request.RaidConfigurationRequest;
import com.example.serverprovision.provisioning.setting.dto.request.VolumePriorityRuleRequest;
import com.example.serverprovision.provisioning.setting.vo.ProcessPayload;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

/**
 * E3.5-1 — 활성 스냅샷의 RAID payload 에서 카드 전제를 꺼내는 SPI 구현. 소프트참조라
 * "카드 자원이 사라진 상태" 가 정상 입력이다(Subsystem null 로 나르고 엔진이 대조 생략).
 */
@ExtendWith(MockitoExtension.class)
class RaidConfigurationResolutionProviderImplTest {

    private static final UUID GUEST_ID = UUID.randomUUID();

    @Mock SettingAssignmentSnapshotRepository assignmentRepository;
    @Mock RaidCardRepository raidCardRepository;
    @InjectMocks RaidConfigurationResolutionProviderImpl provider;

    private void stubSnapshotWithRaid(Long raidCardId) {
        RaidConfigurationRequest raid = new RaidConfigurationRequest(
                raidCardId, List.of(), VolumePriorityRuleRequest.defaults());
        AssignedProcessSnapshot process = mock(AssignedProcessSnapshot.class);
        given(process.getPayload()).willReturn(new ProcessPayload(raid));
        SettingAssignmentSnapshot snapshot = mock(SettingAssignmentSnapshot.class);
        given(snapshot.getProcesses()).willReturn(List.of(process));
        given(assignmentRepository.findByGuestServer_IdAndSupersededAtIsNull(GUEST_ID))
                .willReturn(Optional.of(snapshot));
    }

    @Test
    @DisplayName("카드 지정 + 자원 실존 — Subsystem(toDisplay) · 모델명을 나른다")
    void resolve_cardPresent() {
        stubSnapshotWithRaid(7L);
        RaidCard card = mock(RaidCard.class);
        given(card.getPciSubsystemId()).willReturn(PciSubsystemId.parse("[1000:9361]"));
        given(card.getModelName()).willReturn("AVAGO MegaRAID 9361-8i");
        given(raidCardRepository.findById(7L)).willReturn(Optional.of(card));

        Optional<RaidConfigurationTarget> target = provider.resolveFor(GUEST_ID);

        assertThat(target).hasValueSatisfying(t -> {
            assertThat(t.raidCardId()).isEqualTo(7L);
            assertThat(t.pciSubsystemId()).isEqualTo("1000:9361");
            assertThat(t.cardModelName()).isEqualTo("AVAGO MegaRAID 9361-8i");
        });
    }

    @Test
    @DisplayName("카드 지정 + 자원 소실(소프트참조) — Subsystem null · '(사라진 카드 #id)'")
    void resolve_cardGone() {
        stubSnapshotWithRaid(7L);
        given(raidCardRepository.findById(7L)).willReturn(Optional.empty());

        assertThat(provider.resolveFor(GUEST_ID)).hasValueSatisfying(t -> {
            assertThat(t.pciSubsystemId()).isNull();
            assertThat(t.cardModelName()).isEqualTo("(사라진 카드 #7)");
        });
    }

    @Test
    @DisplayName("카드 미지정 — 단계는 있으므로 empty 가 아니라 null 필드 target")
    void resolve_noCardPremise() {
        stubSnapshotWithRaid(null);

        assertThat(provider.resolveFor(GUEST_ID)).hasValueSatisfying(t -> {
            assertThat(t.raidCardId()).isNull();
            assertThat(t.pciSubsystemId()).isNull();
        });
    }

    @Test
    @DisplayName("활성 할당 없음 · RAID 단계 없는 정의서 — empty(창 밖)")
    void resolve_outOfWindow() {
        given(assignmentRepository.findByGuestServer_IdAndSupersededAtIsNull(GUEST_ID))
                .willReturn(Optional.empty());
        assertThat(provider.resolveFor(GUEST_ID)).isEmpty();
    }
}
