package com.example.serverprovision.execution.service;

import com.example.serverprovision.execution.dto.response.GuestServerDetailResponse;
import com.example.serverprovision.execution.dto.response.GuestServerSummaryResponse;
import com.example.serverprovision.execution.entity.GuestServer;
import com.example.serverprovision.execution.entity.GuestServerDetail;
import com.example.serverprovision.execution.entity.HostNicBinding;
import com.example.serverprovision.execution.entity.ProvisioningProgress;
import com.example.serverprovision.execution.entity.ProvisioningHistory;
import com.example.serverprovision.execution.enums.DiscoveryStage;
import com.example.serverprovision.execution.enums.GuestServerStatus;
import com.example.serverprovision.execution.enums.IpSource;
import com.example.serverprovision.execution.enums.ProvisioningPhase;
import com.example.serverprovision.execution.enums.ProvisioningPhaseStep;
import com.example.serverprovision.execution.enums.ProvisioningStatus;
import com.example.serverprovision.execution.exception.GuestServerNotFoundException;
import com.example.serverprovision.execution.repository.GuestServerDetailRepository;
import com.example.serverprovision.execution.repository.GuestServerRepository;
import com.example.serverprovision.execution.repository.HostNicBindingRepository;
import com.example.serverprovision.execution.repository.ProvisioningProgressRepository;
import com.example.serverprovision.execution.repository.ProvisioningHistoryRepository;
import com.example.serverprovision.execution.vo.IpAddressVO;
import com.example.serverprovision.execution.vo.MacAddressVO;
import com.example.serverprovision.management.board.entity.BoardModel;
import com.example.serverprovision.management.board.enums.Vendor;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * U1 CP4 — {@link GuestServerQueryService} 단위 테스트. vendor·운영상태 도출과 매핑, 404, 빈 목록을 검증한다.
 */
@ExtendWith(MockitoExtension.class)
class GuestServerQueryServiceTest {

    @Mock GuestServerRepository guestServerRepository;
    @Mock GuestServerDetailRepository detailRepository;
    @Mock HostNicBindingRepository nicRepository;
    @Mock ProvisioningProgressRepository progressRepository;
    @Mock ProvisioningHistoryRepository provisioningHistoryRepository;
    // E2-1-b — 상세 조회가 펌웨어 해석을 한 번 돌린다. 이 파일의 시나리오는 펌웨어 단계와 무관하므로
    // "해당 없음"(empty)을 돌려주는 mock 으로 두고, 판정 자체는 FirmwareResolverTest 가 검증한다.
    @Mock com.example.serverprovision.execution.engine.firmware.FirmwareResolutionProvider firmwareResolutionProvider;
    @Mock com.example.serverprovision.execution.engine.raid.RaidConfigurationResolutionProvider raidConfigurationResolutionProvider;
    @Mock com.example.serverprovision.execution.repository.RaidVolumeRepository raidVolumeRepository;
    @Mock com.example.serverprovision.execution.engine.phase.HoldTtlPolicy holdTtlPolicy;
    @Mock RetryPolicy retryPolicy;

    @org.mockito.Spy tools.jackson.databind.ObjectMapper objectMapper = new tools.jackson.databind.ObjectMapper();

    @InjectMocks GuestServerQueryService service;

    private GuestServer server(UUID id, String name, LocalDateTime decommissionedAt) {
        return GuestServer.builder().id(id).systemUUID(UUID.randomUUID())
                .name(name).modelName("RE2108").serialNumber("RE2108X").memo("memo")
                .decommissionedAt(decommissionedAt).build();
    }

    private GuestServerDetail detail(GuestServer s) {
        return GuestServerDetail.builder().id(UUID.randomUUID()).guestServer(s)
                .boardModel(BoardModel.builder().vendor(Vendor.GIGABYTE).modelName("MS73-HB1-000").build())
                .boardSerial("GB-001").discoveryStage(DiscoveryStage.IPXE_REGISTERED).build();
    }

    private HostNicBinding nic(GuestServer s) {
        return HostNicBinding.builder().id(UUID.randomUUID()).guestServer(s)
                .macAddress(MacAddressVO.of("aa:bb:cc:dd:ee:ff")).ipAddress(IpAddressVO.of("10.20.3.11"))
                .ipSource(IpSource.DHCP).isPrimary(true).build();
    }

