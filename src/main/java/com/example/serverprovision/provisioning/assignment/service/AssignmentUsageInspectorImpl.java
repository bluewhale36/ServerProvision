package com.example.serverprovision.provisioning.assignment.service;

import com.example.serverprovision.provisioning.assignment.repository.SettingAssignmentSnapshotRepository;
import com.example.serverprovision.provisioning.setting.service.AssignmentUsageInspector;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * {@link AssignmentUsageInspector} 구현 — 할당 도메인이 setting 소유 SPI 를 채운다(dependency inversion,
 * DEC-D). 이미 존재하는 {@code assignment → setting} 방향만 쓰므로 순환이 없다.
 *
 * <p>활성 할당({@code supersededAt IS NULL}) 만 센다. supersede 된 이력 행은 재할당으로 논리 종료된
 * 과거 스냅샷이라 "현재 이 정의서를 쓰는 게스트" 경고의 대상이 아니다(잔여 3 — 활성 기준 채택).</p>
 */
@Component
@RequiredArgsConstructor
public class AssignmentUsageInspectorImpl implements AssignmentUsageInspector {

    private final SettingAssignmentSnapshotRepository assignmentRepository;

    @Override
    @Transactional(readOnly = true)
    public long countReferencing(Long definitionId) {
        return assignmentRepository.countBySourceDefinitionRef_DefinitionIdAndSupersededAtIsNull(definitionId);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean isReferenced(Long definitionId) {
        return assignmentRepository.existsBySourceDefinitionRef_DefinitionIdAndSupersededAtIsNull(definitionId);
    }
}
