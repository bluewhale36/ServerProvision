package com.example.serverprovision.execution.engine.raid;

import com.example.serverprovision.execution.config.PxeAssetsProperties;
import com.example.serverprovision.execution.entity.GuestServer;
import com.example.serverprovision.execution.entity.GuestServerDetail;
import com.example.serverprovision.execution.entity.ProvisioningHistory;
import com.example.serverprovision.execution.entity.ProvisioningProgress;
import com.example.serverprovision.execution.enums.AgentDirective;
import com.example.serverprovision.execution.enums.ProvisioningPhaseStep;
import com.example.serverprovision.execution.repository.GuestServerDetailRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * E3.5-1 — RAID 구성 실행기의 지시 판정과 인벤토리 소비(카드 대조 · 적재 · 원장 사유).
 * 파싱 자체는 {@link RaidInventoryParserTest}(실측 픽스처)가 검증하므로 여기서는 mock 으로 잘라
 * 대조 진리표를 고정한다.
 */
@ExtendWith(MockitoExtension.class)
class RaidConfigurationExecutorTest {

    private static final UUID GUEST_ID = UUID.randomUUID();

    @Mock PxeAssetsProperties properties;
    @Mock GuestServerDetailRepository guestServerDetailRepository;
    @Mock RaidInventoryParser inventoryParser;
    @Mock RaidConfigurationResolutionProvider resolutionProvider;
    @Mock RaidLedger raidLedger;
    @Spy ObjectMapper objectMapper = new ObjectMapper();
    @InjectMocks RaidConfigurationExecutor executor;

    private GuestServer guest() {
        return GuestServer.builder().id(GUEST_ID).systemUUID(UUID.randomUUID()).build();
    }

    private ProvisioningProgress progress() {
        ProvisioningProgress p = ProvisioningProgress.builder()
                .id(UUID.randomUUID())
                .currentStep(ProvisioningPhaseStep.RAID_INVENTORY_COLLECTING)
                .lastTransitionAt(LocalDateTime.now())
                .build();
        p.start(LocalDateTime.now());
        return p;
    }

    private ProvisioningHistory inventoryStep(GuestServer g) {
        return ProvisioningHistory.openRunning(g, ProvisioningPhaseStep.RAID_INVENTORY_COLLECTING, LocalDateTime.now());
    }

    private RaidInventory inventoryOf(String subsystem) {
        return new RaidInventory(new DetectedRaidCard(RaidChipFamily.MEGARAID, subsystem, "9361-8i", "fw"),
                List.of(), List.of());
    }

    private GuestServerDetail stubDetail() {
        GuestServerDetail detail = mock(GuestServerDetail.class);
        given(guestServerDetailRepository.findByServerIdWithBoardModel(GUEST_ID)).willReturn(Optional.of(detail));
        return detail;
    }

    // ==== directiveFor ====

    @Test
    @DisplayName("지시 — 인벤토리 미적재면 RAID_INVENTORY, 적재됐으면 WAIT(명시 대기 — 계획은 E3.5-2)")
    void directive_byInventoryPresence() {
        GuestServerDetail detail = stubDetail();
        given(detail.getRaidInventoryJson()).willReturn(null);
        assertThat(executor.directiveFor(guest(), progress())).isEqualTo(AgentDirective.RAID_INVENTORY);

        given(detail.getRaidInventoryJson()).willReturn("{}");
        assertThat(executor.directiveFor(guest(), progress())).isEqualTo(AgentDirective.WAIT);
    }

    // ==== onStepClosed — 대조 진리표 ====

    @Test
    @DisplayName("정합 — 지정 카드와 감지 Subsystem 일치 → 인벤토리 적재")
    void consume_match_enriches() {
        GuestServer g = guest();
        ProvisioningProgress p = progress();
        given(inventoryParser.parse(any())).willReturn(inventoryOf("1000:9361"));
        given(resolutionProvider.resolveFor(GUEST_ID))
                .willReturn(Optional.of(new RaidConfigurationTarget(7L, "1000:9361", "AVAGO 9361-8i")));
        GuestServerDetail detail = stubDetail();

        executor.onStepClosed(g, p, inventoryStep(g));

        verify(detail).enrichRaidInventory(contains("1000:9361"));
        verify(raidLedger, never()).failInstant(any(), any(), anyString(), anyString(), any());
    }

