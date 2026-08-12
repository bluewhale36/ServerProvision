package com.example.serverprovision.provisioning.assignment.service;

import com.example.serverprovision.execution.entity.GuestServer;
import com.example.serverprovision.execution.entity.GuestServerDetail;
import com.example.serverprovision.execution.enums.ProvisioningPhase;
import com.example.serverprovision.execution.exception.GuestServerNotFoundException;
import com.example.serverprovision.execution.repository.GuestServerDetailRepository;
import com.example.serverprovision.execution.repository.GuestServerRepository;
import com.example.serverprovision.management.board.entity.BoardModel;
import com.example.serverprovision.management.board.repository.BoardModelRepository;
import com.example.serverprovision.provisioning.assignment.entity.SettingAssignment;
import com.example.serverprovision.provisioning.assignment.dto.response.AssignmentFormResponse;
import com.example.serverprovision.provisioning.assignment.dto.response.AssignmentPlanResponse;
import com.example.serverprovision.provisioning.assignment.dto.response.DefinitionOptionResponse;
import com.example.serverprovision.provisioning.assignment.enums.AssignmentBlockKind;
import com.example.serverprovision.provisioning.assignment.enums.AssignmentBlockKind.AssignmentBlock;
import com.example.serverprovision.provisioning.assignment.vo.AssignmentEligibility;
import com.example.serverprovision.provisioning.assignment.vo.OwnedPhases;
import com.example.serverprovision.provisioning.assignment.repository.SettingAssignmentRepository;
import com.example.serverprovision.provisioning.setting.dto.response.SettingSummaryResponse;
import com.example.serverprovision.provisioning.setting.vo.RequiredBoardModel;
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
    // U3-5-a — 할당 가능성 판정 입력. execution 방향 참조이며 setting 을 참조하지 않는다(패키지 순환 회피).
    private final GuestServerRepository guestServerRepository;
    private final GuestServerDetailRepository guestServerDetailRepository;
    private final BoardModelRepository boardModelRepository;

    /**
     * 정의서 선택지에 <b>이 서버에 붙일 수 있는가</b>를 덧댄다 (U3-5-a).
     *
     * <p>선택지 목록 자체는 호출자(컨트롤러)가 {@code SettingQueryService} 에서 이미 받아 넘긴다. 이 서비스가
     * 직접 조회하면 assignment → setting 참조가 생기는데, setting 은 이미 {@code AssignmentUsageInspector} 로
     * assignment 를 참조하고 있어 패키지가 양방향이 된다(R7 이 없앤 형태). 판정에 필요한 요구 보드는 요약이
     * 이미 싣고 있으므로 조회할 것도 없다.</p>
     *
     * <p>회수된 서버는 어떤 정의서를 골라도 막히므로 화면이 폼 자체를 닫는다 — 그 경우 이 목록은 쓰이지
     * 않지만, 판정을 여기서 빼면 "왜 전부 잠겼는가" 를 화면이 따로 계산하게 되므로 그대로 둔다.</p>
     */
    public AssignmentFormResponse assignmentForm(UUID guestId, List<SettingSummaryResponse> assignable) {
        GuestServer server = guestServerRepository.findById(guestId)
                .orElseThrow(() -> new GuestServerNotFoundException(guestId));
        GuestServerDetail detail = guestServerDetailRepository.findByServerIdWithBoardModel(guestId).orElse(null);

        List<DefinitionOptionResponse> options = assignable.stream().map(summary -> {
            AssignmentEligibility eligibility = new AssignmentEligibility(server, detail, requiredBoardOf(summary));
            AssignmentBlock block = AssignmentBlockKind.evaluate(eligibility);
            return new DefinitionOptionResponse(
                    summary,
                    block != null ? block.reason() : null,
                    block == null && eligibility.boardUnverified());
        }).toList();

        // 폼 자체를 닫을 사유는 엔티티가 답한다 — 화면과 서버 가드가 같은 문자열을 쓰게 하는 유일한 방법이다.
        return new AssignmentFormResponse(server.assignBlockReason(), options);
    }

    /** 요약이 싣고 온 요구 보드를 값 객체로 되돌린다 — 요구하지 않으면 null. */
    private static RequiredBoardModel requiredBoardOf(SettingSummaryResponse summary) {
        return summary.requiredBoardModelId() == null
                ? null
                : new RequiredBoardModel(summary.requiredBoardModelId(), summary.requiredBoardModelName());
    }

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
                        snapshotHardwareMismatch(assignment),
                        orderedPlan(assignment.getOwnedPhases())))
                .orElseGet(AssignmentPlanResponse::unassigned);
    }

    /**
     * 이미 든 스냅샷이 이 서버의 하드웨어와 맞는가 (U3-5-a) — 맞으면 {@code null}, 아니면 경고 문구.
     *
     * <p>선택지 잠금과 <b>같은 판정</b>({@code AssignmentBlockKind})을 쓰되 입력이 다르다. 잠금은 정의서
     * 원본에서, 이것은 <b>얼린 스냅샷</b>에서 요구 하드웨어를 뽑는다 — 원본이 나중에 바뀌어도 이 서버가
     * 실제로 밟을 것은 얼린 그 값이기 때문이다. U3-1 이 payload 를 무변환 복사한 덕에 추출 코드가 같다.</p>
     *
     * <p>회수 여부는 여기서 보지 않는다. 회수는 이미 폼을 닫는 사유이고, 이 경고는 "할당은 있는데 그것으로
     * 진행할 수 없다" 를 알리는 자리다. 그래서 서버를 넘기되 하드웨어 축만 묻는다.</p>
     */
    private String snapshotHardwareMismatch(SettingAssignment assignment) {
        GuestServer server = assignment.getGuestServer();
        GuestServerDetail detail = guestServerDetailRepository
                .findByServerIdWithBoardModel(server.getId()).orElse(null);
        if (detail == null) {
            return null;   // 아직 수집 전 — 대조할 것이 없다(막지 않는다는 규칙과 같은 결)
        }
        RequiredBoardModel required = RequiredBoardModel.from(
                assignment.getProcesses().stream().map(p -> p.getPayload().request()).toList(),
                boardModelId -> boardModelRepository.findById(boardModelId)
                        .map(BoardModel::getModelName)
                        .orElse("등록되지 않은 보드"));
        return required == null ? null
                : required.blockReasonFor(detail.getBoardModel().getId(), detail.getBoardModel().getModelName());
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
