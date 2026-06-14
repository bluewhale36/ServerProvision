package com.example.serverprovision.management.board.service.cascade;

import com.example.serverprovision.management.board.entity.BoardModel;
import com.example.serverprovision.management.board.enums.Vendor;
import com.example.serverprovision.management.subprogram.entity.Subprogram;
import com.example.serverprovision.management.subprogram.enums.SubprogramKind;
import com.example.serverprovision.management.subprogram.repository.SubprogramRepository;
import com.example.serverprovision.management.subprogram.service.SubprogramService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * R3-4 — {@link SubprogramBoardScopedChildLifecycle} 어댑터 단위 테스트.
 *
 * <p>구 {@code BoardModelLifecycleServiceTest} 의 Subprogram per-child cascade 검증을 본 어댑터로 분산 흡수.
 * BIOS/BMC 와의 비대칭을 검증한다 :
 * <ul>
 *   <li>service 시그니처 — {@code softDelete(spId)} / {@code restore(spId)} <b>1-arg</b> (board-scoped 가 아닌 단일 인자)</li>
 *   <li>라벨 포맷 — {@code kind.getDisplayName() + ": " + name} (고정 접두 아님 — DRIVER → "드라이버: ")</li>
 *   <li>repo 메서드 — {@code findAllByBoardModel_Id} 계열(Order/version 무관)</li>
 * </ul>
 * 공용 Subprogram(boardModel=null)은 board.id 매칭에서 자연 제외되므로 cascade 대상이 아니다(repo 계약이 보장).</p>
 */
@ExtendWith(MockitoExtension.class)
class SubprogramBoardScopedChildLifecycleTest {

    private static final Long BOARD_ID = 7L;

    @Mock SubprogramRepository subprogramRepository;
    @Mock SubprogramService subprogramService;

    SubprogramBoardScopedChildLifecycle adapter;

    @BeforeEach
    void initAdapter() {
        adapter = new SubprogramBoardScopedChildLifecycle(subprogramRepository, subprogramService);
    }

    // ==== helper =====================================================

    private BoardModel parent(boolean enabled, boolean deprecated) {
        BoardModel p = BoardModel.builder()
                .id(BOARD_ID).vendor(Vendor.ASUS).modelName("P13R-E")
                .ownEnabled(enabled).ownDeprecated(deprecated).isDeleted(false)
                .build();
        p.recomputeEffective();
        return p;
    }

    private Subprogram subprogram(Long id, BoardModel parent, SubprogramKind kind, boolean deleted) {
        Subprogram s = Subprogram.builder()
                .id(id).kind(kind).boardModel(parent)
                .name("sp-" + id).version("1." + id).treeRootPath("/sp/" + id)
                .manifestHash("h").fileCount(1).totalBytes(1L)
                .ownEnabled(true).ownDeprecated(false).isDeleted(deleted)
                .build();
        s.recomputeEffective();
        return s;
    }

    // ==== recomputeEffective — 활성 자식만 재계산, soft-deleted 제외 ====

    @Test
    @DisplayName("recomputeEffective : 활성 자식만 effective 재계산(부모 비활성 반영), soft-deleted 자식은 제외")
    void recomputeEffective_onlyActiveChildren() {
        BoardModel p = parent(true, false);
        Subprogram active = subprogram(301L, p, SubprogramKind.DRIVER, false);
        Subprogram deleted = subprogram(302L, p, SubprogramKind.DRIVER, true);
        p.toggleEnabled();   // 부모 비활성 전이
        given(subprogramRepository.findAllByBoardModel_Id(BOARD_ID))
                .willReturn(List.of(active, deleted));

        adapter.recomputeEffective(BOARD_ID);

        assertThat(active.isEnabled()).isFalse();
        assertThat(active.isOwnEnabled()).isTrue();
        assertThat(deleted.isEnabled()).isTrue();   // soft-deleted → 제외
    }

    // ==== softDeleteActive — service.softDelete(spId) [1-arg] ====

