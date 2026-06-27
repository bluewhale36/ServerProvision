package com.example.serverprovision.management.board.service.metadata;

import com.example.serverprovision.global.exception.TypedNameMismatchException;
import com.example.serverprovision.management.board.entity.BoardModel;
import com.example.serverprovision.management.board.enums.Vendor;
import com.example.serverprovision.management.board.exception.BoardModelNotFoundException;
import com.example.serverprovision.management.board.exception.DuplicateBoardModelException;
import com.example.serverprovision.management.board.exception.IllegalBoardModelStateException;
import com.example.serverprovision.management.board.repository.BoardModelRepository;
import com.example.serverprovision.management.board.service.BoardScopedChildLifecycle;
import com.example.serverprovision.management.common.dto.response.RestoreResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * R3-4 — {@link BoardModelLifecycleService} 단위 테스트 (오케스트레이션 검증).
 *
 * <p>R3-4 에서 자식 BIOS/BMC/Subprogram cascade 가 {@link BoardScopedChildLifecycle} 다형 순회로 통일되어,
 * 본 서비스는 자식 repo/service 를 직접 주입하지 않고 {@code List<BoardScopedChildLifecycle>} 만 순회한다.
 * 따라서 본 테스트는 더 이상 자식 도메인 entity 의 effective 전이를 직접 검증하지 않고, <b>board 레벨 로직</b>
 * (404 가드 / Duplicate / TypedName / soft-deleted 가드)과 <b>어댑터 위임</b>(softDeleteActive / restoreDeleted
 * 합산 / hasAny anyMatch / recomputeEffective / deletedLabels flatMap)을 검증한다.</p>
 *
 * <p>자식별 시그니처 비대칭(2-arg vs 1-arg)·라벨 포맷·effective 전이의 per-child 검증은 어댑터 단위 테스트
 * ({@code cascade/{Bios,Bmc,Subprogram}BoardScopedChildLifecycleTest})로 이관했다.</p>
 */
@ExtendWith(MockitoExtension.class)
class BoardModelLifecycleServiceTest {

    @Mock BoardModelRepository boardModelRepository;

    // R3-4 — 자식 cascade 어댑터 3종 (BIOS=10 / BMC=20 / Subprogram=30 순서를 mock 리스트 순서로 재현).
    @Mock BoardScopedChildLifecycle biosChild;
    @Mock BoardScopedChildLifecycle bmcChild;
    @Mock BoardScopedChildLifecycle subprogramChild;

    BoardModelLifecycleService boardModelService;

    @BeforeEach
    void initService() {
        // @Order 순회 순서(BIOS → BMC → Subprogram)를 리스트 순서로 고정해 주입.
        // R7-3 — service→verifier 변 절단: typed-name 검증이 static TypedNameGuard 로 이동해
        // verifier 빈 주입이 사라졌다(생성자 2-arg).
        boardModelService = new BoardModelLifecycleService(
                boardModelRepository, List.of(biosChild, bmcChild, subprogramChild));
    }

    // ==== helper =====================================================

    private BoardModel activeParent() {
        BoardModel p = BoardModel.builder()
                .id(7L).vendor(Vendor.ASUS).modelName("P13R-E")
                .ownEnabled(true).ownDeprecated(false).isDeleted(false)
                .build();
        p.recomputeEffective();
        return p;
    }

    private BoardModel deletedParent() {
        return BoardModel.builder().id(7L).vendor(Vendor.ASUS).modelName("P13R-E")
                .ownEnabled(true).ownDeprecated(false).isDeleted(true).build();
    }

    // ==== toggleEnabled / deprecate / undeprecate — 부모 own flip + cascadeRecompute 위임 ====

    @Test
    @DisplayName("toggleEnabled : 활성 보드 own flip(true→false) + 전 자식 어댑터 recomputeEffective 위임")
    void toggleEnabled_flipsOwnAndDelegatesRecompute() {
        BoardModel p = activeParent();
        given(boardModelRepository.findByIdAndIsDeletedFalse(7L)).willReturn(Optional.of(p));

        boardModelService.toggleEnabled(7L);

        assertThat(p.isEnabled()).isFalse();          // 부모 own flip
        verify(biosChild).recomputeEffective(7L);
        verify(bmcChild).recomputeEffective(7L);
        verify(subprogramChild).recomputeEffective(7L);
    }

