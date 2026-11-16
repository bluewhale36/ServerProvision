package com.example.serverprovision.provisioning.assignment.service;

import com.example.serverprovision.execution.engine.raid.RaidConfigurationResolutionProvider;
import com.example.serverprovision.execution.engine.raid.RaidExistingConfigPolicy;
import com.example.serverprovision.execution.engine.raid.RaidInventory;
import com.example.serverprovision.execution.engine.raid.RaidPlanOutcome;
import com.example.serverprovision.provisioning.assignment.service.plan.RaidPlanner;
import com.example.serverprovision.execution.engine.raid.RaidConfigurationTarget;
import com.example.serverprovision.provisioning.assignment.repository.SettingAssignmentSnapshotRepository;
import com.example.serverprovision.provisioning.setting.dto.request.RaidConfigurationRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

/**
 * RAID 구성 목표 공급(E3.5-1) — 활성 스냅샷의 RAID 구성 payload 에서 카드 전제를 꺼낸다
 * ({@code BiosSettingResolutionProviderImpl} 과 같은 역전 자리). 카드는 소프트참조라 자원이
 * 사라졌을 수 있다 — 그 경우 Subsystem 을 null 로 나르고 대조는 엔진이 생략한다(관용 · WARN).
 */
@Component
@RequiredArgsConstructor
public class RaidConfigurationResolutionProviderImpl implements RaidConfigurationResolutionProvider {

    private final SettingAssignmentSnapshotRepository assignmentRepository;
    private final RaidCardRequirementReader requirementReader;

    @Override
    @Transactional(readOnly = true)
    public Optional<RaidPlanOutcome> planFor(UUID guestServerId, RaidInventory inventory,
                                             RaidExistingConfigPolicy policy) {
        return activeRaidRequest(guestServerId)
                .map(raid -> RaidPlanner.plan(raid.getDiskGroups(), raid.getVolumePriorities(), inventory, policy));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<RaidConfigurationTarget> resolveFor(UUID guestServerId) {
        // 창 밖(활성 할당 없음 · RAID 구성 단계 없음)이면 empty. 지정 카드 해석은 할당 판정과 같은 변환(R20)
        return activeRaidRequest(guestServerId)
                .map(raid -> requirementReader.requiredOf(raid).toTarget());
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<RaidExistingConfigPolicy> policyOf(UUID guestServerId) {
        return activeRaidRequest(guestServerId)
                .map(RaidConfigurationRequest::getExistingConfigPolicy)
                .map(policy -> switch (policy) {   // U 어휘 → 실행 어휘 1:1 번역(plan 결정 1)
                    case PRESERVE -> RaidExistingConfigPolicy.PRESERVE;
                    case DESTROY -> RaidExistingConfigPolicy.DESTROY;
                });
    }

    /** 활성 스냅샷의 RAID 구성 payload — resolveFor · planFor · policyOf 가 같은 창 판정을 공유한다. */
    private Optional<RaidConfigurationRequest> activeRaidRequest(UUID guestServerId) {
        return assignmentRepository
                .findByGuestServer_IdAndSupersededAtIsNull(guestServerId)
                .flatMap(snapshot -> snapshot.processRequestOf(RaidConfigurationRequest.class));
    }
}
