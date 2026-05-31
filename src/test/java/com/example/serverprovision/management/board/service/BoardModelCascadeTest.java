package com.example.serverprovision.management.board.service;

import com.example.serverprovision.global.exception.ChildLifecycleBlockedByParentException;
import com.example.serverprovision.management.board.entity.BoardModel;
import com.example.serverprovision.management.board.enums.Vendor;
import com.example.serverprovision.management.board.repository.BoardModelRepository;
import com.example.serverprovision.management.bios.entity.BoardBIOS;
import com.example.serverprovision.management.bios.repository.BiosRepository;
import com.example.serverprovision.management.bios.service.BiosService;
import com.example.serverprovision.management.bmc.entity.BoardBMC;
import com.example.serverprovision.management.bmc.repository.BmcRepository;
import com.example.serverprovision.management.bmc.service.BmcService;
import com.example.serverprovision.management.board.exception.IllegalBoardModelStateException;
import com.example.serverprovision.management.common.nudge.NudgeRegistry;
import com.example.serverprovision.management.subprogram.entity.Subprogram;
import com.example.serverprovision.management.subprogram.enums.SubprogramKind;
import com.example.serverprovision.management.subprogram.exception.DuplicateSubprogramVersionException;
import com.example.serverprovision.management.subprogram.vo.BoardScope;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * S5-2-3-1 — Board 부모 lifecycle cascade 단위 테스트.
 * 자식 BIOS / BMC 자식 단독 가드는 각자의 service 테스트에서 추가.
 */
@ExtendWith(MockitoExtension.class)
class BoardModelCascadeTest {

    @Mock BoardModelRepository boardModelRepository;
    @Mock BiosRepository biosRepository;
    @Mock BmcRepository bmcRepository;
    @Mock NudgeRegistry nudgeRegistry;
    @Mock BiosService biosService;
    @Mock BmcService bmcService;
    @Mock com.example.serverprovision.management.subprogram.repository.SubprogramRepository subprogramRepository;
    @Mock com.example.serverprovision.management.subprogram.service.SubprogramService subprogramService;
    @InjectMocks BoardModelService boardModelService;

    private BoardModel parent(boolean enabled, boolean deprecated) {
        return BoardModel.builder()
                .id(7L)
                .vendor(Vendor.ASUS).modelName("P13R-E")
                .isEnabled(enabled).isDeprecated(deprecated).isDeleted(false)
                .build();
    }

    private BoardBIOS bios(Long id, BoardModel parent, boolean enabled, boolean deprecated, boolean deleted) {
        return BoardBIOS.builder()
                .id(id).boardModel(parent).name("BIOS-" + id).version("1." + id)
                .isEnabled(enabled).isDeprecated(deprecated).isDeleted(deleted)
                .build();
    }

    private BoardBMC bmc(Long id, BoardModel parent, boolean enabled, boolean deprecated, boolean deleted) {
        return BoardBMC.builder()
                .id(id).boardModel(parent).name("BMC-" + id).version("2." + id)
                .treeRootPath("/fw/bmc/" + id).legacyFilePath("/fw/bmc/" + id).boardModelIdMirror(7L)
                .entrypointRelativePath("flash.nsh")
                .manifestHash("hash").markerSignature("sig").fileCount(2).totalBytes(128L)
                .isEnabled(enabled).isDeprecated(deprecated).isDeleted(deleted)
                .build();
    }

