package com.example.serverprovision.management.board.service.cascade;

import com.example.serverprovision.management.bmc.entity.BoardBMC;
import com.example.serverprovision.management.bmc.repository.BmcRepository;
import com.example.serverprovision.management.bmc.service.BmcService;
import com.example.serverprovision.management.board.entity.BoardModel;
import com.example.serverprovision.management.board.enums.Vendor;
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
 * R3-4 — {@link BmcBoardScopedChildLifecycle} 어댑터 단위 테스트.
 *
 * <p>구 {@code BoardModelLifecycleServiceTest} 의 BMC per-child cascade 검증을 본 어댑터로 분산 흡수.
 * BIOS 어댑터와 동형(IN_TREE 자식, service 2-arg) — softDelete/restore 가 {@code (boardId, childId)} 2-arg,
 * 라벨 고정 접두 {@code "BMC: "}.</p>
 */
@ExtendWith(MockitoExtension.class)
class BmcBoardScopedChildLifecycleTest {

    private static final Long BOARD_ID = 7L;

    @Mock BmcRepository bmcRepository;
    @Mock BmcService bmcService;

    BmcBoardScopedChildLifecycle adapter;

    @BeforeEach
    void initAdapter() {
        adapter = new BmcBoardScopedChildLifecycle(bmcRepository, bmcService);
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

    private BoardBMC bmc(Long id, BoardModel parent, boolean deleted) {
        BoardBMC b = BoardBMC.builder()
                .id(id).boardModel(parent).name("BMC-" + id).version("2." + id)
                .treeRootPath("/fw/bmc/" + id).legacyFilePath("/fw/bmc/" + id).boardModelIdMirror(BOARD_ID)
                .entrypointRelativePath("flash.nsh")
                .manifestHash("hash").markerSignature("sig").fileCount(2).totalBytes(128L)
                .ownEnabled(true).ownDeprecated(false).isDeleted(deleted)
                .build();
        b.recomputeEffective();
        return b;
    }

    // ==== recomputeEffective — 활성 자식만 재계산, soft-deleted 제외 ====

    @Test
    @DisplayName("recomputeEffective : 활성 자식만 effective 재계산(부모 비활성 반영), soft-deleted 자식은 제외")
    void recomputeEffective_onlyActiveChildren() {
        BoardModel p = parent(true, false);
        BoardBMC active = bmc(201L, p, false);
        BoardBMC deleted = bmc(202L, p, true);
        p.toggleEnabled();   // 부모 비활성 전이
        given(bmcRepository.findAllByBoardModel_IdOrderByVersionDesc(BOARD_ID))
                .willReturn(List.of(active, deleted));

        adapter.recomputeEffective(BOARD_ID);

        assertThat(active.isEnabled()).isFalse();
        assertThat(active.isOwnEnabled()).isTrue();
        assertThat(deleted.isEnabled()).isTrue();   // soft-deleted → 제외
    }

    // ==== softDeleteActive — service.softDelete(boardId, childId) [2-arg] ====

    @Test
    @DisplayName("softDeleteActive : 활성 자식 각각 service.softDelete(boardId, bmcId) 2-arg 위임")
    void softDeleteActive_delegatesTwoArg() {
        BoardModel p = parent(true, false);
        given(bmcRepository.findAllByBoardModel_IdAndIsDeletedFalseOrderByVersionDesc(BOARD_ID))
                .willReturn(List.of(bmc(201L, p, false), bmc(202L, p, false)));

        adapter.softDeleteActive(BOARD_ID);

        verify(bmcService).softDelete(BOARD_ID, 201L);
        verify(bmcService).softDelete(BOARD_ID, 202L);
    }

    @Test
    @DisplayName("softDeleteActive : 활성 자식 없으면 service 미호출")
    void softDeleteActive_noActive_skips() {
        given(bmcRepository.findAllByBoardModel_IdAndIsDeletedFalseOrderByVersionDesc(BOARD_ID))
                .willReturn(List.of());

        adapter.softDeleteActive(BOARD_ID);

        verify(bmcService, never()).softDelete(org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.anyLong());
    }

    // ==== restoreDeleted — soft-deleted 자식 → service.restore + count ====

    @Test
    @DisplayName("restoreDeleted : soft-deleted 자식 각각 service.restore(boardId, bmcId) 위임 + 복구 수 반환")
    void restoreDeleted_delegatesAndCounts() {
        BoardModel p = parent(true, false);
        given(bmcRepository.findAllByBoardModel_IdAndIsDeletedTrue(BOARD_ID))
                .willReturn(List.of(bmc(201L, p, true), bmc(202L, p, true)));

        int restored = adapter.restoreDeleted(BOARD_ID);

        assertThat(restored).isEqualTo(2);
        verify(bmcService).restore(BOARD_ID, 201L);
        verify(bmcService).restore(BOARD_ID, 202L);
    }

    @Test
    @DisplayName("restoreDeleted : soft-deleted 자식 없으면 0 반환, service 미호출")
    void restoreDeleted_none_returnsZero() {
        given(bmcRepository.findAllByBoardModel_IdAndIsDeletedTrue(BOARD_ID)).willReturn(List.of());

        assertThat(adapter.restoreDeleted(BOARD_ID)).isZero();
        verify(bmcService, never()).restore(org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.anyLong());
    }

    // ==== hasAny ====

    @Test
    @DisplayName("hasAny : 자식(삭제 포함) 존재 → true")
    void hasAny_whenChildrenExist_true() {
        BoardModel p = parent(true, false);
        given(bmcRepository.findAllByBoardModel_IdOrderByVersionDesc(BOARD_ID))
                .willReturn(List.of(bmc(201L, p, true)));

        assertThat(adapter.hasAny(BOARD_ID)).isTrue();
    }

    @Test
    @DisplayName("hasAny : 자식 없음 → false")
    void hasAny_whenEmpty_false() {
        given(bmcRepository.findAllByBoardModel_IdOrderByVersionDesc(BOARD_ID)).willReturn(List.of());

        assertThat(adapter.hasAny(BOARD_ID)).isFalse();
    }

    // ==== deletedLabels — "BMC: " 접두 ====

    @Test
    @DisplayName("deletedLabels : soft-deleted 자식 이름을 'BMC: ' 접두로 포맷")
    void deletedLabels_formatsWithPrefix() {
        BoardModel p = parent(true, false);
        given(bmcRepository.findAllByBoardModel_IdAndIsDeletedTrue(BOARD_ID))
                .willReturn(List.of(bmc(201L, p, true), bmc(202L, p, true)));

        List<String> labels = adapter.deletedLabels(BOARD_ID);

        assertThat(labels).containsExactly("BMC: BMC-201", "BMC: BMC-202");
    }

    @Test
    @DisplayName("deletedLabels : soft-deleted 자식 없으면 빈 리스트")
    void deletedLabels_empty() {
        given(bmcRepository.findAllByBoardModel_IdAndIsDeletedTrue(BOARD_ID)).willReturn(List.of());

        assertThat(adapter.deletedLabels(BOARD_ID)).isEmpty();
    }
}
