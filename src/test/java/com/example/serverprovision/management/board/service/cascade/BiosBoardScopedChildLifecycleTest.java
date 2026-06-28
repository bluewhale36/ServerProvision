package com.example.serverprovision.management.board.service.cascade;

import com.example.serverprovision.management.bios.entity.BoardBIOS;
import com.example.serverprovision.management.bios.repository.BiosRepository;
import com.example.serverprovision.management.bios.service.BiosLifecycleService;
import com.example.serverprovision.management.board.entity.BoardModel;
import com.example.serverprovision.management.board.enums.Vendor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
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
 * <p>R4-3 — BIOS lifecycle 1-arg 재성형 후 어댑터가 {@code BiosLifecycleService.softDelete(childId)} /
 * {@code restore(childId)} 를 1-arg 로 위임한다. 라벨 포맷은 고정 접두 {@code "BIOS: "}.</p>
 */
@ExtendWith(MockitoExtension.class)
class BiosBoardScopedChildLifecycleTest {

    private static final Long BOARD_ID = 7L;

    @Mock BiosRepository biosRepository;
    @Mock BiosLifecycleService biosLifecycleService;

    BiosBoardScopedChildLifecycle adapter;

    @BeforeEach
    void initAdapter() {
        adapter = new BiosBoardScopedChildLifecycle(biosRepository, biosLifecycleService);
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

    /**
     * 정상 trashed 자식. soft-deleted 면 trashedAt/trashedPath 가 채워져 있어 {@link
     * com.example.serverprovision.global.trash.GhostEvaluator#isGhost} 가 조건2(trashedAt!=null)에서 false 를
     * 반환한다(ghost 아님 → cascade restore 대상). treeRootPath 도 채워 getResourcePath() NPE 를 피한다.
     */
    private BoardBIOS bios(Long id, BoardModel parent, boolean deleted) {
        BoardBIOS b = BoardBIOS.builder()
                .id(id).boardModel(parent).name("BIOS-" + id).version("1." + id)
                .treeRootPath("/fw/bios/" + id)
                .ownEnabled(true).ownDeprecated(false).isDeleted(deleted)
                // 정상 trashed 자식은 trashedAt 이 채워져 있으므로 isGhost 조건2 에서 false (ghost 아님).
                .trashedAt(deleted ? Instant.now() : null)
                .trashedPath(deleted ? "/trash/bios/" + id : null)
                .build();
        b.recomputeEffective();
        return b;
    }

    /**
     * MK3-1 ghost 자식. isDeleted=true + trashedAt/trashedPath=null + 존재하지 않는 resourcePath →
     * isGhost==true. Fix A 회귀 검증용 — cascade restore 가 건너뛰어야 한다.
     */
    private BoardBIOS ghostBios(Long id, BoardModel parent) {
        BoardBIOS b = BoardBIOS.builder()
                .id(id).boardModel(parent).name("BIOS-" + id).version("1." + id)
                .treeRootPath("/nonexistent/ghost-bios-" + id)   // Files.notExists → ghost
                .ownEnabled(true).ownDeprecated(false).isDeleted(true)
                .build();   // trashedAt / trashedPath 미설정 = null
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

    // ==== softDeleteActive — 활성 자식 → lifecycleService.softDelete(childId) [1-arg] ====

    @Test
    @DisplayName("softDeleteActive : 활성 자식 각각 lifecycleService.softDelete(biosId) 1-arg 위임")
    void softDeleteActive_delegatesOneArg() {
        BoardModel p = parent(true, false);
        given(biosRepository.findAllByBoardModel_IdAndIsDeletedFalseOrderByVersionDesc(BOARD_ID))
                .willReturn(List.of(bios(101L, p, false), bios(102L, p, false)));

        adapter.softDeleteActive(BOARD_ID);

        verify(biosLifecycleService).softDelete(101L);
        verify(biosLifecycleService).softDelete(102L);
    }

    @Test
    @DisplayName("softDeleteActive : 활성 자식 없으면 service 미호출")
    void softDeleteActive_noActive_skips() {
        given(biosRepository.findAllByBoardModel_IdAndIsDeletedFalseOrderByVersionDesc(BOARD_ID))
                .willReturn(List.of());

        adapter.softDeleteActive(BOARD_ID);

        verify(biosLifecycleService, never()).softDelete(org.mockito.ArgumentMatchers.anyLong());
    }

    // ==== restoreDeleted — soft-deleted 자식 → service.restore + count ====

    @Test
    @DisplayName("restoreDeleted : soft-deleted 자식 각각 lifecycleService.restore(biosId) 위임 + 복구 수 반환")
    void restoreDeleted_delegatesAndCounts() {
        BoardModel p = parent(true, false);
        given(biosRepository.findAllByBoardModel_IdAndIsDeletedTrue(BOARD_ID))
                .willReturn(List.of(bios(101L, p, true), bios(102L, p, true)));

        int restored = adapter.restoreDeleted(BOARD_ID);

        assertThat(restored).isEqualTo(2);
        verify(biosLifecycleService).restore(101L);
        verify(biosLifecycleService).restore(102L);
    }

    @Test
    @DisplayName("restoreDeleted : soft-deleted 자식 없으면 0 반환, service 미호출")
    void restoreDeleted_none_returnsZero() {
        given(biosRepository.findAllByBoardModel_IdAndIsDeletedTrue(BOARD_ID)).willReturn(List.of());

        assertThat(adapter.restoreDeleted(BOARD_ID)).isZero();
        verify(biosLifecycleService, never()).restore(org.mockito.ArgumentMatchers.anyLong());
    }

    @Test
    @DisplayName("restoreDeleted(Fix A) : ghost 자식은 건너뛰고 정상 trashed 자식만 복구 (catch-22 차단)")
    void restoreDeleted_skipsGhostChild() {
        BoardModel p = parent(true, false);
        BoardBIOS normal = bios(101L, p, true);   // 정상 trashed (trashedAt non-null)
        BoardBIOS ghost = ghostBios(102L, p);     // ghost (trashedAt=null, resourcePath 부재)
        given(biosRepository.findAllByBoardModel_IdAndIsDeletedTrue(BOARD_ID))
                .willReturn(List.of(normal, ghost));

        int restored = adapter.restoreDeleted(BOARD_ID);

        assertThat(restored).isEqualTo(1);
        verify(biosLifecycleService).restore(101L);                        // 정상만 복구
        verify(biosLifecycleService, never()).restore(102L);               // ghost 미복구
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
