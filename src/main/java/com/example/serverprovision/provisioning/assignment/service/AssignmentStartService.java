package com.example.serverprovision.provisioning.assignment.service;

import com.example.serverprovision.execution.exception.ProvisioningStartRejectedException;
import com.example.serverprovision.execution.service.GuestServerCommandService;
import com.example.serverprovision.provisioning.assignment.repository.SettingAssignmentSnapshotRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 프로비저닝 개시 오케스트레이션(provisioning 측) — 개시와 활성 스냅샷 소비를 <b>한 트랜잭션</b>으로 원자 실행.
 *
 * <p>{@code execution.GuestServerCommandService.startProvisioning} 을 직접 고쳐 assignment 를 건드리면
 * execution→provisioning 순환(R7 이 제거)이 재생성된다. 대신 provisioning 이 오케스트레이터를 소유해
 * execution 의 개시({@code REQUIRED} 전파로 이 트랜잭션에 조인 — 가드/404/409 그대로 전파) + 활성
 * assignment 의 {@code markConsumed} 를 원자 실행한다(결정 D-D).</p>
 *
 * <p><b>미할당 개시는 거절한다(U3-6).</b> "할당 없는 게스트도 진단까지는 진행한다" 는 종전 허용은 R13
 * 이전의 세계다 — 진단이 자동 진행이 된 지금 개시의 실효는 "진단 이후로 나아가기" 이고, 미할당 개시는
 * 소급 완주 판정이 빈 소유 phase 를 보고 즉시 종단시켜 회수 전까지 어떤 액션도 못 하게 만든다.
 * 미할당 게스트는 수집을 마친 자리에서 멈춰 있어야 한다. 정상 흐름은 뷰가 버튼을 잠가 차단하고
 * (판정 재료 = {@code AssignmentPlanResponse.assigned} — 같은 활성 스냅샷 조회), 여기는 direct POST 안전망.</p>
 */
@Service
@RequiredArgsConstructor
public class AssignmentStartService {

    private final GuestServerCommandService guestServerCommandService;
    private final SettingAssignmentSnapshotRepository assignmentRepository;

    @Transactional
    public void startProvisioning(UUID guestId) {
        var assignment = assignmentRepository.findByGuestServer_IdAndSupersededAtIsNull(guestId)
                .orElseThrow(() -> ProvisioningStartRejectedException.unassigned(guestId));
        // 개시 가드(미개시·미회수)를 통과하면 startedAt 기록 — 실패 시 아래 소비까지 함께 롤백된다.
        guestServerCommandService.startProvisioning(guestId);
        assignment.markConsumed(LocalDateTime.now());
    }
}
