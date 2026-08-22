package com.example.serverprovision.provisioning.assignment.service;

import com.example.serverprovision.execution.engine.OwnedPhasesProvider;
import com.example.serverprovision.execution.enums.ProvisioningPhase;
import com.example.serverprovision.provisioning.assignment.repository.SettingAssignmentSnapshotRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;
import java.util.UUID;

/**
 * {@link OwnedPhasesProvider} 구현 — 할당 도메인이 execution 소유 SPI 를 채운다(dependency inversion).
 *
 * <p>활성 스냅샷(supersededAt IS NULL)의 동결 {@code ownedPhases} 를 read 한다. 활성 할당이 없으면
 * immutable 빈 Set — 분기 추가 0 으로 "진단 완주 종단" 종단 동작을 재현한다(결정 D-A · D-G).</p>
 */
@Component
@RequiredArgsConstructor
public class OwnedPhasesProviderImpl implements OwnedPhasesProvider {

    private final SettingAssignmentSnapshotRepository assignmentRepository;

    @Override
    @Transactional(readOnly = true)
    public Set<ProvisioningPhase> ownedPhasesOf(UUID guestId) {
        return assignmentRepository.findByGuestServer_IdAndSupersededAtIsNull(guestId)
                .map(assignment -> assignment.getOwnedPhases().asSet())
                .orElseGet(Set::of);
    }
}
