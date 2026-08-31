package com.example.serverprovision.execution.engine.raid;

import com.example.serverprovision.execution.config.PxeAssetsProperties;
import com.example.serverprovision.execution.engine.boot.DiagnoseLinuxChainload;
import com.example.serverprovision.execution.engine.phase.PhaseCursorAdvancer;
import com.example.serverprovision.execution.engine.phase.ProvisioningPhaseExecutor;
import com.example.serverprovision.execution.entity.GuestServer;
import com.example.serverprovision.execution.entity.GuestServerDetail;
import com.example.serverprovision.execution.entity.ProvisioningHistory;
import com.example.serverprovision.execution.entity.ProvisioningProgress;
import com.example.serverprovision.execution.entity.RaidVolume;
import com.example.serverprovision.execution.enums.AgentDirective;
import com.example.serverprovision.execution.enums.ProvisioningPhase;
import com.example.serverprovision.execution.enums.ProvisioningPhaseStep;
import com.example.serverprovision.execution.enums.ProvisioningStatus;
import com.example.serverprovision.execution.repository.GuestServerDetailRepository;
import com.example.serverprovision.execution.repository.RaidVolumeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * RAID 구성 phase 실행기(E3.5-1 인벤토리 → E3.5-3 집행 · 검증) — 하이브리드(0-3 결정 D-1): 머리(계획 ·
 * 검증)는 서버, 손(CLI)은 게스트. 지시 판정은 {@link #directiveFor} 상태기계, 보고 소비는
 * {@link #onStepClosed} 훅이다.
 */
@Slf4j
@Component
@ConditionalOnProperty("pxe.assets.root")
@RequiredArgsConstructor
public class RaidConfigurationExecutor implements ProvisioningPhaseExecutor {

    /** 우리가 만든 볼륨의 이름 접두(E3.5-3 CP1 개정 — 하이픈 미지원 카드 대비 영숫자만). */
    private static final String OUR_NAME_PREFIX = "spvR";

    private final PxeAssetsProperties properties;
    private final GuestServerDetailRepository guestServerDetailRepository;
    private final RaidInventoryParser inventoryParser;
    private final RaidConfigurationResolutionProvider resolutionProvider;
    private final RaidLedger raidLedger;
    private final RaidVolumeRepository raidVolumeRepository;
    private final PhaseCursorAdvancer phaseCursorAdvancer;
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

    /**
     * 지시 상태기계(E3.5-3 plan §4) — ① 미적재 → 수집 ② 집행 성공 · 검증 미완 → 재채집
     * ③ 외부 볼륨 + 축 부재 → 보류 ④ 계획 거절 → 보류 ⑤ 계획 성립 → 동결 + 집행.
     */
    @Override
    public AgentDirective directiveFor(GuestServer server, ProvisioningProgress progress) {
        Optional<GuestServerDetail> detail = guestServerDetailRepository.findByServerIdWithBoardModel(server.getId());
        String inventoryJson = detail.map(GuestServerDetail::getRaidInventoryJson).orElse(null);
        if (inventoryJson == null) {
            return AgentDirective.RAID_INVENTORY;                                    // ① 현행 유지(E3.5-1)
        }
        // ② 집행이 성공 close 됐고 검증이 아직 커서를 전진시키지 못했으면 재채집 지시 — close 응답 ·
        //    재체크인 양쪽이 같은 판정을 받는다(응답 유실 재전송 멱등).
        boolean applied = raidLedger.latestOf(server.getId(), ProvisioningPhaseStep.RAID_APPLYING)
                .filter(h -> h.getStatus() == ProvisioningStatus.SUCCEEDED).isPresent();
        if (applied) {
            return AgentDirective.RAID_VERIFY;
        }
        RaidInventory inventory = parseStored(inventoryJson, server);
        if (inventory == null) {
            return AgentDirective.WAIT;                                              // 저장본 손상 — 관용 대기
        }
        LocalDateTime now = LocalDateTime.now();
        long foreign = inventory.volumes().stream().filter(v -> !isOurs(v)).count();
        if (foreign > 0) {
            // ③ 외부 볼륨 — 파괴를 임의 선택할 수 없다(결정 3 · D-7). spvR* 잔여는 재구성 대상이라 제외.
            raidLedger.holdInstant(server, ProvisioningPhaseStep.RAID_APPLYING, RaidLedger.POLICY_UNDECIDED,
                    "외부 기존 볼륨 " + foreign + "개 — \"기존 구성 : 보존 / 파괴\" 축 도입(E3.5-4) 전에는 집행하지 않습니다", now);
            return AgentDirective.WAIT;
        }
        Optional<RaidPlanOutcome> outcome = resolutionProvider.planFor(
                server.getId(), inventory, RaidExistingConfigPolicy.DESTROY);
        if (outcome.isEmpty()) {
            log.warn("RAID phase 커서인데 계획 창 밖(활성 할당 · RAID 단계 부재) : guestServerId={}", server.getId());
            return AgentDirective.WAIT;
        }
        if (outcome.get() instanceof RaidPlanRejection rejection) {
            // ④ 거절 — 정의서 수정으로 풀리는 보류(실패 낙인 없음).
            raidLedger.holdInstant(server, ProvisioningPhaseStep.RAID_APPLYING, RaidLedger.PLAN_REJECTED,
                    rejection.code() + " — " + rejection.detail(), now);
            return AgentDirective.WAIT;
        }
        RaidPlan plan = (RaidPlan) outcome.get();
        if (plan.volumes().isEmpty() && plan.passthroughs().isEmpty()) {
            // 묶음 규칙 없는 정의서(설치기 자동) — 집행 · 검증할 것이 없으니 phase 완주로 전진한다.
            phaseCursorAdvancer.advanceOrComplete(progress, server.getId(), now);
            log.info("RAID 계획이 비어 phase 완주 처리 : guestServerId={}", server.getId());
            return AgentDirective.REBOOT;
        }
        raidLedger.freezePlanned(server, objectMapper.writeValueAsString(plan), now);   // ⑤ 동결(결정 2)
        return AgentDirective.RAID_APPLY;
    }

    /** RAID_APPLY 에 동봉할 집행 축약형 — 동결본(결정 2)에서 파생해 지시와 payload 가 같은 SSOT 를 본다. */
    @Override
    public RaidApplyPayload raidApplyPayloadFor(GuestServer server, ProvisioningProgress progress) {
        RaidPlan frozen = loadFrozenPlan(server.getId());
        return frozen == null ? null : RaidApplyPayload.from(frozen);
    }

    @Override
    public void onStepClosed(GuestServer server, ProvisioningProgress progress, ProvisioningHistory step) {
        if (step.getStepCode() == ProvisioningPhaseStep.RAID_INVENTORY_COLLECTING) {
            consumeInventoryReport(server, progress, step);
            return;
        }
        if (step.getStepCode() == ProvisioningPhaseStep.RAID_VERIFYING) {
            consumeVerificationReport(server, progress, step);
        }
        // RAID_APPLYING 성공 close 는 소비할 것이 없다 — 로그 원문은 행이 보존하고, 다음 지시(RAID_VERIFY)는
        // close 응답의 directiveFor 재계산(판정 ②)이 낸다.
    }

    /** 인벤토리 보고 소비(E3.5-1) — 파싱 → 카드 대조 → 적재. */
    private void consumeInventoryReport(GuestServer server, ProvisioningProgress progress, ProvisioningHistory step) {
        LocalDateTime now = LocalDateTime.now();
        RaidInventory inventory;
        try {
            inventory = inventoryParser.parse(step.getStatusMeta());
        } catch (RaidInventoryParser.ReportUnparsableException e) {
            log.warn("RAID 인벤토리 해석 불가 — 원문은 원장 보존 : guestServerId={}", server.getId(), e);
            raidLedger.failInstant(server, progress, ProvisioningPhaseStep.RAID_INVENTORY_COLLECTING,
                    RaidLedger.REPORT_UNPARSABLE, e.getMessage(), now);
            return;
        }

        Optional<RaidConfigurationTarget> target = resolutionProvider.resolveFor(server.getId());
        if (target.isPresent() && target.get().raidCardId() != null && !cardMatches(server, progress, target.get(), inventory, now)) {
            return;   // 사유는 cardMatches 가 원장에 남겼다 — 적재 생략(원문은 원장 보존)
        }

        requireDetail(server).enrichRaidInventory(objectMapper.writeValueAsString(inventory));
        log.info("RAID 인벤토리 적재 : guestServerId={}, card={}, disks={}, volumes={}",
                server.getId(), inventory.card() == null ? null : inventory.card().pciSubsystemId(),
                inventory.disks().size(), inventory.volumes().size());
    }

    /**
     * 검증 보고 소비(E3.5-3 결정 4) — 재채집 파싱 → 동결 계획 대조 → 일치 = raid_volume replace 기록 +
     * 인벤토리 재적재 + 커서 전진 / 불일치 = RESULT_MISMATCH 실패.
     */
    private void consumeVerificationReport(GuestServer server, ProvisioningProgress progress, ProvisioningHistory step) {
        LocalDateTime now = LocalDateTime.now();
        RaidInventory observed;
        try {
            observed = inventoryParser.parse(step.getStatusMeta());
        } catch (RaidInventoryParser.ReportUnparsableException e) {
            raidLedger.failInstant(server, progress, ProvisioningPhaseStep.RAID_VERIFYING,
                    RaidLedger.REPORT_UNPARSABLE, e.getMessage(), now);
            return;
        }
        RaidPlan frozen = loadFrozenPlan(server.getId());
        if (frozen == null) {
            raidLedger.failInstant(server, progress, ProvisioningPhaseStep.RAID_VERIFYING,
                    RaidLedger.RESULT_MISMATCH, "동결 계획(PLANNED)이 원장에 없습니다 — 집행 이력 손상", now);
            return;
        }
        String mismatch = RaidResultVerifier.mismatchReason(frozen, observed);
        if (mismatch != null) {
            raidLedger.failInstant(server, progress, ProvisioningPhaseStep.RAID_VERIFYING,
                    RaidLedger.RESULT_MISMATCH, mismatch, now);
            return;
        }
        recordVolumes(server, frozen, observed);
        requireDetail(server).enrichRaidInventory(objectMapper.writeValueAsString(observed));   // 화면 = 실물
        phaseCursorAdvancer.advanceOrComplete(progress, server.getId(), now);
        log.info("RAID 집행 검증 통과 — raid_volume {}건 기록 · 커서 전진 : guestServerId={}",
                frozen.volumes().size() + frozen.passthroughs().size(), server.getId());
    }

    /** 검증 통과 실물의 replace 기록(결정 D-8) — 게스트 단위 전부 삭제 후 동결 계획 기준으로 다시 쓴다. */
    private void recordVolumes(GuestServer server, RaidPlan frozen, RaidInventory observed) {
        raidVolumeRepository.deleteByGuestServer_Id(server.getId());
        List<RaidVolume> rows = new java.util.ArrayList<>();
        for (PlannedVolume volume : frozen.volumes()) {
            String state = observed.volumes().stream()
                    .filter(v -> volume.name().equalsIgnoreCase(v.name() == null ? "" : v.name().trim()))
                    .map(RaidExistingVolume::state).findFirst().orElse(null);
            rows.add(RaidVolume.of(server, volume.name(), volume.level(),
                    objectMapper.writeValueAsString(volume.memberSlots()), volume.usableBytes(),
                    volume.role(), volume.ruleNo(), state));
        }
        for (PlannedPassthrough passthrough : frozen.passthroughs()) {
            rows.add(RaidVolume.of(server, passthrough.slot(), null,
                    objectMapper.writeValueAsString(List.of(passthrough.slot())), passthrough.usableBytes(),
                    passthrough.role(), passthrough.ruleNo(), null));
        }
        raidVolumeRepository.saveAll(rows);
    }

    private RaidPlan loadFrozenPlan(java.util.UUID guestServerId) {
        return raidLedger.latestFrozenPlanMeta(guestServerId)
                .map(meta -> objectMapper.readTree(meta).path("plan"))
                .filter(node -> !node.isMissingNode() && !node.isNull())
                .map(node -> objectMapper.treeToValue(node, RaidPlan.class))
                .orElse(null);
    }

    private RaidInventory parseStored(String inventoryJson, GuestServer server) {
        try {
            return objectMapper.readValue(inventoryJson, RaidInventory.class);
        } catch (RuntimeException e) {
            log.warn("저장 인벤토리 해석 불가 — 대기 : guestServerId={}", server.getId(), e);
            return null;
        }
    }

    private boolean isOurs(RaidExistingVolume volume) {
        return volume.name() != null && volume.name().trim().startsWith(OUR_NAME_PREFIX);
    }

    private GuestServerDetail requireDetail(GuestServer server) {
        return guestServerDetailRepository.findByServerIdWithBoardModel(server.getId())
                .orElseThrow(() -> new IllegalStateException(
                        "guest_server_detail 1:1 불변 위반 — 등록 seed 누락. guestServerId=" + server.getId()));
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
            raidLedger.failInstant(server, progress, ProvisioningPhaseStep.RAID_INVENTORY_COLLECTING,
                    RaidLedger.CARD_NOT_DETECTED,
                    "지정 카드 " + target.cardModelName() + "(" + target.pciSubsystemId() + ") 를 감지하지 못했습니다", now);
            return false;
        }
        if (!target.pciSubsystemId().equalsIgnoreCase(inventory.card().pciSubsystemId())) {
            raidLedger.failInstant(server, progress, ProvisioningPhaseStep.RAID_INVENTORY_COLLECTING,
                    RaidLedger.CARD_MISMATCH,
                    "지정 " + target.cardModelName() + "(" + target.pciSubsystemId() + ") ≠ 감지 "
                            + inventory.card().pciSubsystemId(), now);
            return false;
        }
        return true;
    }
}
