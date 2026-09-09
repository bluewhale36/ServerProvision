package com.example.serverprovision.execution.engine.windows;

import com.example.serverprovision.execution.engine.phase.ReadinessGrade;
import com.example.serverprovision.execution.engine.raid.PlannedVolumeRole;
import com.example.serverprovision.execution.entity.GuestServerDetail;
import com.example.serverprovision.execution.entity.RaidVolume;
import com.example.serverprovision.execution.repository.GuestServerDetailRepository;
import com.example.serverprovision.execution.repository.RaidVolumeRepository;
import com.example.serverprovision.management.raidcard.enums.RaidLevel;
import com.example.serverprovision.execution.wininstall.WindowsInstallSource;
import com.example.serverprovision.execution.wininstall.catalog.FakeWim;
import com.example.serverprovision.execution.wininstall.catalog.WindowsImageCatalog;
import com.example.serverprovision.execution.wininstall.config.WindowsInstallProperties;
import com.example.serverprovision.execution.wininstall.vo.WindowsImageName;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;

/** E4-1-a-3 CP4 — 준비도 재료 조립: 실 카탈로그(가짜 WIM) · 실 소스 자산 · SPI mock 으로 실행기와 카드가 같은 판정을 받는지.
 *  E4-1-a-6 이 더한 디스크 선택은 별 테스트가 검증하므로, 여기서는 base 판정을 가리지 않게 CONFIDENT 재료를 stub 한다. */
class WindowsInstallReadinessResolverTest {

    private static final UUID GUEST_ID = UUID.randomUUID();

    /** 디스크 선택이 CONFIDENT 가 되는 인벤토리 — OS 볼륨 spvR1V1(wwn-os) 하나. */
    private static final String INVENTORY_JSON = """
            {"card":{"chipFamily":"MEGARAID","pciSubsystemId":"1000:9361","model":"m","firmware":"f"},
             "disks":[],
             "volumes":[{"id":"VD0","level":"RAID1","size":"446 GB","state":"Optl","name":"spvR1V1","memberSlots":[],"wwn":"wwn-os"}]}""";

    @TempDir Path root;

    private final WindowsInstallationResolutionProvider provider = mock(WindowsInstallationResolutionProvider.class);
    private final RaidVolumeRepository raidVolumeRepository = mock(RaidVolumeRepository.class);
    private final GuestServerDetailRepository detailRepository = mock(GuestServerDetailRepository.class);
    private final com.example.serverprovision.execution.repository.ProvisioningProgressRepository progressRepository =
            mock(com.example.serverprovision.execution.repository.ProvisioningProgressRepository.class);
    private final com.example.serverprovision.execution.engine.phase.OwnedPhasesProvider ownedPhasesProvider =
            mock(com.example.serverprovision.execution.engine.phase.OwnedPhasesProvider.class);
    private final com.example.serverprovision.execution.engine.raid.RaidConfigurationResolutionProvider raidResolutionProvider =
            mock(com.example.serverprovision.execution.engine.raid.RaidConfigurationResolutionProvider.class);
    private final ObjectMapper objectMapper = new ObjectMapper();

    private WindowsInstallReadinessResolver resolver(String datacenterKey) {
        WindowsInstallProperties props = new WindowsInstallProperties(root.toString(), "\\\\10.0.0.5\\win2025", "deploy",
                "share-secret-9x", null, new WindowsInstallProperties.ProductKeys("KEY-STD", datacenterKey));
        stubConfidentDisk();
        return new WindowsInstallReadinessResolver(provider, new WindowsImageCatalog(props), props,
                new WindowsInstallSource(props), raidVolumeRepository, detailRepository,
                progressRepository, ownedPhasesProvider, raidResolutionProvider, objectMapper);
    }

    /** OS 볼륨 + 그 볼륨을 담은 RAID 인벤토리를 준비해 디스크 선택이 CONFIDENT 가 되게 한다. */
    private void stubConfidentDisk() {
        RaidVolume os = RaidVolume.of(null, "spvR1V1", RaidLevel.RAID1, "[]", 480103981056L, PlannedVolumeRole.OS, 1, "Optl", "wwn-os");
        lenient().when(raidVolumeRepository.findAllByGuestServer_Id(any())).thenReturn(List.of(os));
        GuestServerDetail detail = mock(GuestServerDetail.class);
        lenient().when(detail.getRaidInventoryJson()).thenReturn(INVENTORY_JSON);
        lenient().when(detailRepository.findByGuestServer_Id(any())).thenReturn(Optional.of(detail));
    }

    @Test
    @DisplayName("창 밖 — SPI empty 면 resolve empty · readiness READY")
    void outsideWindow() {
        given(provider.resolveFor(GUEST_ID)).willReturn(Optional.empty());

        assertThat(resolver(null).resolve(GUEST_ID)).isEmpty();
        assertThat(resolver(null).readiness(GUEST_ID).grade()).isEqualTo(ReadinessGrade.READY);
    }

