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
import com.example.serverprovision.execution.enums.ProvisioningMotion;
import com.example.serverprovision.execution.enums.ProvisioningPhase;
import com.example.serverprovision.execution.enums.ProvisioningPhaseStep;
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
     * 재시도는 인벤토리 재채집부터(HF15-2 · 실기 3호 F-8) — 계획은 저장된 인벤토리에서 나오는데, 실패한 집행이 카드에
     * 남긴 볼륨(spvR1V1 등)을 옛 인벤토리는 모른다. 재시도 커서를 진입 step 에 세우면 {@link #directiveFor} 의
     * "진입 대기 = 재채집" 규칙이 최신 실물로 계획을 다시 세운다(잔여는 재구성 대상으로 지워진다).
     */
    @Override
    public ProvisioningPhaseStep retryEntryStep(ProvisioningProgress progress) {
        return ProvisioningPhaseStep.RAID_INVENTORY_COLLECTING;
    }

    /**
     * 지시 상태기계(E3.5-3 plan §4 · HF15-2 개정) — ① 미적재 또는 phase 진입 대기 → 수집 ② 집행 성공 · 검증 미완 →
     * 재채집 ③ 외부 볼륨 + 축 부재 → 보류 ④ 계획 거절 → 보류 ⑤ 계획 성립 → 동결 + 집행.
     */
    @Override
    public AgentDirective directiveFor(GuestServer server, ProvisioningProgress progress) {
        Optional<GuestServerDetail> detail = guestServerDetailRepository.findByServerIdWithBoardModel(server.getId());
        String inventoryJson = detail.map(GuestServerDetail::getRaidInventoryJson).orElse(null);
        // ① 미적재(E3.5-1) 또는 phase 진입 · 재시도 직후의 부팅 대기(HF15-2) — 계획은 항상 이 부팅에서 채집한
        //    실물로 세운다. 진단 때 채집한 인벤토리는 그 사이(펌웨어 · 설정 · 실패한 집행)에 낡을 수 있다.
        if (inventoryJson == null || awaitingInventoryOnEntry(progress)) {
            return AgentDirective.RAID_INVENTORY;
        }
        // ② 집행이 성공 close 됐고 검증이 아직 그 결과를 거두지 않았으면 재채집 지시 — close 응답 ·
        //    재체크인 양쪽이 같은 판정을 받는다(응답 유실 재전송 멱등). 검증이 이미 실패로 닫혔으면 재시도가
        //    새 계획(잔여 재구성)으로 다시 집행해야 하므로 여기 걸리지 않는다.
        if (raidLedger.awaitingVerification(server.getId())) {
            return AgentDirective.RAID_VERIFY;
        }
        RaidInventory inventory = parseStored(inventoryJson, server);
        if (inventory == null) {
            return AgentDirective.WAIT;                                              // 저장본 손상 — 관용 대기
        }
        LocalDateTime now = LocalDateTime.now();
        Optional<RaidExistingConfigPolicy> declared = resolutionProvider.policyOf(server.getId());
        if (declared.isEmpty()) {
            // ③ 축 미지정(구 저장본) — 외부 볼륨이 있으면 파괴를 임의 선택할 수 없다(결정 3 · D-7).
            //    spvR* 잔여는 재구성 대상이라 제외(판별 SSOT = RaidExistingVolume.isProvisionOwned).
            long foreign = inventory.volumes().stream().filter(v -> !v.isProvisionOwned()).count();
            if (foreign > 0) {
                raidLedger.holdInstant(server, ProvisioningPhaseStep.RAID_APPLYING, RaidLedger.POLICY_UNDECIDED,
                        "외부 기존 볼륨 " + foreign + "개 — 정의서의 \"기존 구성 처리\" 를 선택하기 전에는 집행하지 않습니다", now);
                return AgentDirective.WAIT;
            }
        }
        Optional<RaidPlanOutcome> outcome = resolutionProvider.planFor(
                server.getId(), inventory, declared.orElse(RaidExistingConfigPolicy.DESTROY));
        if (outcome.isEmpty()) {
            log.warn("RAID phase 커서인데 계획 창 밖(활성 할당 · RAID 단계 부재) : guestServerId={}", server.getId());
            return AgentDirective.WAIT;
        }
        if (outcome.get() instanceof RaidPlanRejection rejection) {
            if (RaidPlanRejection.EXISTING_CONFIG.equals(rejection.code())) {
                // 명시한 보존과 실물의 모순은 보류가 아니라 정직한 실패다(E3.5-4 결정 3 · D-7 원문).
                // 거절 코드가 늘면 승격 여부를 코드별로 정해야 하는 자리 — 조용히 보류로 흘리지 말 것.
                raidLedger.failInstant(server, progress, ProvisioningPhaseStep.RAID_APPLYING,
                        RaidLedger.EXISTING_CONFIG, rejection.detail(), now);
                return AgentDirective.REBOOT;
            }
            // ④ 그 외 거절 — 정의서 수정으로 풀리는 보류(실패 낙인 없음).
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

    /** 진입 step 에서 부팅을 기다리는 커서인가 — 진입(pre-position) · 재시도 되감기 둘 다 여기로 온다. */
    private static boolean awaitingInventoryOnEntry(ProvisioningProgress progress) {
        return progress.getCurrentStep() == ProvisioningPhaseStep.RAID_INVENTORY_COLLECTING
                && progress.getMotion() == ProvisioningMotion.AWAITING_BOOT;
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
        GuestServerDetail detail = requireDetail(server);
        detail.enrichRaidInventory(objectMapper.writeValueAsString(observed));   // 화면 = 실물
        refreshOsVisibleDisks(detail, step.getStatusMeta());                      // HF15-5 — 볼륨이 커널에 보인 뒤의 lsblk
        phaseCursorAdvancer.advanceOrComplete(progress, server.getId(), now);
        log.info("RAID 집행 검증 통과 — raid_volume {}건 기록 · 커서 전진 : guestServerId={}",
                frozen.volumes().size() + frozen.passthroughs().size(), server.getId());
    }

    /**
     * 검증 보고에 동봉된 OS 가시 디스크({@code disks} · lsblk 순서 · WWN)로 하드웨어 스펙의 디스크 목록만 갈아 넣는다
     * (HF15-5 · 실기 3호 F-6 · F-9). 진단 때의 목록은 볼륨 생성 전(멤버 디스크 그대로)이라 OS 설치 디스크 번호의 근거가
     * 못 된다. 동봉이 없으면(구 에이전트) 그대로 둔다 — 디스크 선택은 계열 순서 규칙으로 내려간다.
     */
    private void refreshOsVisibleDisks(GuestServerDetail detail, String statusMeta) {
        List<com.example.serverprovision.execution.vo.HardwareSpec.DiskInfo> disks;
        try {
            disks = com.example.serverprovision.execution.engine.diagnose.OsVisibleDiskParser.parse(
                    objectMapper.readTree(statusMeta).path("disks"));
        } catch (RuntimeException e) {
            return;   // 봉투 자체는 inventoryParser 가 이미 읽었다 — 디스크 동봉 해석 실패는 갱신 생략
        }
        if (disks.isEmpty()) {
            return;
        }
        com.example.serverprovision.execution.vo.HardwareSpec current = null;
        if (detail.getHardwareSpec() != null && !detail.getHardwareSpec().isBlank()) {
            try {
                current = objectMapper.readValue(detail.getHardwareSpec(),
                        com.example.serverprovision.execution.vo.HardwareSpec.class);
            } catch (RuntimeException ignored) {
                // 옛 저장본 해석 불가 — 디스크만 담은 스펙으로 갱신
            }
        }
        com.example.serverprovision.execution.vo.HardwareSpec merged = new com.example.serverprovision.execution.vo.HardwareSpec(
                current == null ? null : current.cpuSockets(),
                current == null ? null : current.memoryModules(),
                disks,
                current == null ? null : current.pcieDevices());
        detail.updateHardwareSpec(objectMapper.writeValueAsString(merged));
        log.info("OS 가시 디스크 갱신(RAID 검증 재채집) : guestServerId={}, disks={}", detail.getGuestServer().getId(), disks.size());
    }

    /** 검증 통과 실물의 replace 기록(결정 D-8) — 게스트 단위 전부 삭제 후 동결 계획 기준으로 다시 쓴다. */
    private void recordVolumes(GuestServer server, RaidPlan frozen, RaidInventory observed) {
        raidVolumeRepository.deleteByGuestServer_Id(server.getId());
        List<RaidVolume> rows = new java.util.ArrayList<>();
        for (PlannedVolume volume : frozen.volumes()) {
            Optional<RaidExistingVolume> matched = observed.volumes().stream()
                    .filter(v -> volume.name().equalsIgnoreCase(v.name() == null ? "" : v.name().trim()))
                    .findFirst();
            rows.add(RaidVolume.of(server, volume.name(), volume.level(),
                    objectMapper.writeValueAsString(volume.memberSlots()), volume.usableBytes(),
                    volume.role(), volume.ruleNo(),
                    matched.map(RaidExistingVolume::state).orElse(null),
                    matched.map(RaidExistingVolume::wwn).orElse(null)));   // E4 인계 키(E3.5-4 증보)
        }
        for (PlannedPassthrough passthrough : frozen.passthroughs()) {
            rows.add(RaidVolume.of(server, passthrough.slot(), null,
                    objectMapper.writeValueAsString(List.of(passthrough.slot())), passthrough.usableBytes(),
                    passthrough.role(), passthrough.ruleNo(), null,
                    null));   // 단독 디스크 WWN 은 원천이 다르다 — CP7 실측 후 확장(plan 리스크)
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

    private GuestServerDetail requireDetail(GuestServer server) {
        return guestServerDetailRepository.findByServerIdWithBoardModel(server.getId())
                .orElseThrow(() -> new IllegalStateException(
                        "guest_server_detail 1:1 불변 위반 — 등록 seed 누락. guestServerId=" + server.getId()));
    }

    /** 정의서 지정 카드와 감지 카드의 Subsystem 대조 — 불일치 · 미감지는 원장 사유로 남기고 false. */
    /**
     * 카드 대조(E3.5-1) — 판정은 {@link RaidCardMatch#judge}(화면의 개시 전 예고와 같은 진리표 · E3.5-5-a D4)가 내고,
     * 여기서는 그 판정에 원장 기록만 얹는다. 호출부가 카드를 지정한 할당에서만 부르므로 NOT_APPLICABLE 은 오지 않는다.
     */
    private boolean cardMatches(GuestServer server, ProvisioningProgress progress,
                                RaidConfigurationTarget target, RaidInventory inventory, LocalDateTime now) {
        return switch (RaidCardMatch.judge(target, inventory)) {
            case UNVERIFIABLE -> {
                // 소프트참조 카드가 사라졌거나 카드에 Subsystem 이 미등록 — 대조할 정본이 없으니 막지 않는다
                log.warn("RAID 카드 대조 생략 — 지정 카드의 Subsystem 미확보 : guestServerId={}, raidCardId={}",
                        server.getId(), target.raidCardId());
                yield true;
            }
            case NOT_DETECTED -> {
                raidLedger.failInstant(server, progress, ProvisioningPhaseStep.RAID_INVENTORY_COLLECTING,
                        RaidLedger.CARD_NOT_DETECTED,
                        "지정 카드 " + target.cardModelName() + "(" + target.pciSubsystemId() + ") 를 감지하지 못했습니다", now);
                yield false;
            }
            case MISMATCH -> {
                raidLedger.failInstant(server, progress, ProvisioningPhaseStep.RAID_INVENTORY_COLLECTING,
                        RaidLedger.CARD_MISMATCH,
                        "지정 " + target.cardModelName() + "(" + target.pciSubsystemId() + ") ≠ 감지 "
                                + inventory.card().pciSubsystemId(), now);
                yield false;
            }
            case MATCH, NOT_APPLICABLE -> true;
        };
    }
}
