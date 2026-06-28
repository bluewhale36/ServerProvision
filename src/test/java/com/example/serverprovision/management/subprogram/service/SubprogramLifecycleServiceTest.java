package com.example.serverprovision.management.subprogram.service;

import com.example.serverprovision.global.exception.ChildLifecycleBlockedByParentException;
import com.example.serverprovision.global.exception.TypedNameMismatchException;
import com.example.serverprovision.global.lifecycle.SoftDeleteIntentService;
import com.example.serverprovision.global.trash.TrashLifecycleService;
import com.example.serverprovision.management.board.entity.BoardModel;
import com.example.serverprovision.management.board.enums.Vendor;
import com.example.serverprovision.management.common.filesystem.service.BundleTreeCleanupService;
import com.example.serverprovision.management.subprogram.entity.Subprogram;
import com.example.serverprovision.management.subprogram.enums.SubprogramKind;
import com.example.serverprovision.management.subprogram.exception.DuplicateSubprogramVersionException;
import com.example.serverprovision.management.subprogram.exception.IllegalSubprogramStateException;
import com.example.serverprovision.management.subprogram.exception.SubprogramNotFoundException;
import com.example.serverprovision.management.subprogram.repository.SubprogramRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * R6-3 CP4 — {@link SubprogramLifecycleService} 단위 테스트.
 *
 * <p>fat {@code SubprogramService} 5분할 시 lifecycle 시나리오 (toggle / restore / deprecate / undeprecate /
 * purge / softDelete) 를 본 file 로 이동. Subprogram 은 분리 전부터 1-arg {@code (subprogramId)} 라
 * BIOS / BMC 의 {@code assertBelongsToBoard} 같은 board-scope forging 가드가 존재하지 않는다 (lifecycle URL 에
 * boardId path variable 부재). 부모 가드는 {@code sp.getBoardModel() != null} 일 때만 발동 (공용 자원 = null).</p>
 *
 * <p>typed-name purge 는 의존성 0 인 static {@code TypedNameGuard} 를 직접 호출하므로 verifier mock 이 없다.
 * 일치/불일치는 Subprogram 의 실제 displayName()(= name) 으로 결정한다.</p>
 */
@ExtendWith(MockitoExtension.class)
class SubprogramLifecycleServiceTest {

    @Mock SubprogramRepository subprogramRepository;
    @Mock TrashLifecycleService trashLifecycleService;
    @Mock SoftDeleteIntentService softDeleteIntentService;
    @Mock BundleTreeCleanupService bundleTreeCleanupService;
    @InjectMocks SubprogramLifecycleService subprogramLifecycleService;

    // ==== fixtures =====================================================

    private BoardModel board(boolean enabled, boolean deprecated, boolean deleted) {
        BoardModel p = BoardModel.builder()
                .id(10L).vendor(Vendor.GIGABYTE).modelName("MS03-CE0")
                .ownEnabled(enabled).ownDeprecated(deprecated).isDeleted(deleted).build();
        p.recomputeEffective();   // R4-1 — effective(isEnabled/isDeprecated) 를 own 으로부터 초기화. childEnableBlockReason 이 effective 를 읽으므로 필수.
        return p;
    }

    private Subprogram sp(Long id, BoardModel parent, boolean ownEnabled, boolean ownDeprecated, boolean deleted) {
        Subprogram s = Subprogram.builder()
                .id(id).kind(SubprogramKind.DRIVER).boardModel(parent)
                .name("a").version("1.0").treeRootPath("/p").manifestHash("h")
                .fileCount(1).totalBytes(1L)
                .ownEnabled(ownEnabled).ownDeprecated(ownDeprecated).isDeleted(deleted).build();
        s.recomputeEffective();   // R4-1 — effective = own ⊕ 부모
        return s;
    }

    // ==== toggleEnabled — 부모 가드 ====================================

    @Test
    @DisplayName("toggleEnabled : 부모 DISABLED → 자식 활성화 거절 (ChildLifecycleBlockedByParent)")
    void toggle_parentDisabled_blocks() {
        given(subprogramRepository.findById(5L)).willReturn(Optional.of(sp(5L, board(false, false, false), false, false, false)));
        assertThatThrownBy(() -> subprogramLifecycleService.toggleEnabled(5L))
                .isInstanceOf(ChildLifecycleBlockedByParentException.class);
    }

    @Test
    @DisplayName("toggleEnabled : 부모 DELETED → 자식 활성화 거절 (comprehensive)")
    void toggle_parentDeleted_blocks() {
        given(subprogramRepository.findById(5L)).willReturn(Optional.of(sp(5L, board(true, false, true), false, false, false)));
        assertThatThrownBy(() -> subprogramLifecycleService.toggleEnabled(5L))
                .isInstanceOf(ChildLifecycleBlockedByParentException.class);
    }