    @Test
    @DisplayName("CARD_MISMATCH — 지정 카드 ≠ 감지 카드 → 원장 실패 사유, 적재 없음")
    void consume_mismatch_failsLedger() {
        GuestServer g = guest();
        ProvisioningProgress p = progress();
        given(inventoryParser.parse(any())).willReturn(inventoryOf("1458:3008"));
        given(resolutionProvider.resolveFor(GUEST_ID))
                .willReturn(Optional.of(new RaidConfigurationTarget(7L, "1000:9361", "AVAGO 9361-8i")));

        executor.onStepClosed(g, p, inventoryStep(g));

        verify(raidLedger).failInstant(eq(g), eq(p), eq(RaidLedger.CARD_MISMATCH), contains("1458:3008"), any());
        verify(guestServerDetailRepository, never()).findByServerIdWithBoardModel(any());
    }

    @Test
    @DisplayName("CARD_NOT_DETECTED — 지정했는데 카드 미감지 → 원장 실패 사유")
    void consume_notDetected_failsLedger() {
        GuestServer g = guest();
        ProvisioningProgress p = progress();
        given(inventoryParser.parse(any())).willReturn(new RaidInventory(null, List.of(), List.of()));
        given(resolutionProvider.resolveFor(GUEST_ID))
                .willReturn(Optional.of(new RaidConfigurationTarget(7L, "1000:9361", "AVAGO 9361-8i")));

        executor.onStepClosed(g, p, inventoryStep(g));

        verify(raidLedger).failInstant(eq(g), eq(p), eq(RaidLedger.CARD_NOT_DETECTED), anyString(), any());
    }

    @Test
    @DisplayName("카드 미지정(raidCardId null) · 창 밖(empty) — 대조 없이 적재")
    void consume_noCardPremise_enriches() {
        GuestServer g = guest();
        ProvisioningProgress p = progress();
        given(inventoryParser.parse(any())).willReturn(inventoryOf("1458:3008"));
        given(resolutionProvider.resolveFor(GUEST_ID))
                .willReturn(Optional.of(new RaidConfigurationTarget(null, null, null)));
        GuestServerDetail detail = stubDetail();

        executor.onStepClosed(g, p, inventoryStep(g));
        verify(detail).enrichRaidInventory(anyString());

        given(resolutionProvider.resolveFor(GUEST_ID)).willReturn(Optional.empty());
        executor.onStepClosed(g, p, inventoryStep(g));
        verify(detail, org.mockito.Mockito.times(2)).enrichRaidInventory(anyString());
    }

    @Test
    @DisplayName("지정 카드의 Subsystem 미확보(자원 소실) — 대조 생략 · 적재(관용, WARN)")
    void consume_missingSubsystem_skipsCheck() {
        GuestServer g = guest();
        ProvisioningProgress p = progress();
        given(inventoryParser.parse(any())).willReturn(inventoryOf("1000:9361"));
        given(resolutionProvider.resolveFor(GUEST_ID))
                .willReturn(Optional.of(new RaidConfigurationTarget(7L, null, "(사라진 카드 #7)")));
        GuestServerDetail detail = stubDetail();

        executor.onStepClosed(g, p, inventoryStep(g));

        verify(detail).enrichRaidInventory(anyString());
        verify(raidLedger, never()).failInstant(any(), any(), anyString(), anyString(), any());
    }

    @Test
    @DisplayName("REPORT_UNPARSABLE — 해석 불가는 실패로 남긴다(진단의 관용 루프와 다른 선택, plan §7)")
    void consume_unparsable_failsLedger() {
        GuestServer g = guest();
        ProvisioningProgress p = progress();
        given(inventoryParser.parse(any()))
                .willThrow(new RaidInventoryParser.ReportUnparsableException("깨진 봉투", null));

        executor.onStepClosed(g, p, inventoryStep(g));

        verify(raidLedger).failInstant(eq(g), eq(p), eq(RaidLedger.REPORT_UNPARSABLE), anyString(), any());
    }

    @Test
    @DisplayName("소비 대상 아님 — RAID_INVENTORY_COLLECTING 외 step 은 no-op")
    void consume_otherStep_noop() {
        GuestServer g = guest();
        executor.onStepClosed(g, progress(),
                ProvisioningHistory.openRunning(g, ProvisioningPhaseStep.RAID_APPLYING, LocalDateTime.now()));
        verify(inventoryParser, never()).parse(any());
    }
}
