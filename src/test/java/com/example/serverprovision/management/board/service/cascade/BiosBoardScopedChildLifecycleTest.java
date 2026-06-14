package com.example.serverprovision.management.board.service.cascade;

import com.example.serverprovision.management.bios.entity.BoardBIOS;
import com.example.serverprovision.management.bios.repository.BiosRepository;
import com.example.serverprovision.management.bios.service.BiosService;
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
 * R3-4 — {@link BiosBoardScopedChildLifecycle} 어댑터 단위 테스트.
 *
 * <p>구 {@code BoardModelLifecycleServiceTest} 의 BIOS per-child cascade 검증을 본 어댑터로 분산 흡수.
 * 5 SPI 메서드(recomputeEffective / softDeleteActive / restoreDeleted / hasAny / deletedLabels)별로 자식
 * repo·service 위임을 검증한다. {@code @Lazy} 생성자 주입이라 {@code @InjectMocks} 대신 mock 을 직접 넘겨 조립.</p>
 *
 * <p>BIOS 의 service 시그니처는 {@code softDelete(boardId, childId)} / {@code restore(boardId, childId)} 2-arg —
 * Subprogram 의 1-arg 와 비대칭. 라벨 포맷은 고정 접두 {@code "BIOS: "}.</p>
 */
@ExtendWith(MockitoExtension.class)
class BiosBoardScopedChildLifecycleTest {

    private static final Long BOARD_ID = 7L;

    @Mock BiosRepository biosRepository;
    @Mock BiosService biosService;

    BiosBoardScopedChildLifecycle adapter;

    @BeforeEach
    void initAdapter() {
        adapter = new BiosBoardScopedChildLifecycle(biosRepository, biosService);
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

    private BoardBIOS bios(Long id, BoardModel parent, boolean deleted) {
        BoardBIOS b = BoardBIOS.builder()
                .id(id).boardModel(parent).name("BIOS-" + id).version("1." + id)
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
        BoardBIOS active = bios(101L, p, false);     // effective enabled=true (부모 활성)
        BoardBIOS deleted = bios(102L, p, true);     // soft-deleted
        // 부모를 비활성으로 전이 → 활성 자식만 재계산되면 effective=false 가 되어야 한다.
        p.toggleEnabled();
        given(biosRepository.findAllByBoardModel_IdOrderByVersionDesc(BOARD_ID))
                .willReturn(List.of(active, deleted));

        adapter.recomputeEffective(BOARD_ID);

        assertThat(active.isEnabled()).isFalse();    // 부모 비활성 반영
        assertThat(active.isOwnEnabled()).isTrue();  // own 보존
        assertThat(deleted.isEnabled()).isTrue();    // soft-deleted → recompute 제외 (초기값 유지)
    }

    // ==== softDeleteActive — 활성 자식 → service.softDelete(boardId, childId) [2-arg] ====

    @Test
    @DisplayName("softDeleteActive : 활성 자식 각각 service.softDelete(boardId, biosId) 2-arg 위임")
    void softDeleteActive_delegatesTwoArg() {
        BoardModel p = parent(true, false);
        given(biosRepository.findAllByBoardModel_IdAndIsDeletedFalseOrderByVersionDesc(BOARD_ID))
                .willReturn(List.of(bios(101L, p, false), bios(102L, p, false)));

        adapter.softDeleteActive(BOARD_ID);

        verify(biosService).softDelete(BOARD_ID, 101L);
        verify(biosService).softDelete(BOARD_ID, 102L);
    }

    @Test
    @DisplayName("softDeleteActive : 활성 자식 없으면 service 미호출")
    void softDeleteActive_noActive_skips() {
        given(biosRepository.findAllByBoardModel_IdAndIsDeletedFalseOrderByVersionDesc(BOARD_ID))
                .willReturn(List.of());

        adapter.softDeleteActive(BOARD_ID);

        verify(biosService, never()).softDelete(org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.anyLong());
    }

    // ==== restoreDeleted — soft-deleted 자식 → service.restore + count ====

    @Test
    @DisplayName("restoreDeleted : soft-deleted 자식 각각 service.restore(boardId, biosId) 위임 + 복구 수 반환")
    void restoreDeleted_delegatesAndCounts() {
        BoardModel p = parent(true, false);
        given(biosRepository.findAllByBoardModel_IdAndIsDeletedTrue(BOARD_ID))
                .willReturn(List.of(bios(101L, p, true), bios(102L, p, true)));

        int restored = adapter.restoreDeleted(BOARD_ID);

        assertThat(restored).isEqualTo(2);
        verify(biosService).restore(BOARD_ID, 101L);
        verify(biosService).restore(BOARD_ID, 102L);
    }

    @Test
    @DisplayName("restoreDeleted : soft-deleted 자식 없으면 0 반환, service 미호출")
    void restoreDeleted_none_returnsZero() {
        given(biosRepository.findAllByBoardModel_IdAndIsDeletedTrue(BOARD_ID)).willReturn(List.of());

        assertThat(adapter.restoreDeleted(BOARD_ID)).isZero();
        verify(biosService, never()).restore(org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.anyLong());
    }

    // ==== hasAny — 자식 존재 여부 ====

    @Test
    @DisplayName("hasAny : 자식(삭제 포함) 존재 → true")
    void hasAny_whenChildrenExist_true() {
        BoardModel p = parent(true, false);
        given(biosRepository.findAllByBoardModel_IdOrderByVersionDesc(BOARD_ID))
                .willReturn(List.of(bios(101L, p, true)));   // 삭제 자식도 잔존으로 카운트

        assertThat(adapter.hasAny(BOARD_ID)).isTrue();
    }

    @Test
    @DisplayName("hasAny : 자식 없음 → false")
    void hasAny_whenEmpty_false() {
        given(biosRepository.findAllByBoardModel_IdOrderByVersionDesc(BOARD_ID)).willReturn(List.of());

        assertThat(adapter.hasAny(BOARD_ID)).isFalse();
    }

    // ==== deletedLabels — soft-deleted 자식 라벨("BIOS: " 접두) ====

    @Test
    @DisplayName("deletedLabels : soft-deleted 자식 이름을 'BIOS: ' 접두로 포맷")
    void deletedLabels_formatsWithPrefix() {
        BoardModel p = parent(true, false);
        given(biosRepository.findAllByBoardModel_IdAndIsDeletedTrue(BOARD_ID))
                .willReturn(List.of(bios(101L, p, true), bios(102L, p, true)));

        List<String> labels = adapter.deletedLabels(BOARD_ID);

        assertThat(labels).containsExactly("BIOS: BIOS-101", "BIOS: BIOS-102");
    }

    @Test
    @DisplayName("deletedLabels : soft-deleted 자식 없으면 빈 리스트")
    void deletedLabels_empty() {
        given(biosRepository.findAllByBoardModel_IdAndIsDeletedTrue(BOARD_ID)).willReturn(List.of());

        assertThat(adapter.deletedLabels(BOARD_ID)).isEmpty();
    }
}
