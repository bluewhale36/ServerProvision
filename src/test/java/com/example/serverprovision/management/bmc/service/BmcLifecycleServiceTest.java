package com.example.serverprovision.management.bmc.service;

import com.example.serverprovision.global.exception.ChildLifecycleBlockedByParentException;
import com.example.serverprovision.global.exception.TypedNameMismatchException;
import com.example.serverprovision.global.lifecycle.SoftDeleteIntentService;
import com.example.serverprovision.global.trash.TrashLifecycleService;
import com.example.serverprovision.management.bmc.entity.BoardBMC;
import com.example.serverprovision.management.bmc.exception.BmcNotFoundException;
import com.example.serverprovision.management.bmc.exception.IllegalBmcStateException;
import com.example.serverprovision.management.bmc.repository.BmcRepository;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * R5-3 CP4 — BmcLifecycleService 단위 테스트.
 *
 * <p>구 {@code BmcServiceTest} 의 lifecycle 시나리오(purge happy/fail) + {@code BmcParentGuardTest} 의
 * 부모 가드 시나리오(toggle/undeprecate/restore)를 본 file 로 응집. 시그니처가 단일 bmcId 로 정렬됐고
 * 부모 lookup 은 entity 의 {@code boardModel} reference 로 자체 수행.</p>
 *
 * <p>typed-name 검증은 의존성 0 의 static {@code TypedNameGuard} 직접 호출 — verifier 빈/mock 이 사라졌다.
 * typed-name 일치/불일치는 BoardBMC 의 displayName()(= name) 으로 결정 ({@code IsoLifecycleServiceTest} 미러).</p>
 */
@ExtendWith(MockitoExtension.class)
class BmcLifecycleServiceTest {

    @Mock BmcRepository bmcRepository;
    @Mock BoardModelRepository boardModelRepository;
    @Mock TrashLifecycleService trashLifecycleService;
    @Mock SoftDeleteIntentService softDeleteIntentService;
    @Mock BundleTreeCleanupService bundleTreeCleanupService;

    @InjectMocks BmcLifecycleService bmcLifecycleService;

    // ==== assertBelongsToBoard — URL forging 가드 =========================

    @Test
    @DisplayName("assertBelongsToBoard : entity 의 부모 id 와 expectedBoardId 일치 → 통과")
    void assertBelongsToBoard_matches_passes() {
        BoardModel parent = parent(2L, true, false, false);
        BoardBMC bmc = bmc(5L, parent, true, false, false);
        given(bmcRepository.findById(5L)).willReturn(Optional.of(bmc));

        bmcLifecycleService.assertBelongsToBoard(5L, 2L);
        // throw 없으면 통과
    }

    @Test
    @DisplayName("assertBelongsToBoard : URL 의 boardId 가 entity 부모 id 와 불일치 → BmcNotFoundException")
    void assertBelongsToBoard_mismatch_throws() {
        BoardModel parent = parent(2L, true, false, false);
        BoardBMC bmc = bmc(5L, parent, true, false, false);
        given(bmcRepository.findById(5L)).willReturn(Optional.of(bmc));

        assertThatThrownBy(() -> bmcLifecycleService.assertBelongsToBoard(5L, 999L))
                .isInstanceOf(BmcNotFoundException.class);
    }

