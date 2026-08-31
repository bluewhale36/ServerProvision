package com.example.serverprovision.execution.engine.raid;

import com.example.serverprovision.execution.config.PxeAssetsProperties;
import com.example.serverprovision.execution.engine.boot.DiagnoseLinuxChainload;
import com.example.serverprovision.execution.engine.phase.ProvisioningPhaseExecutor;
import com.example.serverprovision.execution.entity.GuestServer;
import com.example.serverprovision.execution.entity.GuestServerDetail;
import com.example.serverprovision.execution.entity.ProvisioningHistory;
import com.example.serverprovision.execution.entity.ProvisioningProgress;
import com.example.serverprovision.execution.enums.AgentDirective;
import com.example.serverprovision.execution.enums.ProvisioningPhase;
import com.example.serverprovision.execution.enums.ProvisioningPhaseStep;
import com.example.serverprovision.execution.repository.GuestServerDetailRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * RAID 구성 phase 실행기(E3.5-1) — 빈 등록만으로 dispatch 의 HOLD 행이 위임으로 바뀐다(DEC-6).
 * 이 슬라이스는 인벤토리까지다: 게스트를 진단 리눅스로 부팅시켜 카드 뒤(물리 디스크 · 기존 볼륨)를
 * 보고받고, 정의서 카드와 대조해 적재한다. 계획 산출(E3.5-2) · 집행(E3.5-3)이 뒤를 채우기 전까지
 * 인벤토리 완료 게스트는 {@code WAIT} 로 명시 대기한다.
 */
@Slf4j
@Component
@ConditionalOnProperty("pxe.assets.root")
@RequiredArgsConstructor
public class RaidConfigurationExecutor implements ProvisioningPhaseExecutor {

    private final PxeAssetsProperties properties;
    private final GuestServerDetailRepository guestServerDetailRepository;
    private final RaidInventoryParser inventoryParser;
    private final RaidConfigurationResolutionProvider resolutionProvider;
    private final RaidLedger raidLedger;
    private final ObjectMapper objectMapper;

    @Override
    public ProvisioningPhase phase() {
        return ProvisioningPhase.RAID_CONFIGURATION;
    }

    @Override
    public String bootScript(GuestServer server, ProvisioningProgress progress, String rebootQuery) {
        if (server.getGuestToken() == null) {
            throw new IllegalStateException("게스트 토큰 부재 — 등록 invariant 위반. guestServerId=" + server.getId());
        }
        return DiagnoseLinuxChainload.script(properties.getBaseUrl(), server.getGuestToken().value(), rebootQuery);
    }

    /** 인벤토리 미적재 → 수집 지시, 적재됨 → 명시 대기(계획 · 집행은 후속 슬라이스). */
    @Override
    public AgentDirective directiveFor(GuestServer server, ProvisioningProgress progress) {
        boolean collected = guestServerDetailRepository.findByServerIdWithBoardModel(server.getId())
                .map(detail -> detail.getRaidInventoryJson() != null)
                .orElse(false);
        return collected ? AgentDirective.WAIT : AgentDirective.RAID_INVENTORY;
    }

    /**
     * 인벤토리 보고 소비 — 파싱 → 카드 대조 → 적재. 대조 실패는 예외가 아니라 원장 사유 + 실패 신호다
     * ({@link RaidLedger}). 해석 불가도 실패로 남긴다 — 진단 수집의 관용 루프와 달리 이 phase 는 개시
     * 이후라, 같은 원문이 반복 실패하는 루프보다 "실패 지점부터 다시 하기" 동선이 운영자에게 정직하다.
     */
    @Override
    public void onStepClosed(GuestServer server, ProvisioningProgress progress, ProvisioningHistory step) {
        if (step.getStepCode() != ProvisioningPhaseStep.RAID_INVENTORY_COLLECTING) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        RaidInventory inventory;
        try {
            inventory = inventoryParser.parse(step.getStatusMeta());
        } catch (RaidInventoryParser.ReportUnparsableException e) {
            log.warn("RAID 인벤토리 해석 불가 — 원문은 원장 보존 : guestServerId={}", server.getId(), e);
            raidLedger.failInstant(server, progress, RaidLedger.REPORT_UNPARSABLE, e.getMessage(), now);
            return;
        }

        Optional<RaidConfigurationTarget> target = resolutionProvider.resolveFor(server.getId());
        if (target.isPresent() && target.get().raidCardId() != null && !cardMatches(server, progress, target.get(), inventory, now)) {
            return;   // 사유는 cardMatches 가 원장에 남겼다 — 적재 생략(원문은 원장 보존)
        }

        GuestServerDetail detail = guestServerDetailRepository.findByServerIdWithBoardModel(server.getId())
                .orElseThrow(() -> new IllegalStateException(
                        "guest_server_detail 1:1 불변 위반 — 등록 seed 누락. guestServerId=" + server.getId()));
        detail.enrichRaidInventory(objectMapper.writeValueAsString(inventory));
        log.info("RAID 인벤토리 적재 : guestServerId={}, card={}, disks={}, volumes={}",
                server.getId(), inventory.card() == null ? null : inventory.card().pciSubsystemId(),
                inventory.disks().size(), inventory.volumes().size());
    }

    /** 정의서 지정 카드와 감지 카드의 Subsystem 대조 — 불일치 · 미감지는 원장 사유로 남기고 false. */
    private boolean cardMatches(GuestServer server, ProvisioningProgress progress,
                                RaidConfigurationTarget target, RaidInventory inventory, LocalDateTime now) {
        if (target.pciSubsystemId() == null) {
            // 소프트참조 카드가 사라졌거나 카드에 Subsystem 이 미등록 — 대조할 정본이 없으니 막지 않는다
            log.warn("RAID 카드 대조 생략 — 지정 카드의 Subsystem 미확보 : guestServerId={}, raidCardId={}",
                    server.getId(), target.raidCardId());
            return true;
        }
        if (inventory.card() == null || inventory.card().pciSubsystemId() == null) {
            raidLedger.failInstant(server, progress, RaidLedger.CARD_NOT_DETECTED,
                    "지정 카드 " + target.cardModelName() + "(" + target.pciSubsystemId() + ") 를 감지하지 못했습니다", now);
            return false;
        }
        if (!target.pciSubsystemId().equalsIgnoreCase(inventory.card().pciSubsystemId())) {
            raidLedger.failInstant(server, progress, RaidLedger.CARD_MISMATCH,
                    "지정 " + target.cardModelName() + "(" + target.pciSubsystemId() + ") ≠ 감지 "
                            + inventory.card().pciSubsystemId(), now);
            return false;
        }
        return true;
    }
}