    private ProvisioningProgress progress(GuestServer s, ProvisioningPhase phase) {
        // E1-0a — 진행 상태는 개시(startedAt) 선행 전제(DEC-26). ES-2: 커서는 step 저장이라 진입 step 으로 변환.
        return ProvisioningProgress.builder().id(UUID.randomUUID()).guestServer(s)
                .currentStep(com.example.serverprovision.execution.enums.ProvisioningPhaseStep.entryOf(phase))
                .lastTransitionAt(LocalDateTime.now())
                .startedAt(LocalDateTime.now()).build();
    }

    @Test
    @DisplayName("findAll — vendor 는 boardModel 에서, status 는 progress 에서 도출")
    void findAll_derivesVendorAndStatus() {
        UUID id = UUID.randomUUID();
        GuestServer s = server(id, "web-01", null);
        given(guestServerRepository.findAllByOrderByCreatedAtDesc()).willReturn(List.of(s));
        given(detailRepository.findAllByServerIdInWithBoardModel(anyList())).willReturn(List.of(detail(s)));
        given(nicRepository.findPrimaryByServerIdIn(anyList())).willReturn(List.of(nic(s)));
        given(progressRepository.findAllByGuestServer_IdIn(anyList()))
                .willReturn(List.of(progress(s, ProvisioningPhase.OS_INSTALLING)));

        List<GuestServerSummaryResponse> result = service.findAll();

        assertThat(result).hasSize(1);
        GuestServerSummaryResponse row = result.get(0);
        assertThat(row.name()).isEqualTo("web-01");
        assertThat(row.vendor()).isEqualTo(Vendor.GIGABYTE);                 // 도출
        assertThat(row.boardModelName()).isEqualTo("MS73-HB1-000");
        assertThat(row.status()).isEqualTo(GuestServerStatus.PROVISIONING);  // 도출
        assertThat(row.primaryIp().value()).isEqualTo("10.20.3.11");
    }

    @Test
    @DisplayName("접촉 관찰(S7) — 연결 중이면 남은 초(90-경과)가 실리고, 침묵이면 remaining 이 비워진다")
    void contactMapping_remainingSeconds() {
        UUID activeId = UUID.randomUUID();
        UUID staleId = UUID.randomUUID();
        GuestServer active = GuestServer.builder().id(activeId).systemUUID(UUID.randomUUID())
                .lastSeenAt(LocalDateTime.now().minusSeconds(30)).build();
        GuestServer stale = GuestServer.builder().id(staleId).systemUUID(UUID.randomUUID())
                .lastSeenAt(LocalDateTime.now().minusSeconds(200)).build();
        given(guestServerRepository.findAllByOrderByCreatedAtDesc()).willReturn(List.of(active, stale));
        given(detailRepository.findAllByServerIdInWithBoardModel(anyList())).willReturn(List.of());
        given(nicRepository.findPrimaryByServerIdIn(anyList())).willReturn(List.of());
        given(progressRepository.findAllByGuestServer_IdIn(anyList())).willReturn(List.of());

        List<GuestServerSummaryResponse> rows = service.findAll();

        GuestServerSummaryResponse activeRow = rows.stream().filter(r -> r.id().equals(activeId)).findFirst().orElseThrow();
        GuestServerSummaryResponse staleRow = rows.stream().filter(r -> r.id().equals(staleId)).findFirst().orElseThrow();
        assertThat(activeRow.contactActive()).isTrue();
        assertThat(activeRow.contactRemainingSeconds()).isBetween(55L, 61L);   // 90 - 경과(~30초)
        assertThat(staleRow.contactActive()).isFalse();
        assertThat(staleRow.contactRemainingSeconds()).isNull();               // rollover 예약 불필요

        // 상세 Contact — 경과 + 남은 초 = 임계(90초) 정확 일치 (같은 계산 기반에서 도출)
        given(guestServerRepository.findById(activeId)).willReturn(Optional.of(active));
        given(detailRepository.findByServerIdWithBoardModel(activeId)).willReturn(Optional.empty());
        given(nicRepository.findAllByServerIdOrderByPrimary(activeId)).willReturn(List.of());
        given(progressRepository.findByGuestServer_Id(activeId)).willReturn(Optional.empty());
        given(provisioningHistoryRepository.findAllByServerIdOrderByStartedAt(activeId)).willReturn(List.of());

        given(firmwareResolutionProvider.resolveFor(org.mockito.ArgumentMatchers.any()))
                .willReturn(java.util.Optional.empty());
        GuestServerDetailResponse.Contact contact = service.findDetail(activeId).contact();
        assertThat(contact.active()).isTrue();
        assertThat(contact.secondsSince() + contact.remainingSeconds()).isEqualTo(90L);
    }

