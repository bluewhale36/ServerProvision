package com.example.serverprovision.provisioning.assignment.service;

import com.example.serverprovision.provisioning.assignment.repository.SettingAssignmentSnapshotRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

/**
 * U3-2-b — {@link AssignmentUsageInspectorImpl} 단위. setting 소유 SPI 를 assignment 가 채우는 구현(DEC-D)이
 * 활성 할당({@code supersededAt IS NULL}) 만 카운트/판정하도록 파생 쿼리에 위임하는지 검증한다.
 */
@ExtendWith(MockitoExtension.class)
class AssignmentUsageInspectorImplTest {

    @Mock SettingAssignmentSnapshotRepository assignmentRepository;
    @InjectMocks AssignmentUsageInspectorImpl inspector;

    @Test
    @DisplayName("countReferencing — 활성 할당 수를 파생 쿼리(…AndSupersededAtIsNull) 로 위임")
    void countReferencing_delegatesToActiveOnlyQuery() {
        given(assignmentRepository.countBySourceDefinitionRef_DefinitionIdAndSupersededAtIsNull(7L))
                .willReturn(3L);

        assertThat(inspector.countReferencing(7L)).isEqualTo(3L);
    }

    @Test
    @DisplayName("countReferencing — 참조 없음 → 0")
    void countReferencing_noneReferencing_returnsZero() {
        given(assignmentRepository.countBySourceDefinitionRef_DefinitionIdAndSupersededAtIsNull(7L))
                .willReturn(0L);

        assertThat(inspector.countReferencing(7L)).isZero();
    }

    @Test
    @DisplayName("isReferenced — 존재 여부 파생 쿼리(exists…AndSupersededAtIsNull) 로 위임")
    void isReferenced_delegatesToExists() {
        given(assignmentRepository.existsBySourceDefinitionRef_DefinitionIdAndSupersededAtIsNull(7L))
                .willReturn(true);

        assertThat(inspector.isReferenced(7L)).isTrue();
    }
}