    @Test
    @DisplayName("Windows 목표 + 소스 · wimboot · 키 → READY · 이미지(표시명)가 함께 실린다")
    void windowsTarget_ready() throws IOException {
        FakeWim.writeSource(root, FakeWim.fixtureXml());
        FakeWim.writeOemScripts(root);   // E4-1-a-4 — 12행(스크립트 부재)이 판정을 가리지 않게
        Files.writeString(root.resolve("wimboot"), "FAKE-WIMBOOT");
        given(provider.resolveFor(GUEST_ID)).willReturn(Optional.of(
                WindowsInstallTarget.windows(new WindowsImageName(FakeWim.STANDARD_DESKTOP), "P@ss")));

        WindowsInstallReadinessResolver.Resolved r = resolver(null).resolve(GUEST_ID).orElseThrow();

        assertThat(r.readiness().grade()).isEqualTo(ReadinessGrade.READY);
        assertThat(r.image()).hasValueSatisfying(i -> assertThat(i.editionId()).isEqualTo("ServerStandard"));
        assertThat(r.snapshot().ready()).isTrue();
    }

    @Test
    @DisplayName("wimboot 부재 → BLOCKED 'wimboot missing' — 파일 관측이 판정에 실제로 들어간다")
    void wimbootMissing_blocked() throws IOException {
        FakeWim.writeSource(root, FakeWim.fixtureXml());
        FakeWim.writeOemScripts(root);   // E4-1-a-4 — 12행(스크립트 부재)이 판정을 가리지 않게
        given(provider.resolveFor(GUEST_ID)).willReturn(Optional.of(
                WindowsInstallTarget.windows(new WindowsImageName(FakeWim.STANDARD_DESKTOP), "P@ss")));

        assertThat(resolver(null).readiness(GUEST_ID).wire()).isEqualTo("wimboot missing");
    }

    @Test
    @DisplayName("소스는 준비됐지만 OS 볼륨이 없으면 selection 이 BLOCKED 를 합류시킨다(E4-1-a-6)")
    void osVolumeMissing_blockedBySelection() throws IOException {
        FakeWim.writeSource(root, FakeWim.fixtureXml());
        FakeWim.writeOemScripts(root);
        Files.writeString(root.resolve("wimboot"), "FAKE-WIMBOOT");
        given(provider.resolveFor(GUEST_ID)).willReturn(Optional.of(
                WindowsInstallTarget.windows(new WindowsImageName(FakeWim.STANDARD_DESKTOP), "P@ss")));
        WindowsInstallReadinessResolver r = resolver(null);
        given(raidVolumeRepository.findAllByGuestServer_Id(any())).willReturn(List.of());   // OS 볼륨 없음

        assertThat(r.readiness(GUEST_ID).grade()).isEqualTo(ReadinessGrade.BLOCKED);
        assertThat(r.readiness(GUEST_ID).wire()).isEqualTo("os volume missing");
    }

    /** RAID 단계를 보유하고 커서가 아직 그 단계 앞(펌웨어)인 게스트 — 실기 3호 F-1 의 상황. */
    private void raidPhaseAhead() {
        given(ownedPhasesProvider.ownedPhasesOf(any())).willReturn(java.util.Set.of(
                com.example.serverprovision.execution.enums.ProvisioningPhase.FIRMWARE_UPDATING,
                com.example.serverprovision.execution.enums.ProvisioningPhase.RAID_CONFIGURATION,
                com.example.serverprovision.execution.enums.ProvisioningPhase.OS_INSTALLING));
        com.example.serverprovision.execution.entity.ProvisioningProgress p =
                com.example.serverprovision.execution.entity.ProvisioningProgress.builder()
                        .currentStep(com.example.serverprovision.execution.enums.ProvisioningPhaseStep.BIOS_UPDATING)
                        .lastTransitionAt(java.time.LocalDateTime.now()).startedAt(java.time.LocalDateTime.now()).build();
        given(progressRepository.findByGuestServer_Id(any())).willReturn(Optional.of(p));
        given(raidVolumeRepository.findAllByGuestServer_Id(any())).willReturn(List.of());   // 실물 볼륨 아직 없음
    }

    private static com.example.serverprovision.execution.engine.raid.RaidPlan planWithOs(String osName) {
        var vol = new com.example.serverprovision.execution.engine.raid.PlannedVolume(osName, RaidLevel.RAID1,
                List.of("1:2", "1:3"), 240_056_459_591L,
                osName == null ? PlannedVolumeRole.DATA : PlannedVolumeRole.OS, 1, null, List.of(), null);
        return new com.example.serverprovision.execution.engine.raid.RaidPlan(false,
                List.of(osName == null ? new com.example.serverprovision.execution.engine.raid.PlannedVolume("spvR1V1", RaidLevel.RAID1,
                        List.of("1:0", "1:1"), 1L, PlannedVolumeRole.DATA, 1, null, List.of(), null) : vol),
                List.of(), List.of(), List.of(), osName == null ? "우선순위 없음" : null);
    }