    @Test
    @DisplayName("findAll — 서버가 없으면 빈 목록 + 후속 조회 미수행(N+1 회피 단축)")
    void findAll_empty() {
        given(guestServerRepository.findAllByOrderByCreatedAtDesc()).willReturn(List.of());

        assertThat(service.findAll()).isEmpty();
        verifyNoInteractions(detailRepository, nicRepository, progressRepository);
    }

    @Test
    @DisplayName("findDetail — 정체성/인벤토리/단계 매핑 + step.phase 는 stepCode 에서 도출")
    void findDetail_mapsAndDerives() {
        UUID id = UUID.randomUUID();
        GuestServer s = server(id, "web-01", null);
        ProvisioningHistory step = ProvisioningHistory.builder().id(UUID.randomUUID()).guestServer(s)
                .stepCode(ProvisioningPhaseStep.OS_INSTALLING).status(ProvisioningStatus.RUNNING).build();

        given(guestServerRepository.findById(id)).willReturn(Optional.of(s));
        given(detailRepository.findByServerIdWithBoardModel(id)).willReturn(Optional.of(detail(s)));
        given(nicRepository.findAllByServerIdOrderByPrimary(id)).willReturn(List.of(nic(s)));
        given(progressRepository.findByGuestServer_Id(id)).willReturn(Optional.of(progress(s, ProvisioningPhase.OS_INSTALLING)));
        given(provisioningHistoryRepository.findAllByServerIdOrderByStartedAt(id)).willReturn(List.of(step));

        given(firmwareResolutionProvider.resolveFor(org.mockito.ArgumentMatchers.any()))
                .willReturn(java.util.Optional.empty());
        GuestServerDetailResponse res = service.findDetail(id);

        assertThat(res.name()).isEqualTo("web-01");
        assertThat(res.modelName()).isEqualTo("RE2108");
        assertThat(res.serialNumber()).isEqualTo("RE2108X");
        assertThat(res.status()).isEqualTo(GuestServerStatus.PROVISIONING);
        assertThat(res.inventory().vendor()).isEqualTo(Vendor.GIGABYTE);
        assertThat(res.nics()).hasSize(1);
        assertThat(res.steps()).hasSize(1);
        assertThat(res.steps().get(0).phase()).isEqualTo(ProvisioningPhase.OS_INSTALLING);  // 도출
        assertThat(res.steps().get(0).step()).isEqualTo(ProvisioningPhaseStep.OS_INSTALLING);
    }

    @Test
    @DisplayName("findDetail — 게스트 실패: failedStepCode = 커서 step 파생 (ES-2 D-5)")
    void findDetail_guestFailure_derivesCursorStep() {
        UUID id = UUID.randomUUID();
        GuestServer s = server(id, "web-02", null);
        LocalDateTime failedAt = LocalDateTime.now();
        ProvisioningProgress failed = ProvisioningProgress.builder().id(UUID.randomUUID()).guestServer(s)
                .currentStep(com.example.serverprovision.execution.enums.ProvisioningPhaseStep.INFORMATION_COLLECTING)
                .lastTransitionAt(failedAt).startedAt(failedAt).failedAt(failedAt).build();
        ProvisioningHistory guestFailRow = ProvisioningHistory.instant(s,
                ProvisioningPhaseStep.INFORMATION_COLLECTING, ProvisioningStatus.FAILED,
                "{\"reason\":\"disk\"}", failedAt);

        given(guestServerRepository.findById(id)).willReturn(Optional.of(s));
        given(detailRepository.findByServerIdWithBoardModel(id)).willReturn(Optional.of(detail(s)));
        given(nicRepository.findAllByServerIdOrderByPrimary(id)).willReturn(List.of());
        given(progressRepository.findByGuestServer_Id(id)).willReturn(Optional.of(failed));
        given(provisioningHistoryRepository.findAllByServerIdOrderByStartedAt(id)).willReturn(List.of(guestFailRow));

        given(firmwareResolutionProvider.resolveFor(org.mockito.ArgumentMatchers.any()))
                .willReturn(java.util.Optional.empty());
        GuestServerDetailResponse res = service.findDetail(id);

        assertThat(res.progress().failedStepCode())
                .isEqualTo(ProvisioningPhaseStep.INFORMATION_COLLECTING);   // 커서 = 실패 지점
    }