    @Test
    @DisplayName("softDeleteActive : 활성 자식 각각 service.softDelete(spId) 1-arg 위임 (BIOS/BMC 2-arg 와 비대칭)")
    void softDeleteActive_delegatesSingleArg() {
        BoardModel p = parent(true, false);
        given(subprogramRepository.findAllByBoardModel_IdAndIsDeletedFalse(BOARD_ID))
                .willReturn(List.of(
                        subprogram(301L, p, SubprogramKind.DRIVER, false),
                        subprogram(302L, p, SubprogramKind.UTILITY, false)));

        adapter.softDeleteActive(BOARD_ID);

        verify(subprogramService).softDelete(301L);   // 단일 인자
        verify(subprogramService).softDelete(302L);
    }

    @Test
    @DisplayName("softDeleteActive : 활성 자식 없으면 service 미호출")
    void softDeleteActive_noActive_skips() {
        given(subprogramRepository.findAllByBoardModel_IdAndIsDeletedFalse(BOARD_ID))
                .willReturn(List.of());

        adapter.softDeleteActive(BOARD_ID);

        verify(subprogramService, never()).softDelete(org.mockito.ArgumentMatchers.anyLong());
    }

    // ==== restoreDeleted — service.restore(spId) [1-arg] + count ====

    @Test
    @DisplayName("restoreDeleted : soft-deleted 자식 각각 service.restore(spId) 1-arg 위임 + 복구 수 반환")
    void restoreDeleted_delegatesSingleArgAndCounts() {
        BoardModel p = parent(true, false);
        given(subprogramRepository.findAllByBoardModel_IdAndIsDeletedTrue(BOARD_ID))
                .willReturn(List.of(
                        subprogram(301L, p, SubprogramKind.DRIVER, true),
                        subprogram(302L, p, SubprogramKind.UTILITY, true)));

        int restored = adapter.restoreDeleted(BOARD_ID);

        assertThat(restored).isEqualTo(2);
        verify(subprogramService).restore(301L);
        verify(subprogramService).restore(302L);
    }

    @Test
    @DisplayName("restoreDeleted : soft-deleted 자식 없으면 0 반환, service 미호출")
    void restoreDeleted_none_returnsZero() {
        given(subprogramRepository.findAllByBoardModel_IdAndIsDeletedTrue(BOARD_ID)).willReturn(List.of());

        assertThat(adapter.restoreDeleted(BOARD_ID)).isZero();
        verify(subprogramService, never()).restore(org.mockito.ArgumentMatchers.anyLong());
    }

    // ==== hasAny ====

    @Test
    @DisplayName("hasAny : 자식(삭제 포함) 존재 → true")
    void hasAny_whenChildrenExist_true() {
        BoardModel p = parent(true, false);
        given(subprogramRepository.findAllByBoardModel_Id(BOARD_ID))
                .willReturn(List.of(subprogram(301L, p, SubprogramKind.DRIVER, true)));

        assertThat(adapter.hasAny(BOARD_ID)).isTrue();
    }

    @Test
    @DisplayName("hasAny : 자식 없음 → false")
    void hasAny_whenEmpty_false() {
        given(subprogramRepository.findAllByBoardModel_Id(BOARD_ID)).willReturn(List.of());

        assertThat(adapter.hasAny(BOARD_ID)).isFalse();
    }

    // ==== deletedLabels — kind.displayName 접두 (BIOS/BMC 고정 접두와 비대칭) ====

    @Test
    @DisplayName("deletedLabels : soft-deleted 자식 이름을 kind.displayName 접두로 포맷 (DRIVER→'드라이버: ', UTILITY→'유틸리티: ')")
    void deletedLabels_formatsWithKindDisplayName() {
        BoardModel p = parent(true, false);
        given(subprogramRepository.findAllByBoardModel_IdAndIsDeletedTrue(BOARD_ID))
                .willReturn(List.of(
                        subprogram(301L, p, SubprogramKind.DRIVER, true),
                        subprogram(302L, p, SubprogramKind.UTILITY, true)));

        List<String> labels = adapter.deletedLabels(BOARD_ID);

        assertThat(labels).containsExactly(
                SubprogramKind.DRIVER.getDisplayName() + ": sp-301",
                SubprogramKind.UTILITY.getDisplayName() + ": sp-302");
    }

    @Test
    @DisplayName("deletedLabels : soft-deleted 자식 없으면 빈 리스트")
    void deletedLabels_empty() {
        given(subprogramRepository.findAllByBoardModel_IdAndIsDeletedTrue(BOARD_ID)).willReturn(List.of());

        assertThat(adapter.deletedLabels(BOARD_ID)).isEmpty();
    }
}