    @Test
    @DisplayName("F-1 · RAID 단계가 남았고 계획에 OS 영역이 있으면 DEFERRED — 준비도는 READY · 사유에 계획 볼륨 이름")
    void raidAhead_planHasOs_deferred() throws IOException {
        FakeWim.writeSource(root, FakeWim.fixtureXml());
        FakeWim.writeOemScripts(root);
        Files.writeString(root.resolve("wimboot"), "FAKE-WIMBOOT");
        given(provider.resolveFor(GUEST_ID)).willReturn(Optional.of(
                WindowsInstallTarget.windows(new WindowsImageName(FakeWim.STANDARD_DESKTOP), "P@ss")));
        WindowsInstallReadinessResolver r = resolver(null);
        raidPhaseAhead();
        given(raidResolutionProvider.planFor(any(), any(), any())).willReturn(Optional.of(planWithOs("spvR1V2")));

        WindowsInstallReadinessResolver.Resolved resolved = r.resolve(GUEST_ID).orElseThrow();

        assertThat(resolved.readiness().grade()).isEqualTo(ReadinessGrade.READY);
        assertThat(resolved.diskSelection().confidence()).isEqualTo(WindowsDiskSelection.Confidence.DEFERRED);
        assertThat(resolved.diskSelection().note()).contains("spvR1V2");
    }

    @Test
    @DisplayName("F-1 · RAID 단계가 남았는데 계획에 OS 영역이 없으면 미리 BLOCKED — os volume missing in plan")
    void raidAhead_planWithoutOs_blocked() throws IOException {
        FakeWim.writeSource(root, FakeWim.fixtureXml());
        FakeWim.writeOemScripts(root);
        Files.writeString(root.resolve("wimboot"), "FAKE-WIMBOOT");
        given(provider.resolveFor(GUEST_ID)).willReturn(Optional.of(
                WindowsInstallTarget.windows(new WindowsImageName(FakeWim.STANDARD_DESKTOP), "P@ss")));
        WindowsInstallReadinessResolver r = resolver(null);
        raidPhaseAhead();
        given(raidResolutionProvider.planFor(any(), any(), any())).willReturn(Optional.of(planWithOs(null)));

        assertThat(r.readiness(GUEST_ID).grade()).isEqualTo(ReadinessGrade.BLOCKED);
        assertThat(r.readiness(GUEST_ID).wire()).isEqualTo("os volume missing in plan");
        assertThat(r.readiness(GUEST_ID).notes()).anySatisfy(n -> assertThat(n).contains("우선순위 없음"));
    }

    @Test
    @DisplayName("RAID 단계를 지났는데(커서 OS 설치) 실물 볼륨이 없으면 계획을 보지 않고 BLOCKED — os volume missing")
    void raidPassed_noVolume_blocked() throws IOException {
        FakeWim.writeSource(root, FakeWim.fixtureXml());
        FakeWim.writeOemScripts(root);
        Files.writeString(root.resolve("wimboot"), "FAKE-WIMBOOT");
        given(provider.resolveFor(GUEST_ID)).willReturn(Optional.of(
                WindowsInstallTarget.windows(new WindowsImageName(FakeWim.STANDARD_DESKTOP), "P@ss")));
        WindowsInstallReadinessResolver r = resolver(null);
        raidPhaseAhead();
        com.example.serverprovision.execution.entity.ProvisioningProgress past =
                com.example.serverprovision.execution.entity.ProvisioningProgress.builder()
                        .currentStep(com.example.serverprovision.execution.enums.ProvisioningPhaseStep.OS_INSTALLING)
                        .lastTransitionAt(java.time.LocalDateTime.now()).startedAt(java.time.LocalDateTime.now()).build();
        given(progressRepository.findByGuestServer_Id(any())).willReturn(Optional.of(past));

        assertThat(r.readiness(GUEST_ID).wire()).isEqualTo("os volume missing");
        org.mockito.Mockito.verify(raidResolutionProvider, org.mockito.Mockito.never()).planFor(any(), any(), any());
    }

    @Test
    @DisplayName("리눅스 목표 → BLOCKED · 이미지 없음(대조할 이름이 없다)")
    void linuxTarget_blocked() throws IOException {
        FakeWim.writeSource(root, FakeWim.fixtureXml());
        FakeWim.writeOemScripts(root);   // E4-1-a-4 — 12행(스크립트 부재)이 판정을 가리지 않게
        Files.writeString(root.resolve("wimboot"), "FAKE-WIMBOOT");
        given(provider.resolveFor(GUEST_ID)).willReturn(Optional.of(WindowsInstallTarget.unsupported("RHEL 계열")));

        WindowsInstallReadinessResolver.Resolved r = resolver(null).resolve(GUEST_ID).orElseThrow();

        assertThat(r.readiness().wire()).isEqualTo("linux install not supported");
        assertThat(r.image()).isEmpty();
    }
}