    @Test
    @DisplayName("toggleEnabled : 활성 보드 없음 → BoardModelNotFoundException, 자식 어댑터 미호출")
    void toggleEnabled_whenBoardMissing_throws404() {
        given(boardModelRepository.findByIdAndIsDeletedFalse(7L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> boardModelService.toggleEnabled(7L))
                .isInstanceOf(BoardModelNotFoundException.class);
        verify(biosChild, never()).recomputeEffective(org.mockito.ArgumentMatchers.anyLong());
    }

    @Test
    @DisplayName("deprecate : 활성 보드 deprecate + 전 자식 어댑터 recomputeEffective 위임")
    void deprecate_marksParentAndDelegatesRecompute() {
        BoardModel p = activeParent();
        given(boardModelRepository.findByIdAndIsDeletedFalse(7L)).willReturn(Optional.of(p));

        boardModelService.deprecate(7L);

        assertThat(p.isDeprecated()).isTrue();
        assertThat(p.isEnabled()).isTrue();           // deprecate 는 enabled 차원 무관
        verify(biosChild).recomputeEffective(7L);
        verify(bmcChild).recomputeEffective(7L);
        verify(subprogramChild).recomputeEffective(7L);
    }

    @Test
    @DisplayName("undeprecate : 활성 보드 undeprecate + 전 자식 어댑터 recomputeEffective 위임")
    void undeprecate_clearsParentAndDelegatesRecompute() {
        BoardModel p = activeParent();
        p.deprecate();                                  // own_deprecated=true 선행
        given(boardModelRepository.findByIdAndIsDeletedFalse(7L)).willReturn(Optional.of(p));

        boardModelService.undeprecate(7L);

        assertThat(p.isDeprecated()).isFalse();
        verify(biosChild).recomputeEffective(7L);
        verify(bmcChild).recomputeEffective(7L);
        verify(subprogramChild).recomputeEffective(7L);
    }

    @Test
    @DisplayName("deprecate : 활성 보드 없음 → BoardModelNotFoundException, 자식 어댑터 미호출")
    void deprecate_whenBoardMissing_throws404() {
        given(boardModelRepository.findByIdAndIsDeletedFalse(7L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> boardModelService.deprecate(7L))
                .isInstanceOf(BoardModelNotFoundException.class);
        verify(biosChild, never()).recomputeEffective(org.mockito.ArgumentMatchers.anyLong());
    }

    // ==== softDelete — 부모 soft-delete + 자식 softDeleteActive 위임 ====

    @Test
    @DisplayName("softDelete : 활성 보드 → 자식 어댑터 softDeleteActive 위임 後 board.softDelete + markTrashed")
    void softDelete_delegatesToChildrenThenSoftDeletesBoard() {
        BoardModel board = activeParent();
        given(boardModelRepository.findByIdAndIsDeletedFalse(7L)).willReturn(Optional.of(board));

        boardModelService.softDelete(7L);

        assertThat(board.isDeleted()).isTrue();        // board.softDelete 호출됨
        verify(biosChild).softDeleteActive(7L);
        verify(bmcChild).softDeleteActive(7L);
        verify(subprogramChild).softDeleteActive(7L);
    }

    @Test
    @DisplayName("softDelete : 활성 보드 없음 → BoardModelNotFoundException, 자식 어댑터 미호출")
    void softDelete_whenBoardMissing_throws404() {
        given(boardModelRepository.findByIdAndIsDeletedFalse(7L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> boardModelService.softDelete(7L))
                .isInstanceOf(BoardModelNotFoundException.class);
        verify(biosChild, never()).softDeleteActive(org.mockito.ArgumentMatchers.anyLong());
    }

    // ==== restore — cascade 여부 + restoreDeleted 합산 ====

    @Test
    @DisplayName("restore(cascade=false) : 부모만 복구 → RestoreResponse.none, 자식 어댑터 restoreDeleted 미호출")
    void restore_noCascade_returnsNoneAndSkipsChildren() {
        BoardModel p = deletedParent();
        given(boardModelRepository.findByIdAndIsDeletedTrue(7L)).willReturn(Optional.of(p));
        given(boardModelRepository.existsByVendorAndModelNameAndIsDeletedFalse(Vendor.ASUS, "P13R-E"))
                .willReturn(false);

        RestoreResponse response = boardModelService.restore(7L, false);

        assertThat(response.cascadedChildren()).isZero();
        assertThat(p.isDeleted()).isFalse();
        verify(biosChild, never()).restoreDeleted(org.mockito.ArgumentMatchers.anyLong());
        verify(bmcChild, never()).restoreDeleted(org.mockito.ArgumentMatchers.anyLong());
        verify(subprogramChild, never()).restoreDeleted(org.mockito.ArgumentMatchers.anyLong());
    }

    @Test
    @DisplayName("restore(단일인자 오버로드) : cascade=false 와 동일 동작 → none, 자식 미호출")
    void restore_singleArgOverload_behavesAsNoCascade() {
        BoardModel p = deletedParent();
        given(boardModelRepository.findByIdAndIsDeletedTrue(7L)).willReturn(Optional.of(p));
        given(boardModelRepository.existsByVendorAndModelNameAndIsDeletedFalse(Vendor.ASUS, "P13R-E"))
                .willReturn(false);

        boardModelService.restore(7L);

        assertThat(p.isDeleted()).isFalse();
        verify(biosChild, never()).restoreDeleted(org.mockito.ArgumentMatchers.anyLong());
    }

    @Test
    @DisplayName("restore(cascade=true) : board.restore 後 자식 어댑터 restoreDeleted 합산(2+1+0=3) 반환")
    void restore_cascadeTrue_sumsChildRestores() {
        BoardModel p = deletedParent();
        given(boardModelRepository.findByIdAndIsDeletedTrue(7L)).willReturn(Optional.of(p));
        given(boardModelRepository.existsByVendorAndModelNameAndIsDeletedFalse(Vendor.ASUS, "P13R-E"))
                .willReturn(false);
        given(biosChild.restoreDeleted(7L)).willReturn(2);
        given(bmcChild.restoreDeleted(7L)).willReturn(1);
        given(subprogramChild.restoreDeleted(7L)).willReturn(0);

        RestoreResponse response = boardModelService.restore(7L, true);

        assertThat(p.isDeleted()).isFalse();
        assertThat(response.cascadedChildren()).isEqualTo(3);  // 2 + 1 + 0 합산
        verify(biosChild).restoreDeleted(7L);
        verify(bmcChild).restoreDeleted(7L);
        verify(subprogramChild).restoreDeleted(7L);
    }

    @Test
    @DisplayName("restore : 복구하려는 (vendor, modelName) 이 이미 활성 → DuplicateBoardModelException, 자식 미호출")
    void restore_whenActiveKeyExists_throwsDuplicate() {
        BoardModel p = deletedParent();
        given(boardModelRepository.findByIdAndIsDeletedTrue(7L)).willReturn(Optional.of(p));
        given(boardModelRepository.existsByVendorAndModelNameAndIsDeletedFalse(Vendor.ASUS, "P13R-E"))
                .willReturn(true);

        assertThatThrownBy(() -> boardModelService.restore(7L, true))
                .isInstanceOf(DuplicateBoardModelException.class);
        verify(biosChild, never()).restoreDeleted(org.mockito.ArgumentMatchers.anyLong());
    }

    @Test
    @DisplayName("restore : soft-deleted 가 아니면 IllegalBoardModelStateException")
    void restore_notSoftDeleted_throws() {
        given(boardModelRepository.findByIdAndIsDeletedTrue(7L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> boardModelService.restore(7L, true))
                .isInstanceOf(IllegalBoardModelStateException.class);
    }

    @Test
    @DisplayName("restore(cascade=true) : 자식 어댑터 restoreDeleted 가 충돌 예외 던지면 전파(전체 롤백)")
    void restore_cascade_childConflict_propagates() {
        BoardModel p = deletedParent();
        given(boardModelRepository.findByIdAndIsDeletedTrue(7L)).willReturn(Optional.of(p));
        given(boardModelRepository.existsByVendorAndModelNameAndIsDeletedFalse(Vendor.ASUS, "P13R-E"))
                .willReturn(false);
        given(biosChild.restoreDeleted(7L))
                .willThrow(new DuplicateBoardModelException(Vendor.ASUS, "P13R-E"));

        assertThatThrownBy(() -> boardModelService.restore(7L, true))
                .isInstanceOf(DuplicateBoardModelException.class);
    }

    // ==== purge — 자식 잔존 검사(hasAny anyMatch) ====

    @Test
    @DisplayName("purge : 전 자식 어댑터 hasAny=false → 영구 삭제 허용")
    void purge_okWhenNoChildren() {
        BoardModel p = deletedParent();
        given(boardModelRepository.findByIdAndIsDeletedTrue(7L)).willReturn(Optional.of(p));
        given(biosChild.hasAny(7L)).willReturn(false);
        given(bmcChild.hasAny(7L)).willReturn(false);
        given(subprogramChild.hasAny(7L)).willReturn(false);

        boardModelService.purge(7L);

        verify(boardModelRepository).delete(p);
    }

    @Test
    @DisplayName("purge : 자식 어댑터 중 하나라도 hasAny=true(anyMatch) → IllegalBoardModelStateException, delete 미호출")
    void purge_blockedWhenAnyChildRemains() {
        BoardModel p = deletedParent();
        given(boardModelRepository.findByIdAndIsDeletedTrue(7L)).willReturn(Optional.of(p));
        // anyMatch — BIOS=false 지만 BMC=true 면 거절. subprogram 은 short-circuit 으로 미평가 가능.
        given(biosChild.hasAny(7L)).willReturn(false);
        given(bmcChild.hasAny(7L)).willReturn(true);

        assertThatThrownBy(() -> boardModelService.purge(7L))
                .isInstanceOf(IllegalBoardModelStateException.class);
        verify(boardModelRepository, never()).delete(p);
    }

    @Test
    @DisplayName("purge : soft-deleted 가 아니면 IllegalBoardModelStateException")
    void purge_notSoftDeleted_throws() {
        given(boardModelRepository.findByIdAndIsDeletedTrue(7L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> boardModelService.purge(7L))
                .isInstanceOf(IllegalBoardModelStateException.class);
    }

    // ==== purgeWithTypedNameCheck — typed-name 검증 + 자식 잔존 ====

    @Test
    @DisplayName("purgeWithTypedNameCheck(happy) : typedName 일치(static guard 통과) + 자식 없음 → 영구 삭제")
    void purgeWithTypedNameCheck_match_deletes() {
        BoardModel p = deletedParent();   // displayName = "Asus P13R-E"
        given(boardModelRepository.findByIdAndIsDeletedTrue(7L)).willReturn(Optional.of(p));
        given(biosChild.hasAny(7L)).willReturn(false);
        given(bmcChild.hasAny(7L)).willReturn(false);
        given(subprogramChild.hasAny(7L)).willReturn(false);

        // R7-3 — 이름 일치 검증은 이미 로딩한 엔티티로 static TypedNameGuard.verify(board, typedName) 수행.
        boardModelService.purgeWithTypedNameCheck(7L, "Asus P13R-E");

        verify(boardModelRepository).delete(p);
    }

    @Test
    @DisplayName("purgeWithTypedNameCheck(mismatch) : static guard 가 TypedNameMismatchException → 전파, 자식 검사·delete 전 거절")
    void purgeWithTypedNameCheck_mismatch_throws() {
        BoardModel p = deletedParent();   // displayName = "Asus P13R-E"
        given(boardModelRepository.findByIdAndIsDeletedTrue(7L)).willReturn(Optional.of(p));

        // R7-3 — typedName 이 displayName 과 다르면 static guard 가 직접 예외를 던지고 서비스를 통과한다.
        assertThatThrownBy(() -> boardModelService.purgeWithTypedNameCheck(7L, "wrong"))
                .isInstanceOf(TypedNameMismatchException.class);
        verify(boardModelRepository, never()).delete(p);
    }

    @Test
    @DisplayName("purgeWithTypedNameCheck : soft-deleted 가 아니면 IllegalBoardModelStateException")
    void purgeWithTypedNameCheck_notSoftDeleted_throws() {
        given(boardModelRepository.findByIdAndIsDeletedTrue(7L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> boardModelService.purgeWithTypedNameCheck(7L, "Asus P13R-E"))
                .isInstanceOf(IllegalBoardModelStateException.class);
    }

    // ==== findDeletedChildLabels — flatMap 순서 보존 수집 ====

    @Test
    @DisplayName("findDeletedChildLabels : 자식 어댑터 deletedLabels 를 @Order 순서(BIOS→BMC→Subprogram)대로 flatMap 합본")
    void findDeletedChildLabels_flatMapsInOrder() {
        given(biosChild.deletedLabels(7L)).willReturn(List.of("BIOS: bios-a"));
        given(bmcChild.deletedLabels(7L)).willReturn(List.of("BMC: bmc-b"));
        given(subprogramChild.deletedLabels(7L)).willReturn(List.of("드라이버: drv-c"));

        List<String> labels = boardModelService.findDeletedChildLabels(7L);

        assertThat(labels).containsExactly("BIOS: bios-a", "BMC: bmc-b", "드라이버: drv-c");
    }

    @Test
    @DisplayName("findDeletedChildLabels : soft-deleted 자식 없으면 빈 리스트")
    void findDeletedChildLabels_emptyWhenNoneDeleted() {
        given(biosChild.deletedLabels(7L)).willReturn(List.of());
        given(bmcChild.deletedLabels(7L)).willReturn(List.of());
        given(subprogramChild.deletedLabels(7L)).willReturn(List.of());

        assertThat(boardModelService.findDeletedChildLabels(7L)).isEmpty();
    }
}