    @Test
    @DisplayName("HF-2 toggle off : 부모 비활성 → BIOS/BMC enabled 자식 전부(active + deprecated + soft-deleted) 비활성 동기화 (비대칭).")
    void toggleOff_disablesAllEnabledChildren_includingTrashedAndDeprecated() {
        BoardModel p = parent(true, false);
        BoardBIOS biosActive = bios(101L, p, true, false, false);
        BoardBIOS biosDeprecated = bios(102L, p, true, true, false);
        BoardBMC bmcActive = bmc(201L, p, true, false, false);
        BoardBMC bmcDeleted = bmc(202L, p, true, false, true);
        given(boardModelRepository.findByIdAndIsDeletedFalse(7L)).willReturn(Optional.of(p));
        given(biosRepository.findAllByBoardModel_IdOrderByVersionDesc(7L))
                .willReturn(List.of(biosActive, biosDeprecated));
        given(bmcRepository.findAllByBoardModel_IdOrderByVersionDesc(7L))
                .willReturn(List.of(bmcActive, bmcDeleted));

        boardModelService.toggleEnabled(7L);

        assertThat(p.isEnabled()).isFalse();
        assertThat(biosActive.isEnabled()).isFalse();        // 동기화
        assertThat(biosDeprecated.isEnabled()).isFalse();    // HF-2 — deprecated 자식도 동기화
        assertThat(bmcActive.isEnabled()).isFalse();         // 동기화
        assertThat(bmcDeleted.isEnabled()).isFalse();        // HF-2 — soft-deleted 자식도 동기화 (stale 씨앗 차단)
    }

    @Test
    @DisplayName("HF-2 toggle on : 부모 활성 → 자식 cascade 미적용. BIOS/BMC repository 조회조차 안 함 (early return).")
    void toggleOn_doesNotCascadeToChildren() {
        BoardModel p = parent(false, false);
        given(boardModelRepository.findByIdAndIsDeletedFalse(7L)).willReturn(Optional.of(p));

        boardModelService.toggleEnabled(7L);

        assertThat(p.isEnabled()).isTrue();
        verifyNoInteractions(biosRepository, bmcRepository, biosService, bmcService, subprogramRepository, subprogramService);
    }

    @Test
    @DisplayName("deprecate : 부모 active → deprecated. 자식 active 만 deprecate")
    void deprecate_cascadesActiveChildren() {
        BoardModel p = parent(true, false);
        BoardBIOS active = bios(101L, p, true, false, false);
        BoardBIOS alreadyDep = bios(102L, p, true, true, false);
        BoardBMC bmcActive = bmc(201L, p, true, false, false);
        given(boardModelRepository.findByIdAndIsDeletedFalse(7L)).willReturn(Optional.of(p));
        given(biosRepository.findAllByBoardModel_IdOrderByVersionDesc(7L))
                .willReturn(List.of(active, alreadyDep));
        given(bmcRepository.findAllByBoardModel_IdOrderByVersionDesc(7L))
                .willReturn(List.of(bmcActive));

        boardModelService.deprecate(7L);

        assertThat(p.isDeprecated()).isTrue();
        assertThat(active.isDeprecated()).isTrue();
        assertThat(alreadyDep.isDeprecated()).isTrue();  // 그대로
        assertThat(bmcActive.isDeprecated()).isTrue();
    }

    @Test
    @DisplayName("undeprecate : 부모 deprecated → active. 자식 deprecated 만 undeprecate")
    void undeprecate_cascadesDeprecatedChildren() {
        BoardModel p = parent(true, true);
        BoardBIOS active = bios(101L, p, true, false, false);
        BoardBIOS deprecated = bios(102L, p, true, true, false);
        BoardBMC bmcDep = bmc(201L, p, true, true, false);
        given(boardModelRepository.findByIdAndIsDeletedFalse(7L)).willReturn(Optional.of(p));
        given(biosRepository.findAllByBoardModel_IdOrderByVersionDesc(7L))
                .willReturn(List.of(active, deprecated));
        given(bmcRepository.findAllByBoardModel_IdOrderByVersionDesc(7L))
                .willReturn(List.of(bmcDep));

        boardModelService.undeprecate(7L);

        assertThat(p.isDeprecated()).isFalse();
        assertThat(active.isDeprecated()).isFalse();      // 그대로
        assertThat(deprecated.isDeprecated()).isFalse();   // cascade
        assertThat(bmcDep.isDeprecated()).isFalse();
    }

    // ──────────────────────────────────────────────────────────────
    // R3-1 — board-scoped Subprogram cascade parity (공용은 boardModel.id 쿼리로 자연 제외)
    // ──────────────────────────────────────────────────────────────

