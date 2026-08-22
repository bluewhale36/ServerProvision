package com.example.serverprovision.provisioning.assignment.entity;

import com.example.serverprovision.execution.entity.GuestServer;
import com.example.serverprovision.provisioning.assignment.enums.AssignmentState;
import com.example.serverprovision.provisioning.assignment.vo.OwnedPhases;
import com.example.serverprovision.provisioning.assignment.vo.SourceDefinitionRef;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * {@link SettingAssignmentSnapshot} 도메인 메서드 단위 — 파생 상태 · 재할당 차단 사유 SSOT(U3-2-a, DA4).
 * 서버 가드(AssignmentCommandService.reassign)와 뷰 disabled 판정이 함께 호출하는 {@code reassignBlockReason()}
 * 의 상태별 반환을 고정한다.
 */
class SettingAssignmentSnapshotTest {

    private static final String CONSUMED_BLOCK_REASON = "이미 개시되어 재할당할 수 없습니다(회수 후 재등록 필요)";

    private SettingAssignmentSnapshot freshAssignment() {
        return SettingAssignmentSnapshot.create(
                mock(GuestServer.class), new SourceDefinitionRef(1L, "web-standard"), OwnedPhases.empty());
    }

    @Test
    @DisplayName("ACTIVE_UNCONSUMED(미개시 활성) → reassignBlockReason null (재할당 가능)")
    void reassignBlockReason_unconsumed_null() {
        SettingAssignmentSnapshot assignment = freshAssignment();

        assertThat(assignment.state()).isEqualTo(AssignmentState.ACTIVE_UNCONSUMED);
        assertThat(assignment.reassignBlockReason()).isNull();
    }

    @Test
    @DisplayName("ACTIVE_CONSUMED(개시된 활성) → reassignBlockReason 사유 문자열 (재할당 차단)")
    void reassignBlockReason_consumed_reason() {
        SettingAssignmentSnapshot assignment = freshAssignment();
        assignment.markConsumed(LocalDateTime.now());

        assertThat(assignment.state()).isEqualTo(AssignmentState.ACTIVE_CONSUMED);
        assertThat(assignment.reassignBlockReason()).isEqualTo(CONSUMED_BLOCK_REASON);
    }

    @Test
    @DisplayName("SUPERSEDED(논리 종료) → reassignBlockReason null — 활성 행만 가드 대상이라 무해")
    void reassignBlockReason_superseded_null() {
        SettingAssignmentSnapshot assignment = freshAssignment();
        assignment.supersede(LocalDateTime.now());

        assertThat(assignment.state()).isEqualTo(AssignmentState.SUPERSEDED);
        assertThat(assignment.reassignBlockReason()).isNull();
    }

    @Test
    @DisplayName("supersede 는 멱등 — 최초 종료 시각을 보존한다")
    void supersede_isIdempotent() {
        SettingAssignmentSnapshot assignment = freshAssignment();
        LocalDateTime first = LocalDateTime.now().minusHours(2);
        assignment.supersede(first);
        assignment.supersede(LocalDateTime.now());

        assertThat(assignment.getSupersededAt()).isEqualTo(first);
        assertThat(assignment.isActive()).isFalse();
    }
}