    @Test
    @DisplayName("toggleEnabled : 공용(boardModel=null) → 부모 가드 미적용, 정상 활성화")
    void toggle_commonScope_passes() {
        Subprogram s = sp(8L, null, false, false, false);
        given(subprogramRepository.findById(8L)).willReturn(Optional.of(s));
        subprogramLifecycleService.toggleEnabled(8L);
        assertThat(s.isEnabled()).isTrue();
    }

    @Test
    @DisplayName("toggleEnabled : 부모 ACTIVE → 정상 활성화")
    void toggle_parentActive_passes() {
        Subprogram s = sp(9L, board(true, false, false), false, false, false);
        given(subprogramRepository.findById(9L)).willReturn(Optional.of(s));
        subprogramLifecycleService.toggleEnabled(9L);
        assertThat(s.isEnabled()).isTrue();
    }

    @Test
    @DisplayName("toggleEnabled : soft-deleted 자원 → IllegalSubprogramStateException (requireLive)")
    void toggle_softDeleted_throws() {
        given(subprogramRepository.findById(5L)).willReturn(Optional.of(sp(5L, board(true, false, false), false, false, true)));
        assertThatThrownBy(() -> subprogramLifecycleService.toggleEnabled(5L))
                .isInstanceOf(IllegalSubprogramStateException.class);
    }

    // ==== softDelete ===================================================

    @Test
    @DisplayName("softDelete(happy) : 사전조건 통과 후 trash 위임")
    void softDelete_delegatesToTrash() {
        Subprogram s = sp(5L, board(true, false, false), true, false, false);
        given(subprogramRepository.findById(5L)).willReturn(Optional.of(s));

        subprogramLifecycleService.softDelete(5L);

        verify(softDeleteIntentService).checkPrecondition(s);
        verify(trashLifecycleService).softDeleteToTrash(s);
    }

    @Test
    @DisplayName("softDelete(fail) : 자원 부재 → SubprogramNotFoundException")
    void softDelete_notFound_throws() {
        given(subprogramRepository.findById(404L)).willReturn(Optional.empty());
        assertThatThrownBy(() -> subprogramLifecycleService.softDelete(404L))
                .isInstanceOf(SubprogramNotFoundException.class);
    }

    // ==== undeprecate — 부모 가드 ======================================

    @Test
    @DisplayName("undeprecate : 부모 DEPRECATED → 자식 undeprecate 거절")
    void undeprecate_parentDeprecated_blocks() {
        given(subprogramRepository.findById(6L)).willReturn(Optional.of(sp(6L, board(true, true, false), true, true, false)));
        assertThatThrownBy(() -> subprogramLifecycleService.undeprecate(6L))
                .isInstanceOf(ChildLifecycleBlockedByParentException.class);
    }

    @Test
    @DisplayName("undeprecate : 부모 DELETED → 자식 undeprecate 거절")
    void undeprecate_parentDeleted_blocks() {
        given(subprogramRepository.findById(6L)).willReturn(Optional.of(sp(6L, board(true, false, true), true, true, false)));
        assertThatThrownBy(() -> subprogramLifecycleService.undeprecate(6L))
                .isInstanceOf(ChildLifecycleBlockedByParentException.class);
    }

    @Test
    @DisplayName("undeprecate : 공용 → 부모 가드 미적용, 정상 해제")
    void undeprecate_commonScope_passes() {
        Subprogram s = sp(11L, null, true, true, false);
        given(subprogramRepository.findById(11L)).willReturn(Optional.of(s));
        subprogramLifecycleService.undeprecate(11L);
        assertThat(s.isDeprecated()).isFalse();
    }

    // ==== deprecate ====================================================

    @Test
    @DisplayName("deprecate(happy) : Active → Deprecated 전이")
    void deprecate_happy() {
        Subprogram s = sp(6L, board(true, false, false), true, false, false);
        given(subprogramRepository.findById(6L)).willReturn(Optional.of(s));
        subprogramLifecycleService.deprecate(6L);
        assertThat(s.isDeprecated()).isTrue();
    }

    // ==== restore — 부모 가드 + 중복키 ==================================

    @Test
    @DisplayName("restore : 부모 DELETED → 자식 단독 restore 거절")
    void restore_parentDeleted_blocks() {
        given(subprogramRepository.findById(7L)).willReturn(Optional.of(sp(7L, board(true, false, true), false, false, true)));
        assertThatThrownBy(() -> subprogramLifecycleService.restore(7L))
                .isInstanceOf(ChildLifecycleBlockedByParentException.class);
    }

