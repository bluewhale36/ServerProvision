package com.example.serverprovision.provisioning.assignment.service;

import com.example.serverprovision.execution.exception.GuestServerNotFoundException;
import com.example.serverprovision.execution.service.GuestServerCommandService;
import com.example.serverprovision.provisioning.assignment.entity.SettingAssignmentSnapshot;
import com.example.serverprovision.provisioning.assignment.repository.SettingAssignmentSnapshotRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * {@link AssignmentStartService} 단위 — 개시 + 활성 스냅샷 소비의 원자 오케스트레이션(D-D).
 */
@ExtendWith(MockitoExtension.class)
class AssignmentStartServiceTest {

    @Mock GuestServerCommandService guestServerCommandService;
    @Mock SettingAssignmentSnapshotRepository assignmentRepository;

    @InjectMocks AssignmentStartService service;

    private static final UUID GUEST = UUID.randomUUID();

    @Test
    @DisplayName("개시 + 활성 스냅샷 존재 → startProvisioning 후 markConsumed")
    void start_withActiveAssignment_marksConsumed() {
        SettingAssignmentSnapshot assignment = org.mockito.Mockito.mock(SettingAssignmentSnapshot.class);
        given(assignmentRepository.findByGuestServer_IdAndSupersededAtIsNull(GUEST))
                .willReturn(Optional.of(assignment));

        service.startProvisioning(GUEST);

        verify(guestServerCommandService).startProvisioning(GUEST);
        verify(assignment).markConsumed(any(LocalDateTime.class));
    }

    @Test
    @DisplayName("활성 스냅샷 없어도 개시는 허용(markConsumed no-op)")
    void start_withoutActiveAssignment_noop() {
        given(assignmentRepository.findByGuestServer_IdAndSupersededAtIsNull(GUEST))
                .willReturn(Optional.empty());

        service.startProvisioning(GUEST);

        verify(guestServerCommandService).startProvisioning(GUEST);
    }

    @Test
    @DisplayName("개시 가드 거절 시 소비 경로에 도달하지 않는다(한 트랜잭션 원자성)")
    void start_guardRejects_beforeConsume() {
        willThrow(new GuestServerNotFoundException(GUEST))
                .given(guestServerCommandService).startProvisioning(GUEST);

        assertThatThrownBy(() -> service.startProvisioning(GUEST))
                .isInstanceOf(GuestServerNotFoundException.class);

        verify(assignmentRepository, never()).findByGuestServer_IdAndSupersededAtIsNull(any());
    }
}
