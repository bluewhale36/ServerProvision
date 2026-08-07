package com.example.serverprovision.provisioning.assignment.service;

import com.example.serverprovision.execution.enums.ProvisioningPhase;
import com.example.serverprovision.provisioning.assignment.dto.response.AssignmentPlanResponse;
import com.example.serverprovision.provisioning.assignment.vo.OwnedPhases;
import com.example.serverprovision.provisioning.assignment.repository.SettingAssignmentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 할당 조회 — 상세 화면 '계획 phase rail' 입력 공급(read-only).
 */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class AssignmentQueryService {

    private final SettingAssignmentRepository assignmentRepository;

    /**
     * 게스트의 활성 할당 계획 — 밟을 예정 phase 를 {@code ProvisioningPhase} 선언 순으로(진단 리눅스 포함) 담는다.
     * 활성 할당이 없으면 {@link AssignmentPlanResponse#unassigned()}. 실제 진행 커서 겹침은 뷰 조립
     * ({@code PlannedPhaseRailResponse.of})이 담당한다 — 계획(assigned)과 실제(started)를 분리한다.
     */
    public AssignmentPlanResponse plannedPhasesOf(UUID guestId) {
        return assignmentRepository.findByGuestServer_IdAndSupersededAtIsNull(guestId)
                .map(assignment -> new AssignmentPlanResponse(
                        true,
                        assignment.getSourceDefinitionRef().getDefinitionName(),
                        assignment.state(),
                        assignment.reassignBlockReason(),   // 뷰 disabled + tooltip 판정 SSOT(서버 가드와 동일 소스)
                        orderedPlan(assignment.getOwnedPhases())))
                .orElseGet(AssignmentPlanResponse::unassigned);
    }

    /** 진단 리눅스(무조건 phase) + 소유 phase 를 선언 순으로 나열. */
    private static List<ProvisioningPhase> orderedPlan(OwnedPhases ownedPhases) {
        List<ProvisioningPhase> plan = new ArrayList<>();
        plan.add(ProvisioningPhase.DIAGNOSE_LINUX);   // 정의서 소비 없이 항상 밟는 phase(PhaseSequence 선례)
        for (ProvisioningPhase phase : ProvisioningPhase.values()) {
            if (ownedPhases.contains(phase)) {
                plan.add(phase);
            }
        }
        return List.copyOf(plan);
    }
}
