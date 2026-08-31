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
    @Mock com.example.serverprovision.execution.repository.RaidVolumeRepository raidVolumeRepository;
    @Mock com.example.serverprovision.execution.engine.phase.PhaseCursorAdvancer phaseCursorAdvancer;
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
    @DisplayName("지시 — 인벤토리 미적재면 RAID_INVENTORY, 적재 + 계획 창 밖(empty)이면 WAIT")
    void directive_byInventoryPresence() {
        GuestServerDetail detail = stubDetail();
        given(detail.getRaidInventoryJson()).willReturn(null);
        assertThat(executor.directiveFor(guest(), progress())).isEqualTo(AgentDirective.RAID_INVENTORY);

        given(detail.getRaidInventoryJson()).willReturn("{}");   // planFor 미스텁 = empty(창 밖) → WAIT
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
        verify(raidLedger, never()).failInstant(any(), any(), any(), anyString(), anyString(), any());
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

        verify(raidLedger).failInstant(eq(g), eq(p), eq(ProvisioningPhaseStep.RAID_INVENTORY_COLLECTING), eq(RaidLedger.CARD_MISMATCH), contains("1458:3008"), any());
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

        verify(raidLedger).failInstant(eq(g), eq(p), eq(ProvisioningPhaseStep.RAID_INVENTORY_COLLECTING), eq(RaidLedger.CARD_NOT_DETECTED), anyString(), any());
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
        verify(raidLedger, never()).failInstant(any(), any(), any(), anyString(), anyString(), any());
    }

    @Test
    @DisplayName("REPORT_UNPARSABLE — 해석 불가는 실패로 남긴다(진단의 관용 루프와 다른 선택, plan §7)")
    void consume_unparsable_failsLedger() {
        GuestServer g = guest();
        ProvisioningProgress p = progress();
        given(inventoryParser.parse(any()))
                .willThrow(new RaidInventoryParser.ReportUnparsableException("깨진 봉투", null));

        executor.onStepClosed(g, p, inventoryStep(g));

        verify(raidLedger).failInstant(eq(g), eq(p), eq(ProvisioningPhaseStep.RAID_INVENTORY_COLLECTING), eq(RaidLedger.REPORT_UNPARSABLE), anyString(), any());
    }

    @Test
    @DisplayName("소비 대상 아님 — RAID_INVENTORY_COLLECTING 외 step 은 no-op")
    void consume_otherStep_noop() {
        GuestServer g = guest();
        executor.onStepClosed(g, progress(),
                ProvisioningHistory.openRunning(g, ProvisioningPhaseStep.RAID_APPLYING, LocalDateTime.now()));
        verify(inventoryParser, never()).parse(any());
    }

    // ==== E3.5-3 — 집행 · 검증 상태기계 (진리표 V1~V9) ====

    @org.junit.jupiter.api.Nested
    @DisplayName("집행 지시 상태기계 (E3.5-3)")
    class ApplyDirective {

        private final com.example.serverprovision.management.raidcard.enums.RaidLevel RAID1 =
                com.example.serverprovision.management.raidcard.enums.RaidLevel.RAID1;

        private RaidInventory inventoryWith(RaidExistingVolume... volumes) {
            return new RaidInventory(new DetectedRaidCard(RaidChipFamily.MEGARAID, "1000:9361", "9361-8i", "fw"),
                    List.of(new RaidPhysicalDisk("252:0", "SSD", "SATA", "446.625 GB", "Onln", "M", "S", null),
                            new RaidPhysicalDisk("252:1", "SSD", "SATA", "446.625 GB", "Onln", "M", "S", null)),
                    List.of(volumes));
        }

        private RaidExistingVolume volumeNamed(String name) {
            return new RaidExistingVolume("VD0", "RAID1", "446.625 GB", "Optl", name, List.of("252:0", "252:1"));
        }

        private RaidPlan planOf() {
            return new RaidPlan(true,
                    List.of(new PlannedVolume("spvR1V1", RAID1, List.of("252:0", "252:1"),
                            479L, PlannedVolumeRole.OS, 1)),
                    List.of(), List.of(), List.of(), null);
        }

        private GuestServerDetail stubStored(RaidInventory inventory) {
            // 직렬화는 given(...) 밖에서 — 스텁 구성 중 @Spy 실호출이 끼면 UnfinishedStubbing 이 된다
            String json = objectMapper.writeValueAsString(inventory);
            GuestServerDetail detail = stubDetail();
            given(detail.getRaidInventoryJson()).willReturn(json);
            return detail;
        }

        @Test
        @DisplayName("V1 — 계획 성립 → PLANNED 동결 + RAID_APPLY")
        void planReady_freezesAndInstructsApply() {
            stubStored(inventoryWith());
            given(resolutionProvider.planFor(eq(GUEST_ID), any(), eq(RaidExistingConfigPolicy.DESTROY)))
                    .willReturn(Optional.of(planOf()));

            assertThat(executor.directiveFor(guest(), progress())).isEqualTo(AgentDirective.RAID_APPLY);
            verify(raidLedger).freezePlanned(any(), contains("spvR1V1"), any());
        }

        @Test
        @DisplayName("V1 — payload 는 동결본에서 파생된다(지시와 같은 SSOT)")
        void payload_derivedFromFrozenPlan() {
            String frozenMeta = "{\"reason\":\"PLANNED\",\"plan\":" + objectMapper.writeValueAsString(planOf()) + "}";
            given(raidLedger.latestFrozenPlanMeta(GUEST_ID)).willReturn(Optional.of(frozenMeta));

            RaidApplyPayload payload = executor.raidApplyPayloadFor(guest(), progress());

            assertThat(payload.deleteExisting()).isTrue();
            assertThat(payload.volumes()).singleElement()
                    .satisfies(v -> assertThat(v.name()).isEqualTo("spvR1V1"));
        }

        @Test
        @DisplayName("V2 — 외부 기존 볼륨 + 축 부재 = WAIT + POLICY_UNDECIDED 보류(계획 산출 없음)")
        void foreignVolume_holds() {
            stubStored(inventoryWith(volumeNamed("legacy-vd")));

            assertThat(executor.directiveFor(guest(), progress())).isEqualTo(AgentDirective.WAIT);
            verify(raidLedger).holdInstant(any(), eq(ProvisioningPhaseStep.RAID_APPLYING),
                    eq(RaidLedger.POLICY_UNDECIDED), contains("외부 기존 볼륨 1개"), any());
            verify(resolutionProvider, never()).planFor(any(), any(), any());
        }

        @Test
        @DisplayName("V2 — 우리 잔여(spvR*)만 있으면 보류하지 않고 재구성 경로로 간다")
        void ourResidue_proceedsToApply() {
            stubStored(inventoryWith(volumeNamed("spvR1V1")));
            given(resolutionProvider.planFor(eq(GUEST_ID), any(), eq(RaidExistingConfigPolicy.DESTROY)))
                    .willReturn(Optional.of(planOf()));

            assertThat(executor.directiveFor(guest(), progress())).isEqualTo(AgentDirective.RAID_APPLY);
            verify(raidLedger, never()).holdInstant(any(), any(), anyString(), anyString(), any());
        }

        @Test
        @DisplayName("V3 — 계획 거절 = WAIT + PLAN_REJECTED 보류 · 실패 전환 없음")
        void planRejected_holdsWithoutFailure() {
            stubStored(inventoryWith());
            given(resolutionProvider.planFor(eq(GUEST_ID), any(), eq(RaidExistingConfigPolicy.DESTROY)))
                    .willReturn(Optional.of(new RaidPlanRejection(RaidPlanRejection.VOLUME_LIMIT, "3 > 2")));
            ProvisioningProgress progress = progress();

            assertThat(executor.directiveFor(guest(), progress)).isEqualTo(AgentDirective.WAIT);
            verify(raidLedger).holdInstant(any(), eq(ProvisioningPhaseStep.RAID_APPLYING),
                    eq(RaidLedger.PLAN_REJECTED), contains("VOLUME_LIMIT"), any());
            assertThat(progress.isFailed()).isFalse();
        }

        @Test
        @DisplayName("V4 — 집행 성공 close 가 원장에 있으면 RAID_VERIFY(재채집 지시)")
        void appliedSucceeded_instructsVerify() {
            GuestServerDetail detail = stubDetail();
            given(detail.getRaidInventoryJson()).willReturn("{}");
            ProvisioningHistory applied = ProvisioningHistory.openRunning(
                    guest(), ProvisioningPhaseStep.RAID_APPLYING, LocalDateTime.now());
            applied.close(com.example.serverprovision.execution.enums.ProvisioningStatus.SUCCEEDED,
                    null, LocalDateTime.now());
            given(raidLedger.latestOf(GUEST_ID, ProvisioningPhaseStep.RAID_APPLYING))
                    .willReturn(Optional.of(applied));

            assertThat(executor.directiveFor(guest(), progress())).isEqualTo(AgentDirective.RAID_VERIFY);
            verify(resolutionProvider, never()).planFor(any(), any(), any());
        }

        @Test
        @DisplayName("V8 — 직전 집행 실패 후 재진입은 새 동결 + 재집행(V1 재현)")
        void failedApply_replansOnRetry() {
            stubStored(inventoryWith());
            ProvisioningHistory failed = ProvisioningHistory.openRunning(
                    guest(), ProvisioningPhaseStep.RAID_APPLYING, LocalDateTime.now());
            failed.close(com.example.serverprovision.execution.enums.ProvisioningStatus.FAILED,
                    null, LocalDateTime.now());
            given(raidLedger.latestOf(GUEST_ID, ProvisioningPhaseStep.RAID_APPLYING))
                    .willReturn(Optional.of(failed));
            given(resolutionProvider.planFor(eq(GUEST_ID), any(), eq(RaidExistingConfigPolicy.DESTROY)))
                    .willReturn(Optional.of(planOf()));

            assertThat(executor.directiveFor(guest(), progress())).isEqualTo(AgentDirective.RAID_APPLY);
            verify(raidLedger).freezePlanned(any(), anyString(), any());
        }

        @Test
        @DisplayName("빈 계획(묶음 0 정의서) — 집행할 것이 없어 phase 완주 전진 + REBOOT")
        void emptyPlan_advancesPhase() {
            stubStored(inventoryWith());
            given(resolutionProvider.planFor(eq(GUEST_ID), any(), eq(RaidExistingConfigPolicy.DESTROY)))
                    .willReturn(Optional.of(new RaidPlan(false, List.of(), List.of(), List.of(), List.of(), null)));
            ProvisioningProgress progress = progress();

            assertThat(executor.directiveFor(guest(), progress)).isEqualTo(AgentDirective.REBOOT);
            verify(phaseCursorAdvancer).advanceOrComplete(eq(progress), eq(GUEST_ID), any());
        }

        @Test
        @DisplayName("저장 인벤토리 손상 — 관용 WAIT(집행을 임의 진행하지 않는다)")
        void corruptStoredInventory_waits() {
            GuestServerDetail detail = stubDetail();
            given(detail.getRaidInventoryJson()).willReturn("not-json");

            assertThat(executor.directiveFor(guest(), progress())).isEqualTo(AgentDirective.WAIT);
        }
    }

    @org.junit.jupiter.api.Nested
    @DisplayName("검증 보고 소비 (E3.5-3)")
    class VerifyConsumption {

        private final com.example.serverprovision.management.raidcard.enums.RaidLevel RAID1 =
                com.example.serverprovision.management.raidcard.enums.RaidLevel.RAID1;

        private ProvisioningHistory verifyStep(GuestServer g) {
            return ProvisioningHistory.openRunning(g, ProvisioningPhaseStep.RAID_VERIFYING, LocalDateTime.now());
        }

        private RaidPlan frozenPlan() {
            return new RaidPlan(true,
                    List.of(new PlannedVolume("spvR1V1", RAID1, List.of("252:0", "252:1"),
                            479L, PlannedVolumeRole.OS, 1)),
                    List.of(new PlannedPassthrough("252:4", 480L, PlannedVolumeRole.DATA, 2)),
                    List.of(), List.of(), null);
        }

        private void stubFrozen() {
            String meta = "{\"reason\":\"PLANNED\",\"plan\":" + objectMapper.writeValueAsString(frozenPlan()) + "}";
            given(raidLedger.latestFrozenPlanMeta(GUEST_ID)).willReturn(Optional.of(meta));
        }

        private RaidInventory observedMatching() {
            return new RaidInventory(new DetectedRaidCard(RaidChipFamily.MEGARAID, "1000:9361", "9361-8i", "fw"),
                    List.of(),
                    List.of(new RaidExistingVolume("VD0", "RAID1", "446.625 GB", "Optl", "spvR1V1",
                            List.of("252:0", "252:1"))));
        }

        @Test
        @DisplayName("V5 — 일치: raid_volume replace 기록(볼륨 + 단독 디스크) · 인벤토리 재적재 · 커서 전진")
        void matching_recordsAndAdvances() {
            GuestServer g = guest();
            ProvisioningProgress p = progress();
            given(inventoryParser.parse(any())).willReturn(observedMatching());
            stubFrozen();
            GuestServerDetail detail = stubDetail();

            executor.onStepClosed(g, p, verifyStep(g));

            verify(raidVolumeRepository).deleteByGuestServer_Id(GUEST_ID);
            org.mockito.ArgumentCaptor<List<com.example.serverprovision.execution.entity.RaidVolume>> captor =
                    org.mockito.ArgumentCaptor.captor();
            verify(raidVolumeRepository).saveAll(captor.capture());
            assertThat(captor.getValue()).hasSize(2);   // 볼륨 1 + 패스스루 1
            assertThat(captor.getValue().get(0).getName()).isEqualTo("spvR1V1");
            assertThat(captor.getValue().get(0).getState()).isEqualTo("Optl");   // 재채집 상태 원문
            verify(detail).enrichRaidInventory(contains("spvR1V1"));
            verify(phaseCursorAdvancer).advanceOrComplete(eq(p), eq(GUEST_ID), any());
        }

        @Test
        @DisplayName("V6 — 불일치: RESULT_MISMATCH 실패 · 기록 없음")
        void mismatch_failsWithoutRecording() {
            GuestServer g = guest();
            ProvisioningProgress p = progress();
            given(inventoryParser.parse(any())).willReturn(
                    new RaidInventory(null, List.of(), List.of()));   // 볼륨 0 ≠ 계획 1
            stubFrozen();

            executor.onStepClosed(g, p, verifyStep(g));

            verify(raidLedger).failInstant(eq(g), eq(p), eq(ProvisioningPhaseStep.RAID_VERIFYING),
                    eq(RaidLedger.RESULT_MISMATCH), contains("볼륨 수"), any());
            verify(raidVolumeRepository, never()).saveAll(any());
            verify(phaseCursorAdvancer, never()).advanceOrComplete(any(), any(), any());
        }

        @Test
        @DisplayName("V9 — 재채집 해석 불가: REPORT_UNPARSABLE 실패(VERIFYING step)")
        void unparsableVerification_fails() {
            GuestServer g = guest();
            ProvisioningProgress p = progress();
            given(inventoryParser.parse(any()))
                    .willThrow(new RaidInventoryParser.ReportUnparsableException("깨진 봉투", null));

            executor.onStepClosed(g, p, verifyStep(g));

            verify(raidLedger).failInstant(eq(g), eq(p), eq(ProvisioningPhaseStep.RAID_VERIFYING),
                    eq(RaidLedger.REPORT_UNPARSABLE), anyString(), any());
        }

        @Test
        @DisplayName("동결 계획 부재 — 집행 이력 손상은 RESULT_MISMATCH 로 정직하게 실패")
        void missingFrozenPlan_fails() {
            GuestServer g = guest();
            ProvisioningProgress p = progress();
            given(inventoryParser.parse(any())).willReturn(observedMatching());
            given(raidLedger.latestFrozenPlanMeta(GUEST_ID)).willReturn(Optional.empty());

            executor.onStepClosed(g, p, verifyStep(g));

            verify(raidLedger).failInstant(eq(g), eq(p), eq(ProvisioningPhaseStep.RAID_VERIFYING),
                    eq(RaidLedger.RESULT_MISMATCH), contains("동결 계획"), any());
        }

        @Test
        @DisplayName("RAID_APPLYING 성공 close 는 소비하지 않는다(no-op — 다음 지시는 판정이 낸다)")
        void applyingClose_isNoop() {
            GuestServer g = guest();
            executor.onStepClosed(g, progress(),
                    ProvisioningHistory.openRunning(g, ProvisioningPhaseStep.RAID_APPLYING, LocalDateTime.now()));

            verify(inventoryParser, never()).parse(any());
            verify(raidVolumeRepository, never()).saveAll(any());
        }
    }
}