    @Test
    @DisplayName("findDetail — 운영자 수동 전환: 원장 operator 행 판독 → failedStepCode null (화면 '운영자 전환' 유지)")
    void findDetail_manualFailure_derivesNull() {
        UUID id = UUID.randomUUID();
        GuestServer s = server(id, "web-03", null);
        LocalDateTime failedAt = LocalDateTime.now();
        ProvisioningProgress failed = ProvisioningProgress.builder().id(UUID.randomUUID()).guestServer(s)
                .currentStep(com.example.serverprovision.execution.enums.ProvisioningPhaseStep.INFORMATION_COLLECTING)
                .lastTransitionAt(failedAt).startedAt(failedAt).failedAt(failedAt).build();
        ProvisioningHistory operatorRow = ProvisioningHistory.instant(s,
                ProvisioningPhaseStep.INFORMATION_COLLECTING, ProvisioningStatus.FAILED,
                ProvisioningHistory.OPERATOR_ORIGIN_META, failedAt);   // markFailedManually 가 같은 now 로 적재

        given(guestServerRepository.findById(id)).willReturn(Optional.of(s));
        given(detailRepository.findByServerIdWithBoardModel(id)).willReturn(Optional.of(detail(s)));
        given(nicRepository.findAllByServerIdOrderByPrimary(id)).willReturn(List.of());
        given(progressRepository.findByGuestServer_Id(id)).willReturn(Optional.of(failed));
        given(provisioningHistoryRepository.findAllByServerIdOrderByStartedAt(id)).willReturn(List.of(operatorRow));

        given(firmwareResolutionProvider.resolveFor(org.mockito.ArgumentMatchers.any()))
                .willReturn(java.util.Optional.empty());
        GuestServerDetailResponse res = service.findDetail(id);

        assertThat(res.progress().failedStepCode()).isNull();   // '운영자 전환' 배지 경로
    }

    @Test
    @DisplayName("findDetail — 회수된 서버는 status=DECOMMISSIONED (progress 무관)")
    void findDetail_decommissioned() {
        UUID id = UUID.randomUUID();
        GuestServer s = server(id, "old-01", LocalDateTime.now());
        given(guestServerRepository.findById(id)).willReturn(Optional.of(s));
        given(detailRepository.findByServerIdWithBoardModel(id)).willReturn(Optional.of(detail(s)));
        given(nicRepository.findAllByServerIdOrderByPrimary(id)).willReturn(List.of());
        given(progressRepository.findByGuestServer_Id(id)).willReturn(Optional.of(progress(s, ProvisioningPhase.OS_INSTALLING)));
        given(provisioningHistoryRepository.findAllByServerIdOrderByStartedAt(id)).willReturn(List.of());

        given(firmwareResolutionProvider.resolveFor(org.mockito.ArgumentMatchers.any()))
                .willReturn(java.util.Optional.empty());
        assertThat(service.findDetail(id).status()).isEqualTo(GuestServerStatus.DECOMMISSIONED);
    }

    @Test
    @DisplayName("findDetail — 없는 id → GuestServerNotFoundException (advice 404)")
    void findDetail_notFound() {
        UUID id = UUID.randomUUID();
        given(guestServerRepository.findById(id)).willReturn(Optional.empty());

        assertThatThrownBy(() -> service.findDetail(id)).isInstanceOf(GuestServerNotFoundException.class);
    }

    // ==== E3.5-4 — 미리보기 3분기(W8) · 실물 표(W9 · W12) ====

    private com.example.serverprovision.execution.engine.raid.RaidInventory raidInv(
            com.example.serverprovision.execution.engine.raid.RaidExistingVolume... volumes) {
        return new com.example.serverprovision.execution.engine.raid.RaidInventory(null,
                java.util.List.of(), java.util.List.of(volumes));
    }

    private com.example.serverprovision.execution.engine.raid.RaidExistingVolume vol(String name) {
        return new com.example.serverprovision.execution.engine.raid.RaidExistingVolume(
                "VD0", "RAID1", "446.625 GB", "Optl", name, java.util.List.of("252:0", "252:1"), null);
    }

    private UUID stubDetailWithRaidInventory(com.example.serverprovision.execution.engine.raid.RaidInventory inv) {
        UUID id = UUID.randomUUID();
        GuestServer s = server(id, "web-01", null);
        GuestServerDetail d = detail(s);
        d.enrichRaidInventory(objectMapper.writeValueAsString(inv));
        given(guestServerRepository.findById(id)).willReturn(Optional.of(s));
        given(detailRepository.findByServerIdWithBoardModel(id)).willReturn(Optional.of(d));
        return id;
    }

