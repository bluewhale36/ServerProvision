package com.example.serverprovision.management.bios.service;

import com.example.serverprovision.global.exception.ChildLifecycleBlockedByParentException;
import com.example.serverprovision.global.exception.TypedNameMismatchException;
import com.example.serverprovision.global.lifecycle.SoftDeleteIntentService;
import com.example.serverprovision.global.trash.TrashLifecycleService;
import com.example.serverprovision.management.bios.entity.BoardBIOS;
import com.example.serverprovision.management.bios.exception.BiosNotFoundException;
import com.example.serverprovision.management.bios.exception.IllegalBiosStateException;
import com.example.serverprovision.management.bios.repository.BiosRepository;
import com.example.serverprovision.management.board.entity.BoardModel;
import com.example.serverprovision.management.board.enums.Vendor;
import com.example.serverprovision.management.board.repository.BoardModelRepository;
import com.example.serverprovision.management.common.filesystem.service.BundleTreeCleanupService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.file.Path;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * R4-3 CP4 — BiosLifecycleService 단위 테스트.
 *
 * <p>옛 {@code BiosServiceTest} 의 lifecycle 시나리오(toggle / purge)와 {@code BiosParentGuardTest} 전체를
 * 본 file 로 이동. 시그니처가 단일 biosId 로 재성형됐고 부모 lookup 은 entity 의 {@code boardModel}
 * reference + repo 재조회로 자체 수행({@code IsoLifecycleServiceTest} 미러).</p>
 *
 * <p>typed-name purge 는 production 이 static {@code TypedNameGuard.verify} 를 쓰므로 verifier mock 이 없다 —
 * 실제 엔티티 {@code displayName()}(= name) 과의 일치/불일치로 트리거한다(R7-2 정비).</p>
 */
@ExtendWith(MockitoExtension.class)
class BiosLifecycleServiceTest {

    @Mock BiosRepository biosRepository;
    @Mock BoardModelRepository boardModelRepository;
    @Mock TrashLifecycleService trashLifecycleService;
    @Mock SoftDeleteIntentService softDeleteIntentService;
    @Mock BundleTreeCleanupService bundleTreeCleanupService;

    @InjectMocks BiosLifecycleService biosLifecycleService;

    // ==== assertBelongsToBoard — URL forging 가드 ========================

    @Test
    @DisplayName("assertBelongsToBoard : entity 의 부모 id 와 expectedBoardId 일치 → 통과")
    void assertBelongsToBoard_matches_passes() {
        BoardModel parent = parent(2L, true, false, false);
        BoardBIOS bios = bios(5L, parent, true, false, false);
        given(biosRepository.findById(5L)).willReturn(Optional.of(bios));

        biosLifecycleService.assertBelongsToBoard(5L, 2L);
        // throw 없으면 통과
    }

    @Test
    @DisplayName("assertBelongsToBoard : URL 의 boardId 가 entity 부모 id 와 불일치 → BiosNotFoundException (forging 404)")
    void assertBelongsToBoard_mismatch_throws() {
        BoardModel parent = parent(2L, true, false, false);
        BoardBIOS bios = bios(5L, parent, true, false, false);
        given(biosRepository.findById(5L)).willReturn(Optional.of(bios));

        assertThatThrownBy(() -> biosLifecycleService.assertBelongsToBoard(5L, 999L))
                .isInstanceOf(BiosNotFoundException.class);
    }