    private Subprogram subprogram(Long id, BoardModel parent, boolean enabled, boolean deprecated, boolean deleted) {
        return Subprogram.builder()
                .id(id).kind(SubprogramKind.DRIVER).boardModel(parent)
                .name("drv-" + id).version("1." + id).treeRootPath("/sp/" + id)
                .manifestHash("h").fileCount(1).totalBytes(1L)
                .isEnabled(enabled).isDeprecated(deprecated).isDeleted(deleted)
                .build();
    }

    private BoardModel deletedParent() {
        return BoardModel.builder().id(7L).vendor(Vendor.ASUS).modelName("P13R-E")
                .isEnabled(true).isDeprecated(false).isDeleted(true).build();
    }

    @Test
    @DisplayName("R3-1 toggle off : 부모 비활성 → board-scoped Subprogram(enabled) 동반 비활성 (비활성화만 cascade)")
    void toggleOff_cascadesToBoardScopedSubprogram() {
        BoardModel p = parent(true, false);
        Subprogram sp = subprogram(301L, p, true, false, false);
        given(boardModelRepository.findByIdAndIsDeletedFalse(7L)).willReturn(Optional.of(p));
        given(biosRepository.findAllByBoardModel_IdOrderByVersionDesc(7L)).willReturn(List.of());
        given(bmcRepository.findAllByBoardModel_IdOrderByVersionDesc(7L)).willReturn(List.of());
        given(subprogramRepository.findAllByBoardModel_Id(7L)).willReturn(List.of(sp));

        boardModelService.toggleEnabled(7L);

        assertThat(p.isEnabled()).isFalse();
        assertThat(sp.isEnabled()).isFalse();
    }

    @Test
    @DisplayName("R3-1 deprecate : 부모 deprecate → board-scoped Subprogram(active) deprecate")
    void deprecate_cascadesToSubprogram() {
        BoardModel p = parent(true, false);
        Subprogram active = subprogram(301L, p, true, false, false);
        given(boardModelRepository.findByIdAndIsDeletedFalse(7L)).willReturn(Optional.of(p));
        given(biosRepository.findAllByBoardModel_IdOrderByVersionDesc(7L)).willReturn(List.of());
        given(bmcRepository.findAllByBoardModel_IdOrderByVersionDesc(7L)).willReturn(List.of());
        given(subprogramRepository.findAllByBoardModel_Id(7L)).willReturn(List.of(active));

        boardModelService.deprecate(7L);

        assertThat(active.isDeprecated()).isTrue();
    }

    @Test
    @DisplayName("R3-1 undeprecate : 부모 undeprecate → board-scoped Subprogram(deprecated) undeprecate")
    void undeprecate_cascadesToSubprogram() {
        BoardModel p = parent(true, true);
        Subprogram dep = subprogram(301L, p, true, true, false);
        given(boardModelRepository.findByIdAndIsDeletedFalse(7L)).willReturn(Optional.of(p));
        given(biosRepository.findAllByBoardModel_IdOrderByVersionDesc(7L)).willReturn(List.of());
        given(bmcRepository.findAllByBoardModel_IdOrderByVersionDesc(7L)).willReturn(List.of());
        given(subprogramRepository.findAllByBoardModel_Id(7L)).willReturn(List.of(dep));

        boardModelService.undeprecate(7L);

        assertThat(dep.isDeprecated()).isFalse();
    }

    @Test
    @DisplayName("R3-1 softDelete : board-scoped active Subprogram → subprogramService.softDelete 위임 (단일 인자)")
    void softDelete_cascadesToSubprogram() {
        BoardModel p = parent(true, false);
        Subprogram sp = subprogram(301L, p, true, false, false);
        given(boardModelRepository.findByIdAndIsDeletedFalse(7L)).willReturn(Optional.of(p));
        given(biosRepository.findAllByBoardModel_IdAndIsDeletedFalseOrderByVersionDesc(7L)).willReturn(List.of());
        given(bmcRepository.findAllByBoardModel_IdAndIsDeletedFalseOrderByVersionDesc(7L)).willReturn(List.of());
        given(subprogramRepository.findAllByBoardModel_IdAndIsDeletedFalse(7L)).willReturn(List.of(sp));

        boardModelService.softDelete(7L);

        assertThat(p.isDeleted()).isTrue();
        verify(subprogramService).softDelete(301L);
    }