    @Test
    @DisplayName("W8 — 축 명시(보존)면 그 정책 단일 갈래")
    void preview_declaredPolicy_singleBranch() {
        UUID id = stubDetailWithRaidInventory(raidInv(vol("legacy")));
        given(raidConfigurationResolutionProvider.policyOf(id)).willReturn(Optional.of(
                com.example.serverprovision.execution.engine.raid.RaidExistingConfigPolicy.PRESERVE));
        given(raidConfigurationResolutionProvider.planFor(eq(id), any(), eq(
                com.example.serverprovision.execution.engine.raid.RaidExistingConfigPolicy.PRESERVE)))
                .willReturn(Optional.of(new com.example.serverprovision.execution.engine.raid.RaidPlanRejection(
                        com.example.serverprovision.execution.engine.raid.RaidPlanRejection.EXISTING_CONFIG, "외부 1개")));

        var response = service.findDetail(id);

        assertThat(response.raidPlan().policyUndecided()).isFalse();
        assertThat(response.raidPlan().branches()).singleElement()
                .satisfies(b -> assertThat(b.rejectionCode()).isEqualTo("EXISTING_CONFIG"));
    }

    @Test
    @DisplayName("W8 — 축 null + 외부 볼륨 = 두 갈래 병기(현행 유지)")
    void preview_legacyWithForeign_twoBranches() {
        UUID id = stubDetailWithRaidInventory(raidInv(vol("legacy")));
        given(raidConfigurationResolutionProvider.policyOf(id)).willReturn(Optional.empty());
        given(raidConfigurationResolutionProvider.planFor(eq(id), any(), any()))
                .willReturn(Optional.of(new com.example.serverprovision.execution.engine.raid.RaidPlan(
                        true, java.util.List.of(), java.util.List.of(), java.util.List.of(), java.util.List.of(), null)));

        var response = service.findDetail(id);

        assertThat(response.raidPlan().policyUndecided()).isTrue();
        assertThat(response.raidPlan().branches()).hasSize(2);
    }

    @Test
    @DisplayName("W8 — 축 null + spvR 잔여만 = 정책 무관 단일(Q2 통일 — 비대칭 해소)")
    void preview_legacyWithResidueOnly_singleBranch() {
        UUID id = stubDetailWithRaidInventory(raidInv(vol("spvR1V1")));
        given(raidConfigurationResolutionProvider.policyOf(id)).willReturn(Optional.empty());
        given(raidConfigurationResolutionProvider.planFor(eq(id), any(), eq(
                com.example.serverprovision.execution.engine.raid.RaidExistingConfigPolicy.DESTROY)))
                .willReturn(Optional.of(new com.example.serverprovision.execution.engine.raid.RaidPlan(
                        true, java.util.List.of(), java.util.List.of(), java.util.List.of(), java.util.List.of(), null)));

        var response = service.findDetail(id);

        assertThat(response.raidPlan().policyUndecided()).isFalse();
        assertThat(response.raidPlan().branches()).hasSize(1);
    }

    // ── E3.5-5-a — 카드 대조 예고(D5): 판정 SSOT 는 RaidCardMatch.judge, 조회는 재료를 채운다 ──

    private com.example.serverprovision.execution.engine.raid.RaidInventory raidInvWithCard(String subsystem) {
        return new com.example.serverprovision.execution.engine.raid.RaidInventory(
                new com.example.serverprovision.execution.engine.raid.DetectedRaidCard(
                        com.example.serverprovision.management.raidcard.enums.RaidChipFamily.MEGARAID, subsystem, "9361-8i", "fw"),
                java.util.List.of(), java.util.List.of());
    }

    private void stubPlanForAny(UUID id) {
        given(raidConfigurationResolutionProvider.policyOf(id)).willReturn(Optional.empty());
        given(raidConfigurationResolutionProvider.planFor(eq(id), any(), any()))
                .willReturn(Optional.of(new com.example.serverprovision.execution.engine.raid.RaidPlan(
                        false, java.util.List.of(), java.util.List.of(), java.util.List.of(), java.util.List.of(), null)));
    }

    private GuestServerDetailResponse.RaidCardCheck cardCheckOf(
            com.example.serverprovision.execution.engine.raid.RaidInventory inv,
            Optional<com.example.serverprovision.execution.engine.raid.RaidConfigurationTarget> target) {
        UUID id = stubDetailWithRaidInventory(inv);
        stubPlanForAny(id);
        given(raidConfigurationResolutionProvider.resolveFor(id)).willReturn(target);
        return service.findDetail(id).raidPlan().cardCheck();
    }

