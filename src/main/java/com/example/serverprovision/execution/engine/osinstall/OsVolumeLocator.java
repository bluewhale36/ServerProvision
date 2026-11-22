package com.example.serverprovision.execution.engine.osinstall;

import com.example.serverprovision.execution.engine.phase.OwnedPhasesProvider;
import com.example.serverprovision.execution.engine.raid.PlannedVolumeRole;
import com.example.serverprovision.execution.engine.raid.RaidConfigurationResolutionProvider;
import com.example.serverprovision.execution.engine.raid.RaidExistingConfigPolicy;
import com.example.serverprovision.execution.engine.raid.RaidInventory;
import com.example.serverprovision.execution.engine.raid.RaidPlan;
import com.example.serverprovision.execution.engine.raid.RaidPlanRejection;
import com.example.serverprovision.execution.entity.GuestServerDetail;
import com.example.serverprovision.execution.entity.ProvisioningProgress;
import com.example.serverprovision.execution.entity.RaidVolume;
import com.example.serverprovision.execution.enums.ProvisioningPhase;
import com.example.serverprovision.execution.repository.GuestServerDetailRepository;
import com.example.serverprovision.execution.repository.ProvisioningProgressRepository;
import com.example.serverprovision.execution.repository.RaidVolumeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * 게스트의 OS 영역 RAID 볼륨을 찾는다(E4-1-a-6 · HF15-5, E4-1-b-4-2 에서 계열 공통으로 옮김). 실물 볼륨이 있으면 그대로 넘기고,
 * RAID 구성 단계가 아직 남아 실물이 없으면(실기 3호 F-1) 실패가 아니라 "구성 뒤 확정" 이다 — 계획({@code RaidPlanner}
 * 미리보기와 같은 산출)의 OS 영역을 근거로 {@link OsVolume.Deferred} 를 내고, 계획에도 OS 영역이 없을 때만 미리 막는다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OsVolumeLocator {

    private static final String AFTER_RAID = "RAID 구성 뒤 정해집니다";

    private final RaidVolumeRepository raidVolumeRepository;
    private final GuestServerDetailRepository detailRepository;
    private final ProvisioningProgressRepository progressRepository;
    private final OwnedPhasesProvider ownedPhasesProvider;
    private final RaidConfigurationResolutionProvider raidResolutionProvider;
    private final ObjectMapper objectMapper;

    public OsVolume locate(UUID guestServerId) {
        List<RaidVolume> volumes = raidVolumeRepository.findAllByGuestServer_Id(guestServerId);
        boolean hasOsVolume = volumes.stream().anyMatch(v -> v.getVolumeRole() == PlannedVolumeRole.OS);
        RaidInventory inventory = inventoryOf(detailRepository.findByGuestServer_Id(guestServerId));
        if (!hasOsVolume && raidPhasePending(guestServerId)) {
            return byPlan(guestServerId, inventory);
        }
        return new OsVolume.Materialized(volumes, inventory);
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
    private OsVolume byPlan(UUID guestServerId, RaidInventory inventory) {
        if (inventory == null) {
            return new OsVolume.Deferred(AFTER_RAID);
        }
        RaidExistingConfigPolicy policy = raidResolutionProvider.policyOf(guestServerId).orElse(RaidExistingConfigPolicy.DESTROY);
        return raidResolutionProvider.planFor(guestServerId, inventory, policy)
                .map(outcome -> switch (outcome) {
                    case RaidPlanRejection rejection -> (OsVolume) new OsVolume.Deferred("RAID 계획이 거절돼 아직 정할 수 없습니다");
                    case RaidPlan plan -> byPlanVolumes(plan);
                })
                .orElseGet(() -> new OsVolume.Deferred(AFTER_RAID));
    }

    private static OsVolume byPlanVolumes(RaidPlan plan) {
        Optional<String> osName = plan.volumes().stream()
                .filter(v -> v.role() == PlannedVolumeRole.OS).map(v -> v.name()).findFirst()
                .or(() -> plan.passthroughs().stream()
                        .filter(p -> p.role() == PlannedVolumeRole.OS).map(p -> "단독 디스크 " + p.slot()).findFirst());
        if (osName.isEmpty()) {
            String why = plan.osAbsenceReason() == null ? "" : " (" + plan.osAbsenceReason() + ")";
            return new OsVolume.Blocked("os volume missing in plan",
                    "RAID 계획에 OS 영역 볼륨이 없습니다" + why + ": 정의서의 디스크 묶음 규칙에서 OS 영역을 지정해야 합니다");
        }
        return new OsVolume.Deferred(AFTER_RAID + "(OS 영역 " + osName.get() + ")");
    }

    private RaidInventory inventoryOf(Optional<GuestServerDetail> detail) {
        String json = detail.map(GuestServerDetail::getRaidInventoryJson).orElse(null);
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(json, RaidInventory.class);
        } catch (RuntimeException notParseable) {
            log.warn("[osinstall] RAID 인벤토리 JSON 을 읽지 못해 진단 전으로 본다 : {}", notParseable.getMessage());
            return null;
        }
    }
}