    @Test
    @DisplayName("restore : 공용 → 부모 가드 건너뜀, 기존 중복키 가드는 그대로 (Duplicate)")
    void restore_commonScope_skipsParentGuard_keepsDupGuard() {
        given(subprogramRepository.findById(12L)).willReturn(Optional.of(sp(12L, null, false, false, true)));
        given(subprogramRepository.findActiveByCommonKey(SubprogramKind.DRIVER, "a", "1.0"))
                .willReturn(Optional.of(sp(99L, null, true, false, false)));
        assertThatThrownBy(() -> subprogramLifecycleService.restore(12L))
                .isInstanceOf(DuplicateSubprogramVersionException.class);
    }

    @Test
    @DisplayName("restore(happy) : 부모 active + 활성키 충돌 없음 → trash service 위임")
    void restore_parentActive_delegatesToTrash() {
        Subprogram s = sp(13L, board(true, false, false), false, false, true);
        given(subprogramRepository.findById(13L)).willReturn(Optional.of(s));
        given(subprogramRepository.findActiveByBoardKey(SubprogramKind.DRIVER, 10L, "a", "1.0"))
                .willReturn(Optional.empty());

        subprogramLifecycleService.restore(13L);

        verify(trashLifecycleService).restoreFromTrash(any(), any());
    }

    // ==== purge ========================================================

    @Test
    @DisplayName("purge(happy) : soft-deleted Subprogram 영구 삭제 + 트리 정리")
    void purge_whenSoftDeleted_deletesEntity() {
        Subprogram s = sp(20L, board(true, false, false), false, false, true);
        given(subprogramRepository.findById(20L)).willReturn(Optional.of(s));

        subprogramLifecycleService.purge(20L);

        verify(bundleTreeCleanupService).purgeExistingTree(any(), anyString());
        verify(subprogramRepository).delete(s);
    }

    @Test
    @DisplayName("purge(fail) : 활성 Subprogram 에 purge → IllegalSubprogramStateException")
    void purge_whenActive_throws() {
        Subprogram s = sp(20L, board(true, false, false), true, false, false);
        given(subprogramRepository.findById(20L)).willReturn(Optional.of(s));

        assertThatThrownBy(() -> subprogramLifecycleService.purge(20L))
                .isInstanceOf(IllegalSubprogramStateException.class);
        verify(subprogramRepository, never()).delete(any());
    }

    // ==== purgeWithTypedNameCheck — static TypedNameGuard ===============

    @Test
    @DisplayName("purgeWithTypedNameCheck(match) : 입력명이 displayName(=name) 과 일치 → purge 진행")
    void purgeWithTypedNameCheck_nameMatches_purges() {
        Subprogram s = sp(21L, board(true, false, false), false, false, true);  // displayName() = "a"
        given(subprogramRepository.findById(21L)).willReturn(Optional.of(s));

        subprogramLifecycleService.purgeWithTypedNameCheck(21L, "a");

        verify(subprogramRepository).delete(s);
    }

    @Test
    @DisplayName("purgeWithTypedNameCheck(mismatch) : 입력명 불일치 → TypedNameMismatchException + 삭제 미호출")
    void purgeWithTypedNameCheck_nameMismatch_propagatesAndNoDelete() {
        Subprogram s = sp(21L, board(true, false, false), false, false, true);  // displayName() = "a"
        given(subprogramRepository.findById(21L)).willReturn(Optional.of(s));

        assertThatThrownBy(() -> subprogramLifecycleService.purgeWithTypedNameCheck(21L, "wrong"))
                .isInstanceOf(TypedNameMismatchException.class);
        verify(subprogramRepository, never()).delete(any());
    }

    @Test
    @DisplayName("purgeWithTypedNameCheck(active) : 활성 자원 → IllegalSubprogramStateException (검증 전 차단)")
    void purgeWithTypedNameCheck_active_throwsBeforeCheck() {
        Subprogram s = sp(21L, board(true, false, false), true, false, false);  // not deleted
        given(subprogramRepository.findById(21L)).willReturn(Optional.of(s));

        assertThatThrownBy(() -> subprogramLifecycleService.purgeWithTypedNameCheck(21L, "a"))
                .isInstanceOf(IllegalSubprogramStateException.class);
        verify(subprogramRepository, never()).delete(any());
    }

    @Test
    @DisplayName("purgeWithTypedNameCheck(notfound) : 자원 부재 → SubprogramNotFoundException")
    void purgeWithTypedNameCheck_missing_throws() {
        given(subprogramRepository.findById(404L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> subprogramLifecycleService.purgeWithTypedNameCheck(404L, "anything"))
                .isInstanceOf(SubprogramNotFoundException.class);
        verify(subprogramRepository, never()).delete(any());
    }
}