    @Test
    @DisplayName("E3.5-5-a — 할당 없음 → NOT_APPLICABLE(화면은 줄을 그리지 않는다)")
    void cardCheck_noAssignment_notApplicable() {
        var cc = cardCheckOf(raidInvWithCard("1000:9361"), Optional.empty());
        assertThat(cc.verdict()).isEqualTo(com.example.serverprovision.execution.engine.raid.RaidCardMatchVerdict.NOT_APPLICABLE);
        assertThat(cc.observedSubsystem()).isEqualTo("1000:9361");
    }

    @Test
    @DisplayName("E3.5-5-a — 지정 카드의 자원 Subsystem 미확정 → UNVERIFIABLE + 카드명")
    void cardCheck_unverifiable() {
        var cc = cardCheckOf(raidInvWithCard("1000:9361"), Optional.of(
                new com.example.serverprovision.execution.engine.raid.RaidConfigurationTarget(7L, null, "MegaRAID SAS 9361-8i")));
        assertThat(cc.verdict()).isEqualTo(com.example.serverprovision.execution.engine.raid.RaidCardMatchVerdict.UNVERIFIABLE);
        assertThat(cc.cardModelName()).isEqualTo("MegaRAID SAS 9361-8i");
        assertThat(cc.raidCardId()).isEqualTo(7L);   // CP5 F-3 — 카드 자원 링크 재료
        assertThat(cc.specifiedSubsystem()).isNull();
    }

    @Test
    @DisplayName("E3.5-5-a — 확정 Subsystem 일치 → MATCH / 다름 → MISMATCH / 카드 미감지 → NOT_DETECTED")
    void cardCheck_matchMismatchNotDetected() {
        var target = Optional.of(new com.example.serverprovision.execution.engine.raid.RaidConfigurationTarget(7L, "1000:9361", "9361-8i"));
        assertThat(cardCheckOf(raidInvWithCard("1000:9361"), target).verdict())
                .isEqualTo(com.example.serverprovision.execution.engine.raid.RaidCardMatchVerdict.MATCH);
        assertThat(cardCheckOf(raidInvWithCard("1458:3008"), target).verdict())
                .isEqualTo(com.example.serverprovision.execution.engine.raid.RaidCardMatchVerdict.MISMATCH);
        assertThat(cardCheckOf(raidInv(), target).verdict())
                .isEqualTo(com.example.serverprovision.execution.engine.raid.RaidCardMatchVerdict.NOT_DETECTED);
    }

    @Test
    @DisplayName("W9 · W12 — 실물 표 뷰모델: raid_volume 2행(볼륨 + 단독 디스크) · WWN · OS 배지 재료")
    void findDetail_mapsRaidVolumes() {
        UUID id = UUID.randomUUID();
        GuestServer s = server(id, "web-01", null);
        given(guestServerRepository.findById(id)).willReturn(Optional.of(s));
        given(raidVolumeRepository.findAllByGuestServer_Id(id)).willReturn(java.util.List.of(
                com.example.serverprovision.execution.entity.RaidVolume.of(s, "spvR1V1",
                        com.example.serverprovision.management.raidcard.enums.RaidLevel.RAID1,
                        "[\"252:0\",\"252:1\"]", 479_559_942_144L,
                        com.example.serverprovision.execution.engine.raid.PlannedVolumeRole.OS, 1,
                        "Optl", "600605b00d18aa1e322807f9084a72aa"),
                com.example.serverprovision.execution.entity.RaidVolume.of(s, "252:4", null,
                        "[\"252:4\"]", 480_000_000_000L,
                        com.example.serverprovision.execution.engine.raid.PlannedVolumeRole.DATA, 2, null, null)));

        var response = service.findDetail(id);

        assertThat(response.raidVolumes()).hasSize(2);
        var os = response.raidVolumes().get(0);
        assertThat(os.name()).isEqualTo("spvR1V1");
        assertThat(os.level()).isEqualTo("RAID1");
        assertThat(os.members()).isEqualTo("252:0 · 252:1");
        assertThat(os.role()).isEqualTo(com.example.serverprovision.execution.engine.raid.PlannedVolumeRole.OS);
        assertThat(os.wwn()).isEqualTo("600605b00d18aa1e322807f9084a72aa");
        assertThat(response.raidVolumes().get(1).level()).isEqualTo("RAID 없음");
    }
}