    @Test
    @DisplayName("assertBelongsToBoard : entity 부재 → BmcNotFoundException")
    void assertBelongsToBoard_missing_throws() {
        given(bmcRepository.findById(404L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> bmcLifecycleService.assertBelongsToBoard(404L, 2L))
                .isInstanceOf(BmcNotFoundException.class);
    }

    // ==== toggleEnabled — 부모 가드 =====================================

    @Test
    @DisplayName("BMC enable 시도 — 부모 Board disabled 면 거절")
    void toggleEnable_parentDisabled_rejects() {
        BoardModel parent = parent(2L, false, false, false);   // disabled
        BoardBMC disabled = bmc(5L, parent, false, false, false);
        given(bmcRepository.findById(5L)).willReturn(Optional.of(disabled));
        given(boardModelRepository.findById(2L)).willReturn(Optional.of(parent));

        assertThatThrownBy(() -> bmcLifecycleService.toggleEnabled(5L))
                .isInstanceOf(ChildLifecycleBlockedByParentException.class)
                .extracting("parentState").isEqualTo("DISABLED");
        assertThat(disabled.isEnabled()).isFalse();
    }

    @Test
    @DisplayName("BMC enable — 부모 deprecated 이어도 허용 (차원 독립: deprecated ≠ disabled)")
    void toggleEnable_parentDeprecated_allows() {
        BoardModel parent = parent(2L, true, true, false);   // enabled + deprecated
        BoardBMC disabled = bmc(5L, parent, false, false, false);
        given(bmcRepository.findById(5L)).willReturn(Optional.of(disabled));
        given(boardModelRepository.findById(2L)).willReturn(Optional.of(parent));

        bmcLifecycleService.toggleEnabled(5L);

        assertThat(disabled.isEnabled()).isTrue();
    }

    @Test
    @DisplayName("BMC disable 은 부모 상태 무관 자유")
    void toggleDisable_freeWhenParentActive() {
        BoardModel parent = parent(2L, true, false, false);
        BoardBMC active = bmc(5L, parent, true, false, false);
        given(bmcRepository.findById(5L)).willReturn(Optional.of(active));

        bmcLifecycleService.toggleEnabled(5L);

        assertThat(active.isEnabled()).isFalse();
    }

    @Test
    @DisplayName("toggleEnabled(fail) : soft-deleted BMC → IllegalBmcStateException")
    void toggleEnabled_deleted_throws() {
        BoardModel parent = parent(2L, true, false, false);
        BoardBMC deleted = bmc(5L, parent, true, false, true);
        given(bmcRepository.findById(5L)).willReturn(Optional.of(deleted));

        assertThatThrownBy(() -> bmcLifecycleService.toggleEnabled(5L))
                .isInstanceOf(IllegalBmcStateException.class);
    }

    // ==== undeprecate — 부모 가드 ======================================

    @Test
    @DisplayName("BMC undeprecate — 부모 Board deprecated 면 거절")
    void undeprecate_parentDeprecated_rejects() {
        BoardModel parent = parent(2L, true, true, false);
        BoardBMC deprecated = bmc(5L, parent, true, true, false);
        given(bmcRepository.findById(5L)).willReturn(Optional.of(deprecated));
        given(boardModelRepository.findById(2L)).willReturn(Optional.of(parent));

        assertThatThrownBy(() -> bmcLifecycleService.undeprecate(5L))
                .isInstanceOf(ChildLifecycleBlockedByParentException.class)
                .extracting("parentState").isEqualTo("DEPRECATED");
    }

    @Test
    @DisplayName("BMC undeprecate — 부모 active 면 OK")
    void undeprecate_parentActive_succeeds() {
        BoardModel parent = parent(2L, true, false, false);
        BoardBMC deprecated = bmc(5L, parent, true, true, false);
        given(bmcRepository.findById(5L)).willReturn(Optional.of(deprecated));
        given(boardModelRepository.findById(2L)).willReturn(Optional.of(parent));

        bmcLifecycleService.undeprecate(5L);

        assertThat(deprecated.isDeprecated()).isFalse();
    }

    // ==== restore — 부모 가드 ==========================================

    @Test
    @DisplayName("BMC restore — 부모 Board deleted 면 거절")
    void restore_parentDeleted_rejects() {
        BoardModel parent = parent(2L, true, false, true);   // deleted
        BoardBMC deleted = bmc(5L, parent, true, false, true);
        given(bmcRepository.findById(5L)).willReturn(Optional.of(deleted));
        given(boardModelRepository.findById(2L)).willReturn(Optional.of(parent));

        assertThatThrownBy(() -> bmcLifecycleService.restore(5L))
                .isInstanceOf(ChildLifecycleBlockedByParentException.class)
                .extracting("parentState").isEqualTo("DELETED");
    }

    @Test
    @DisplayName("BMC restore — 부모 active 면 OK (trash service 위임)")
    void restore_parentActive_delegatesToTrash() {
        BoardModel parent = parent(2L, true, false, false);
        BoardBMC deleted = bmc(5L, parent, true, false, true);
        given(bmcRepository.findById(5L)).willReturn(Optional.of(deleted));
        given(boardModelRepository.findById(2L)).willReturn(Optional.of(parent));
        given(bmcRepository.existsByBoardModel_IdAndVersionAndIsDeletedFalse(2L, deleted.getVersion()))
                .willReturn(false);

        bmcLifecycleService.restore(5L);

        verify(trashLifecycleService).restoreFromTrash(eq(deleted), any());
    }

    @Test
    @DisplayName("BMC restore — 자식 자체가 active 면 거절 (부모는 active)")
    void restore_childAlreadyActive_rejects() {
        BoardModel parent = parent(2L, true, false, false);
        BoardBMC active = bmc(5L, parent, true, false, false);   // not deleted
        given(bmcRepository.findById(5L)).willReturn(Optional.of(active));
        given(boardModelRepository.findById(2L)).willReturn(Optional.of(parent));

        assertThatThrownBy(() -> bmcLifecycleService.restore(5L))
                .isInstanceOf(IllegalBmcStateException.class);
        verify(trashLifecycleService, never()).restoreFromTrash(any(), any());
    }

    // ==== purge ========================================================

    @Test
    @DisplayName("purge(happy) : soft-deleted BMC 영구 삭제 + 트리 정리")
    void purge_whenSoftDeleted_deletesEntity() {
        BoardModel parent = parent(2L, true, false, false);
        BoardBMC softDeleted = bmc(5L, parent, false, false, true);   // deleted=true
        given(bmcRepository.findById(5L)).willReturn(Optional.of(softDeleted));

        bmcLifecycleService.purge(5L);

        verify(bundleTreeCleanupService).purgeExistingTree(Path.of(softDeleted.getTreeRootPath()), "purgeBmc");
        verify(bmcRepository).delete(softDeleted);
    }

    @Test
    @DisplayName("purge(fail) : 활성 BMC 에 purge → IllegalBmcStateException")
    void purge_whenActive_throws() {
        BoardModel parent = parent(2L, true, false, false);
        BoardBMC active = bmc(5L, parent, true, false, false);
        given(bmcRepository.findById(5L)).willReturn(Optional.of(active));

        assertThatThrownBy(() -> bmcLifecycleService.purge(5L))
                .isInstanceOf(IllegalBmcStateException.class);
        verify(bmcRepository, never()).delete(any());
    }

    @Test
    @DisplayName("purgeWithTypedNameCheck(happy) : 입력명이 BMC displayName 과 일치 → purge 진행")
    void purgeWithTypedNameCheck_nameMatches_purges() {
        BoardModel parent = parent(2L, true, false, false);
        BoardBMC softDeleted = bmc(5L, parent, false, false, true);   // soft-deleted, displayName = "AST2600"
        given(bmcRepository.findById(5L)).willReturn(Optional.of(softDeleted));

        bmcLifecycleService.purgeWithTypedNameCheck(5L, "AST2600");

        verify(bmcRepository).delete(softDeleted);
    }

    @Test
    @DisplayName("purgeWithTypedNameCheck(mismatch) : 입력명 불일치 → TypedNameMismatchException + 삭제 미호출")
    void purgeWithTypedNameCheck_nameMismatch_propagatesAndNoDelete() {
        BoardModel parent = parent(2L, true, false, false);
        BoardBMC softDeleted = bmc(5L, parent, false, false, true);
        given(bmcRepository.findById(5L)).willReturn(Optional.of(softDeleted));

        assertThatThrownBy(() -> bmcLifecycleService.purgeWithTypedNameCheck(5L, "wrong"))
                .isInstanceOf(TypedNameMismatchException.class);
        verify(bmcRepository, never()).delete(any());
    }

    @Test
    @DisplayName("purgeWithTypedNameCheck(상태위반) : BMC 부재 → BmcNotFoundException (검증 진입 전 차단)")
    void purgeWithTypedNameCheck_bmcMissing_throwsBeforeCheck() {
        given(bmcRepository.findById(404L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> bmcLifecycleService.purgeWithTypedNameCheck(404L, "anything"))
                .isInstanceOf(BmcNotFoundException.class);
        verify(bmcRepository, never()).delete(any());
    }

    // ==== fixtures =====================================================

    private static BoardModel parent(Long id, boolean enabled, boolean deprecated, boolean deleted) {
        BoardModel p = BoardModel.builder()
                .id(id)
                .vendor(Vendor.GIGABYTE)
                .modelName("MZ32-AR0")
                .ownEnabled(enabled)
                .ownDeprecated(deprecated)
                .isDeleted(deleted)
                .build();
        p.recomputeEffective();
        return p;
    }

    private static BoardBMC bmc(Long id, BoardModel parent, boolean enabled, boolean deprecated, boolean deleted) {
        BoardBMC b = BoardBMC.builder()
                .id(id)
                .boardModel(parent)
                .name("AST2600")
                .version("13.06.25")
                .treeRootPath("/fw/bmc/" + id)
                .legacyFilePath("/fw/bmc/" + id)
                .boardModelIdMirror(parent.getId())
                .entrypointRelativePath("flash.nsh")
                .manifestHash("hash")
                .markerSignature("sig")
                .fileCount(2)
                .totalBytes(128L)
                .ownEnabled(enabled)
                .ownDeprecated(deprecated)
                .isDeleted(deleted)
                .build();
        b.recomputeEffective();
        return b;
    }
}
