package com.example.serverprovision.execution.engine.windows;

import com.example.serverprovision.execution.engine.phase.OwnedPhasesProvider;
import com.example.serverprovision.execution.engine.phase.PhaseReadiness;
import com.example.serverprovision.execution.engine.phase.ReadinessGrade;
import com.example.serverprovision.execution.engine.raid.PlannedVolumeRole;
import com.example.serverprovision.execution.engine.raid.RaidConfigurationResolutionProvider;
import com.example.serverprovision.execution.engine.raid.RaidExistingConfigPolicy;
import com.example.serverprovision.execution.engine.raid.RaidInventory;
import com.example.serverprovision.execution.engine.raid.RaidPlan;
import com.example.serverprovision.execution.engine.raid.RaidPlanOutcome;
import com.example.serverprovision.execution.engine.raid.RaidPlanRejection;
import com.example.serverprovision.execution.engine.windows.WindowsDiskSelection.DiskSelection;
import com.example.serverprovision.execution.entity.GuestServerDetail;
import com.example.serverprovision.execution.entity.ProvisioningProgress;
import com.example.serverprovision.execution.entity.RaidVolume;
import com.example.serverprovision.execution.enums.ProvisioningPhase;
import com.example.serverprovision.execution.repository.GuestServerDetailRepository;
import com.example.serverprovision.execution.repository.ProvisioningProgressRepository;
import com.example.serverprovision.execution.repository.RaidVolumeRepository;
import com.example.serverprovision.execution.vo.HardwareSpec;
import com.example.serverprovision.execution.wininstall.WindowsInstallSource;
import com.example.serverprovision.execution.wininstall.catalog.InstallSourceSnapshot;
import com.example.serverprovision.execution.wininstall.catalog.WindowsImage;
import com.example.serverprovision.execution.wininstall.catalog.WindowsImageCatalog;
import com.example.serverprovision.execution.wininstall.config.WindowsInstallProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * 준비도 판정의 재료 조립(E4-1-a-3 · E4-1-a-6 · HF15-5) — 정의서 목표(SPI) · 소스 스냅샷 · 운영 설정 · 자산 존재에
 * RAID 구성이 남긴 OS 영역 볼륨 · 진단 인벤토리 · 검증 재채집의 OS 가시 디스크로 설치 대상 디스크 번호
 * ({@link WindowsDiskSelection})까지 한 번에 계산한다. 실행기(게이트 · 서빙)와 상세 카드가 같은 조립을 쓰므로
 * "화면이 준비됐다는데 게이트가 막는다" 는 어긋남이 없다.
 *
 * <p>RAID 구성 단계가 아직 남아 볼륨 실물이 없을 때(실기 3호 F-1)는 실패가 아니라 "구성 뒤 확정" 이다 — 계획
 * ({@code RaidPlanner} 미리보기와 같은 산출)의 OS 영역을 근거로 {@code DEFERRED} 를 내고, 계획에도 OS 영역이 없을 때만
 * BLOCKED 로 미리 알린다. 디스크 선택 판정은 별도 정적 클래스가 하고 여기서는 두 준비도를 합류시킨다 —
 * {@code WindowsInstallReadiness} 진리표에 RAID 분기를 늘리지 않기 위해서다.</p>
 */
@Component
@RequiredArgsConstructor
public class WindowsInstallReadinessResolver {

    private final WindowsInstallationResolutionProvider provider;
    private final WindowsImageCatalog catalog;
    private final WindowsInstallProperties properties;
    private final WindowsInstallSource source;
    private final RaidVolumeRepository raidVolumeRepository;
    private final GuestServerDetailRepository detailRepository;
    private final ProvisioningProgressRepository progressRepository;
    private final OwnedPhasesProvider ownedPhasesProvider;
    private final RaidConfigurationResolutionProvider raidResolutionProvider;
    private final ObjectMapper objectMapper;

    /**
     * 한 게스트의 해석 결과 — {@code image} 는 목표 이미지가 소스에 실재할 때만, {@code diskSelection} 은 Windows
     * 목표일 때만 계산된다(리눅스 · 창 밖은 판정 대상 아님).
     */
    public record Resolved(WindowsInstallTarget target, InstallSourceSnapshot snapshot,
                           Optional<WindowsImage> image, PhaseReadiness readiness, DiskSelection diskSelection) {
    }

    /** empty = 창 밖(활성 할당 없음 · OS 설치 단계 없음). */
    public Optional<Resolved> resolve(UUID guestServerId) {
        Optional<WindowsInstallTarget> target = provider.resolveFor(guestServerId);
        if (target.isEmpty()) {
            return Optional.empty();
        }
        InstallSourceSnapshot snapshot = catalog.snapshot();
        Optional<WindowsImage> image = target.get().hasImage() ? snapshot.find(target.get().imageName()) : Optional.empty();
        PhaseReadiness base = WindowsInstallReadiness.judge(target, snapshot, properties, source.assets());

        // 디스크 선택은 Windows 목표일 때만 의미가 있다(리눅스는 base 가 이미 미지원으로 막는다).
        DiskSelection selection = target.get().windows() ? selectDisk(guestServerId) : null;
        PhaseReadiness readiness = combine(base, selection);
        return Optional.of(new Resolved(target.get(), snapshot, image, readiness, selection));
    }

    public PhaseReadiness readiness(UUID guestServerId) {
        return resolve(guestServerId).map(Resolved::readiness).orElseGet(PhaseReadiness::ready);
    }