    @Test
    @DisplayName("R3-1 restore(cascade) : board.restore 後 deleted Subprogram → subprogramService.restore 위임 (부모 active → R2-2-1 가드 통과)")
    void restoreCascade_delegatesToSubprogram() {
        BoardModel p = deletedParent();
        Subprogram sp = subprogram(301L, p, true, false, true);
        given(boardModelRepository.findByIdAndIsDeletedTrue(7L)).willReturn(Optional.of(p));
        given(boardModelRepository.existsByVendorAndModelNameAndIsDeletedFalse(Vendor.ASUS, "P13R-E")).willReturn(false);
        given(biosRepository.findAllByBoardModel_IdAndIsDeletedTrue(7L)).willReturn(List.of());
        given(bmcRepository.findAllByBoardModel_IdAndIsDeletedTrue(7L)).willReturn(List.of());
        given(subprogramRepository.findAllByBoardModel_IdAndIsDeletedTrue(7L)).willReturn(List.of(sp));

        boardModelService.restore(7L, true);

        assertThat(p.isDeleted()).isFalse();
        verify(subprogramService).restore(301L);
    }

    @Test
    @DisplayName("R3-1 restore(cascade) : Subprogram 활성 동일키 충돌 → DuplicateSubprogramVersionException 전파 (전체 롤백)")
    void restoreCascade_subprogramConflict_propagates() {
        BoardModel p = deletedParent();
        Subprogram sp = subprogram(301L, p, true, false, true);
        given(boardModelRepository.findByIdAndIsDeletedTrue(7L)).willReturn(Optional.of(p));
        given(boardModelRepository.existsByVendorAndModelNameAndIsDeletedFalse(Vendor.ASUS, "P13R-E")).willReturn(false);
        given(biosRepository.findAllByBoardModel_IdAndIsDeletedTrue(7L)).willReturn(List.of());
        given(bmcRepository.findAllByBoardModel_IdAndIsDeletedTrue(7L)).willReturn(List.of());
        given(subprogramRepository.findAllByBoardModel_IdAndIsDeletedTrue(7L)).willReturn(List.of(sp));
        willThrow(new DuplicateSubprogramVersionException(SubprogramKind.DRIVER, BoardScope.ofBoard(7L), "drv-301", "1.301"))
                .given(subprogramService).restore(301L);

        assertThatThrownBy(() -> boardModelService.restore(7L, true))
                .isInstanceOf(DuplicateSubprogramVersionException.class);
    }

    @Test
    @DisplayName("R3-1 purge : board-scoped Subprogram 잔존 시 영구삭제 거절")
    void purge_blockedByRemainingSubprogram() {
        BoardModel p = deletedParent();
        given(boardModelRepository.findByIdAndIsDeletedTrue(7L)).willReturn(Optional.of(p));
        given(biosRepository.findAllByBoardModel_IdOrderByVersionDesc(7L)).willReturn(List.of());
        given(bmcRepository.findAllByBoardModel_IdOrderByVersionDesc(7L)).willReturn(List.of());
        given(subprogramRepository.findAllByBoardModel_Id(7L)).willReturn(List.of(subprogram(301L, p, true, false, true)));

        assertThatThrownBy(() -> boardModelService.purge(7L))
                .isInstanceOf(IllegalBoardModelStateException.class);
        verify(boardModelRepository, never()).delete(p);
    }

    @Test
    @DisplayName("R3-1 purge : 자식 BIOS/BMC/Subprogram 0 → 영구삭제 허용")
    void purge_okWhenChildless() {
        BoardModel p = deletedParent();
        given(boardModelRepository.findByIdAndIsDeletedTrue(7L)).willReturn(Optional.of(p));
        given(biosRepository.findAllByBoardModel_IdOrderByVersionDesc(7L)).willReturn(List.of());
        given(bmcRepository.findAllByBoardModel_IdOrderByVersionDesc(7L)).willReturn(List.of());
        given(subprogramRepository.findAllByBoardModel_Id(7L)).willReturn(List.of());

        boardModelService.purge(7L);

        verify(boardModelRepository).delete(p);
    }
}
