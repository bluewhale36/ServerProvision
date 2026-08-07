package com.example.serverprovision.provisioning.assignment.service;

import com.example.serverprovision.execution.entity.GuestServer;
import com.example.serverprovision.provisioning.assignment.entity.SettingAssignment;
import com.example.serverprovision.provisioning.assignment.repository.SettingAssignmentRepository;
import com.example.serverprovision.provisioning.assignment.vo.OwnedPhases;
import com.example.serverprovision.provisioning.assignment.vo.SourceDefinitionRef;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * {@link AssignmentReapService} 단위 — 미소비 supersede 스냅샷의 지연 purge(U3-2-a, DA3).
 *
 * <p>수거 술어(SUPERSEDED ∧ 미소비 ∧ TTL 경과) 자체는 파생 쿼리 메서드명이 강제하고 그 실행 정합성(소비 이력 ·
 * 활성 · TTL 미경과 보존)은 CP5 샌드박스(실 MariaDB)에서 확인한다(프로젝트 @DataJpaTest 선례 부재 — 오펀 reaper 와
 * 동형). 본 단위는 서비스가 <b>TTL 경계(now - ttl)를 정확히 계산</b>해 쿼리에 넘기고, 쿼리가 돌려준 행만 삭제하며
 * 건수를 반환하는지, 비어 있으면 삭제하지 않는지를 고정한다.</p>
 */
@ExtendWith(MockitoExtension.class)
class AssignmentReapServiceTest {

    @Mock SettingAssignmentRepository assignmentRepository;

    AssignmentReapService service;

    @BeforeEach
    void setUp() {
        service = new AssignmentReapService(assignmentRepository);
        ReflectionTestUtils.setField(service, "ttl", Duration.ofHours(24));
    }

    private SettingAssignment supersededUnconsumed(long id) {
        SettingAssignment assignment = SettingAssignment.create(
                org.mockito.Mockito.mock(GuestServer.class),
                new SourceDefinitionRef(1L, "web-standard"), OwnedPhases.empty());
        assignment.supersede(LocalDateTime.now().minusDays(2));   // 미소비 상태로 논리 종료
        ReflectionTestUtils.setField(assignment, "id", id);
        return assignment;
    }

    @Test
    @DisplayName("purgeExpired — TTL 경계(now - ttl)로 조회한 미소비 supersede 행을 hard-delete + 건수 반환")
    void purgeExpired_deletesReturnedRows() {
        List<SettingAssignment> expired = List.of(supersededUnconsumed(1L), supersededUnconsumed(2L));
        given(assignmentRepository
                .findBySupersededAtIsNotNullAndConsumedAtIsNullAndSupersededAtBefore(any()))
                .willReturn(expired);

        LocalDateTime before = LocalDateTime.now().minusHours(24);
        int purged = service.purgeExpired();
        LocalDateTime after = LocalDateTime.now().minusHours(24);

        assertThat(purged).isEqualTo(2);

        // TTL 경계 검증 — 쿼리에 넘긴 threshold 가 (now - 24h) 창 안이다.
        ArgumentCaptor<LocalDateTime> thresholdCaptor = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(assignmentRepository)
                .findBySupersededAtIsNotNullAndConsumedAtIsNullAndSupersededAtBefore(thresholdCaptor.capture());
        assertThat(thresholdCaptor.getValue()).isBetween(before, after);

        // 쿼리가 돌려준 바로 그 행들만 삭제(서비스가 재필터하지 않는다).
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Iterable<SettingAssignment>> deleteCaptor = ArgumentCaptor.forClass(Iterable.class);
        verify(assignmentRepository).deleteAll(deleteCaptor.capture());
        assertThat(deleteCaptor.getValue()).containsExactlyElementsOf(expired);
    }

    @Test
    @DisplayName("purgeExpired — 수거 대상 없으면 삭제 호출 없이 0 반환")
    void purgeExpired_noExpired_noop() {
        given(assignmentRepository
                .findBySupersededAtIsNotNullAndConsumedAtIsNullAndSupersededAtBefore(any()))
                .willReturn(List.of());

        int purged = service.purgeExpired();

        assertThat(purged).isZero();
        verify(assignmentRepository, never()).deleteAll(any());
    }
}