    /** 실물 볼륨이 있으면 진리표로, RAID 단계가 남아 실물이 없으면 계획으로 판정한다(F-1). */
    private DiskSelection selectDisk(UUID guestServerId) {
        List<RaidVolume> volumes = raidVolumeRepository.findAllByGuestServer_Id(guestServerId);
        boolean hasOsVolume = volumes.stream().anyMatch(v -> v.getVolumeRole() == PlannedVolumeRole.OS);
        Optional<GuestServerDetail> detail = detailRepository.findByGuestServer_Id(guestServerId);
        RaidInventory inventory = inventoryOf(detail);
        if (!hasOsVolume && raidPhasePending(guestServerId)) {
            return deferredByPlan(guestServerId, inventory);
        }
        return WindowsDiskSelection.judge(volumes, inventory, osVisibleDisksOf(detail));
    }

    /** RAID 구성 단계를 보유했고 커서가 아직 그 단계를 지나지 않았는가 — 지났다면 볼륨 부재는 실패다. */
    private boolean raidPhasePending(UUID guestServerId) {
        Set<ProvisioningPhase> owned = ownedPhasesProvider.ownedPhasesOf(guestServerId);
        if (!owned.contains(ProvisioningPhase.RAID_CONFIGURATION)) {
            return false;
        }
        return progressRepository.findByGuestServer_Id(guestServerId)
                .map(ProvisioningProgress::currentPhase)
                .map(phase -> phase.ordinal() <= ProvisioningPhase.RAID_CONFIGURATION.ordinal())
                .orElse(true);
    }

    /** 계획의 OS 영역이 곧 미래의 설치 디스크다 — 계획에도 없으면 정의서를 고쳐야 하므로 미리 막는다. */
    private DiskSelection deferredByPlan(UUID guestServerId, RaidInventory inventory) {
        if (inventory == null) {
            return DiskSelection.deferred("RAID 구성 뒤 확정 — 진단이 RAID 인벤토리를 채집하면 계획의 OS 영역을 보입니다");
        }
        RaidExistingConfigPolicy policy = raidResolutionProvider.policyOf(guestServerId).orElse(RaidExistingConfigPolicy.DESTROY);
        Optional<RaidPlanOutcome> outcome = raidResolutionProvider.planFor(guestServerId, inventory, policy);
        if (outcome.isEmpty()) {
            return DiskSelection.deferred("RAID 구성 뒤 확정");
        }
        if (outcome.get() instanceof RaidPlanRejection rejection) {
            return DiskSelection.deferred("RAID 구성 뒤 확정 — 지금 계획은 거절 상태(" + rejection.code() + ")");
        }
        RaidPlan plan = (RaidPlan) outcome.get();
        Optional<String> osName = plan.volumes().stream()
                .filter(v -> v.role() == PlannedVolumeRole.OS).map(v -> v.name()).findFirst()
                .or(() -> plan.passthroughs().stream()
                        .filter(p -> p.role() == PlannedVolumeRole.OS).map(p -> "단독 디스크 " + p.slot()).findFirst());
        if (osName.isEmpty()) {
            String why = plan.osAbsenceReason() == null ? "" : " (" + plan.osAbsenceReason() + ")";
            return new DiskSelection(WindowsDiskSelection.Confidence.BLOCKED, -1, null, 0L, null,
                    "os volume missing in plan",
                    "RAID 계획에 OS 영역 볼륨이 없습니다" + why + " — 정의서의 디스크 묶음 규칙에서 OS 영역을 지정하세요");
        }
        return DiskSelection.deferred("RAID 구성 뒤 확정 — 계획의 OS 영역 " + osName.get());
    }

    /** base 준비도와 디스크 선택 결과를 합친다 — BLOCKED 만 막고(DEFERRED 는 통과), 사유(note · wire)는 합집합이다. */
    private static PhaseReadiness combine(PhaseReadiness base, DiskSelection selection) {
        boolean selectionBlocked = selection != null && selection.blocked();
        if (!base.isBlocked() && !selectionBlocked) {
            return base;
        }
        List<String> notes = new ArrayList<>(base.notes());
        StringBuilder wire = new StringBuilder(base.isBlocked() ? base.wire() : "");
        if (selectionBlocked) {
            notes.add(selection.note());
            if (!wire.isEmpty()) {
                wire.append("; ");
            }
            wire.append(selection.wire());
        }
        return PhaseReadiness.of(ReadinessGrade.BLOCKED, notes, wire.toString());
    }

    private RaidInventory inventoryOf(Optional<GuestServerDetail> detail) {
        String json = detail.map(GuestServerDetail::getRaidInventoryJson).orElse(null);
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(json, RaidInventory.class);
        } catch (RuntimeException notParseable) {
            return null;
        }
    }

    /** RAID 검증 재채집이 갱신한 OS 가시 디스크(WWN 동봉) — 구 저장본(WWN 없음)이면 진리표가 ② 규칙으로 내려간다. */
    private List<HardwareSpec.DiskInfo> osVisibleDisksOf(Optional<GuestServerDetail> detail) {
        String json = detail.map(GuestServerDetail::getHardwareSpec).orElse(null);
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            HardwareSpec spec = objectMapper.readValue(json, HardwareSpec.class);
            return spec == null ? null : spec.disks();
        } catch (RuntimeException notParseable) {
            return null;
        }
    }
}
