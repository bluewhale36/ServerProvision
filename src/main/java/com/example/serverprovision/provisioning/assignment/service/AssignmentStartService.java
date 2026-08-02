package com.example.serverprovision.provisioning.assignment.service;

import com.example.serverprovision.execution.service.GuestServerCommandService;
import com.example.serverprovision.provisioning.assignment.repository.SettingAssignmentRepository;
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
 * assignment 의 {@code markConsumed} 를 원자 실행한다(결정 D-D). 활성 할당이 없어도 개시는 허용(markConsumed
 * no-op) — 할당 없는 게스트도 진단까지는 진행한다.</p>
 */
@Service
@RequiredArgsConstructor
public class AssignmentStartService {

    private final GuestServerCommandService guestServerCommandService;
    private final SettingAssignmentRepository assignmentRepository;

    @Transactional
    public void startProvisioning(UUID guestId) {
        // 개시 가드(미개시·미회수)를 통과하면 startedAt 기록 — 실패 시 아래 소비까지 함께 롤백된다.
        guestServerCommandService.startProvisioning(guestId);
        assignmentRepository.findByGuestServer_IdAndSupersededAtIsNull(guestId)
                .ifPresent(assignment -> assignment.markConsumed(LocalDateTime.now()));
    }
}