    @Test
    @DisplayName("assertBelongsToBoard : entity 부재 → BiosNotFoundException")
    void assertBelongsToBoard_missing_throws() {
        given(biosRepository.findById(404L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> biosLifecycleService.assertBelongsToBoard(404L, 2L))
                .isInstanceOf(BiosNotFoundException.class);
    }

    // ==== toggleEnabled ================================================

    @Test
    @DisplayName("toggleEnabled(happy) : 활성 BIOS disable 은 부모 상태 무관 자유")
    void toggleEnabled_disable_free() {
        BoardModel parent = parent(2L, true, false, false);
        BoardBIOS bios = bios(5L, parent, true, false, false);
        given(biosRepository.findById(5L)).willReturn(Optional.of(bios));

        biosLifecycleService.toggleEnabled(5L);

        assertThat(bios.isEnabled()).isFalse();
    }

    @Test
    @DisplayName("toggleEnabled(fail) : 삭제된 BIOS 에는 IllegalBiosStateException")
    void toggleEnabled_deleted_throws() {
        BoardModel parent = parent(2L, true, false, false);
        BoardBIOS bios = bios(5L, parent, true, false, true);   // soft-deleted
        given(biosRepository.findById(5L)).willReturn(Optional.of(bios));

        assertThatThrownBy(() -> biosLifecycleService.toggleEnabled(5L))
                .isInstanceOf(IllegalBiosStateException.class);
    }

    @Test
    @DisplayName("toggle enable 시도 — 부모 Board disabled 면 거절 (ChildLifecycleBlockedByParentException)")
    void toggleEnable_parentDisabled_rejects() {
        BoardModel parent = parent(2L, false, false, false);    // disabled
        BoardBIOS bios = bios(5L, parent, false, false, false);
        given(biosRepository.findById(5L)).willReturn(Optional.of(bios));
        given(boardModelRepository.findById(2L)).willReturn(Optional.of(parent));

        assertThatThrownBy(() -> biosLifecycleService.toggleEnabled(5L))
                .isInstanceOf(ChildLifecycleBlockedByParentException.class)
                .extracting("parentState").isEqualTo("DISABLED");
        assertThat(bios.isEnabled()).isFalse();
    }

    // ==== undeprecate — 부모 가드 ======================================

    @Test
    @DisplayName("undeprecate 시도 — 부모 Board deprecated 면 거절")
    void undeprecate_parentDeprecated_rejects() {
        BoardModel parent = parent(2L, true, true, false);
        BoardBIOS bios = bios(5L, parent, true, true, false);
        given(biosRepository.findById(5L)).willReturn(Optional.of(bios));
        given(boardModelRepository.findById(2L)).willReturn(Optional.of(parent));

        assertThatThrownBy(() -> biosLifecycleService.undeprecate(5L))
                .isInstanceOf(ChildLifecycleBlockedByParentException.class)
                .extracting("parentState").isEqualTo("DEPRECATED");
    }

    @Test
    @DisplayName("undeprecate — 부모 Board active 면 OK")
    void undeprecate_parentActive_succeeds() {
        BoardModel parent = parent(2L, true, false, false);
        BoardBIOS bios = bios(5L, parent, true, true, false);
        given(biosRepository.findById(5L)).willReturn(Optional.of(bios));
        given(boardModelRepository.findById(2L)).willReturn(Optional.of(parent));

        biosLifecycleService.undeprecate(5L);

        assertThat(bios.isDeprecated()).isFalse();
    }

    // ==== restore — 부모 가드 ==========================================

    @Test
    @DisplayName("restore 시도 — 부모 Board deleted 면 거절")
    void restore_parentDeleted_rejects() {
        BoardModel parent = parent(2L, true, false, true);      // deleted
        BoardBIOS bios = bios(5L, parent, true, false, true);
        given(biosRepository.findById(5L)).willReturn(Optional.of(bios));
        given(boardModelRepository.findById(2L)).willReturn(Optional.of(parent));

        assertThatThrownBy(() -> biosLifecycleService.restore(5L))
                .isInstanceOf(ChildLifecycleBlockedByParentException.class)
                .extracting("parentState").isEqualTo("DELETED");
    }

    @Test
    @DisplayName("restore — 부모 active + 활성 버전 충돌 없음 → trash service 위임")
    void restore_parentActive_delegatesToTrash() {
        BoardModel parent = parent(2L, true, false, false);
        BoardBIOS bios = bios(5L, parent, true, false, true);   // soft-deleted
        given(biosRepository.findById(5L)).willReturn(Optional.of(bios));
        given(boardModelRepository.findById(2L)).willReturn(Optional.of(parent));
        given(biosRepository.existsByBoardModel_IdAndVersionAndIsDeletedFalse(2L, bios.getVersion()))
                .willReturn(false);

        biosLifecycleService.restore(5L);

        verify(trashLifecycleService).restoreFromTrash(any(), any());
    }

    // ==== purge ========================================================

    @Test
    @DisplayName("purge(happy) : soft-deleted BIOS 영구 삭제 + 트리 정리")
    void purge_whenSoftDeleted_deletesEntity() {
        BoardModel parent = parent(2L, true, false, false);
        BoardBIOS softDeleted = bios(5L, parent, true, false, true);
        given(biosRepository.findById(5L)).willReturn(Optional.of(softDeleted));

        biosLifecycleService.purge(5L);

        verify(bundleTreeCleanupService).purgeExistingTree(Path.of(softDeleted.getTreeRootPath()), "purgeBios");
        verify(biosRepository).delete(softDeleted);
    }

    @Test
    @DisplayName("purge(fail) : 활성 BIOS 에 purge → IllegalBiosStateException")
    void purge_whenActive_throws() {
        BoardModel parent = parent(2L, true, false, false);
        BoardBIOS active = bios(5L, parent, true, false, false);
        given(biosRepository.findById(5L)).willReturn(Optional.of(active));

        assertThatThrownBy(() -> biosLifecycleService.purge(5L))
                .isInstanceOf(IllegalBiosStateException.class);
        verify(biosRepository, never()).delete(any());
    }

    @Test
    @DisplayName("purgeWithTypedNameCheck(happy) : 입력명이 BIOS displayName 과 일치 → purge 진행")
    void purgeWithTypedNameCheck_nameMatches_purges() {
        BoardModel parent = parent(2L, true, false, false);
        BoardBIOS softDeleted = bios(5L, parent, true, false, true);   // displayName = name = "BIOS-5"
        given(biosRepository.findById(5L)).willReturn(Optional.of(softDeleted));

        biosLifecycleService.purgeWithTypedNameCheck(5L, softDeleted.displayName());

        verify(biosRepository).delete(softDeleted);
    }

    @Test
    @DisplayName("purgeWithTypedNameCheck(mismatch) : 입력명 불일치 → TypedNameMismatchException + 삭제 미호출")
    void purgeWithTypedNameCheck_nameMismatch_propagatesAndNoDelete() {
        BoardModel parent = parent(2L, true, false, false);
        BoardBIOS softDeleted = bios(5L, parent, true, false, true);
        given(biosRepository.findById(5L)).willReturn(Optional.of(softDeleted));

        assertThatThrownBy(() -> biosLifecycleService.purgeWithTypedNameCheck(5L, "wrong"))
                .isInstanceOf(TypedNameMismatchException.class);
        verify(biosRepository, never()).delete(any());
    }

    @Test
    @DisplayName("purgeWithTypedNameCheck(상태위반) : BIOS 부재 → BiosNotFoundException (검증 진입 전 차단)")
    void purgeWithTypedNameCheck_biosMissing_throwsBeforeCheck() {
        given(biosRepository.findById(404L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> biosLifecycleService.purgeWithTypedNameCheck(404L, "anything"))
                .isInstanceOf(BiosNotFoundException.class);
        verify(biosRepository, never()).delete(any());
    }

    // ==== fixtures =====================================================

    private static BoardModel parent(Long id, boolean enabled, boolean deprecated, boolean deleted) {
        BoardModel p = BoardModel.builder()
                .id(id).vendor(Vendor.ASUS).modelName("P13R-E")
                .ownEnabled(enabled).ownDeprecated(deprecated).isDeleted(deleted)
                .build();
        p.recomputeEffective();
        return p;
    }

    private static BoardBIOS bios(Long id, BoardModel parent, boolean enabled, boolean deprecated, boolean deleted) {
        BoardBIOS b = BoardBIOS.builder()
                .id(id).boardModel(parent).name("BIOS-" + id).version("1." + id)
                .treeRootPath("/fw/bios/" + id).entrypointRelativePath("flash.nsh")
                .manifestHash("h").markerSignature("s")
                .fileCount(2).totalBytes(100L)
                .ownEnabled(enabled).ownDeprecated(deprecated).isDeleted(deleted)
                .build();
        b.recomputeEffective();
        return b;
    }
}
