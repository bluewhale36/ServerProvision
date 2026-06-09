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

    // R4-1 — own(운영자 의도) 으로 빌드 후 recomputeEffective() 로 초기 effective = own ⊕ 부모 설정.
    private BoardModel parent(boolean enabled, boolean deprecated) {
        BoardModel p = BoardModel.builder()
                .id(7L)
                .vendor(Vendor.ASUS).modelName("P13R-E")
                .ownEnabled(enabled).ownDeprecated(deprecated).isDeleted(false)
                .build();
        p.recomputeEffective();   // 루트 → effective = own
        return p;
    }

    private BoardBIOS bios(Long id, BoardModel parent, boolean ownEnabled, boolean ownDeprecated, boolean deleted) {
        BoardBIOS b = BoardBIOS.builder()
                .id(id).boardModel(parent).name("BIOS-" + id).version("1." + id)
                .ownEnabled(ownEnabled).ownDeprecated(ownDeprecated).isDeleted(deleted)
                .build();
        b.recomputeEffective();
        return b;
    }

    private BoardBMC bmc(Long id, BoardModel parent, boolean ownEnabled, boolean ownDeprecated, boolean deleted) {
        BoardBMC b = BoardBMC.builder()
                .id(id).boardModel(parent).name("BMC-" + id).version("2." + id)
                .treeRootPath("/fw/bmc/" + id).legacyFilePath("/fw/bmc/" + id).boardModelIdMirror(7L)
                .entrypointRelativePath("flash.nsh")
                .manifestHash("hash").markerSignature("sig").fileCount(2).totalBytes(128L)
                .ownEnabled(ownEnabled).ownDeprecated(ownDeprecated).isDeleted(deleted)
                .build();
        b.recomputeEffective();
        return b;
    }

    @Test
    @DisplayName("R4-1 toggle off : 부모 비활성 → 비삭제 자식 effective 비활성(own 보존). soft-deleted 자식은 cascade 제외(restore 시 재계산).")
    void toggleOff_recomputesNonDeletedChildrenDisabled() {
        BoardModel p = parent(true, false);
        BoardBIOS active = bios(101L, p, true, false, false);       // own active
        BoardBIOS opDeprecated = bios(102L, p, true, true, false);  // own deprecated (운영자)
        BoardBMC bmcActive = bmc(201L, p, true, false, false);
        BoardBMC bmcDeleted = bmc(202L, p, true, false, true);      // soft-deleted
        given(boardModelRepository.findByIdAndIsDeletedFalse(7L)).willReturn(Optional.of(p));
        given(biosRepository.findAllByBoardModel_IdOrderByVersionDesc(7L))
                .willReturn(List.of(active, opDeprecated));
        given(bmcRepository.findAllByBoardModel_IdOrderByVersionDesc(7L))
                .willReturn(List.of(bmcActive, bmcDeleted));

        boardModelService.toggleEnabled(7L);   // 부모 활성 → 비활성

        assertThat(p.isEnabled()).isFalse();
        assertThat(active.isEnabled()).isFalse();          // own_en=true 이나 부모 비활성 → 강제
        assertThat(active.isOwnEnabled()).isTrue();        // own 보존
        assertThat(opDeprecated.isEnabled()).isFalse();
        assertThat(opDeprecated.isOwnDeprecated()).isTrue(); // own_dep 보존
        assertThat(bmcActive.isEnabled()).isFalse();
        assertThat(bmcDeleted.isEnabled()).isTrue();        // soft-deleted → cascade 제외, 그대로
    }

    @Test
    @DisplayName("R4-1 toggle on (양방향) : 부모 활성 → own_enabled=true 자식만 복원, own_enabled=false(직접 비활성) 자식 보존.")
    void toggleOn_recomputesChildren_bidirectional() {
        BoardModel p = parent(false, false);   // 부모 비활성
        BoardBIOS ownEnabled = bios(101L, p, true, false, false);    // own_en=true → 현재 부모비활성으로 effective 비활성
        BoardBIOS ownDisabled = bios(102L, p, false, false, false);  // own_en=false (직접 비활성)
        given(boardModelRepository.findByIdAndIsDeletedFalse(7L)).willReturn(Optional.of(p));
        given(biosRepository.findAllByBoardModel_IdOrderByVersionDesc(7L))
                .willReturn(List.of(ownEnabled, ownDisabled));

        boardModelService.toggleEnabled(7L);   // 부모 비활성 → 활성

        assertThat(p.isEnabled()).isTrue();
        assertThat(ownEnabled.isEnabled()).isTrue();    // own_en=true → 부모 따라 복원 (양방향)
        assertThat(ownDisabled.isEnabled()).isFalse();  // own_en=false → 보존
    }

    @Test
    @DisplayName("R4-1 deprecate : 부모 deprecate → 전 자식 effective 강제 deprecated (own 보존).")
    void deprecate_forcesAllChildrenEffectiveDeprecated() {
        BoardModel p = parent(true, false);
        BoardBIOS a = bios(101L, p, true, false, false);  // own active
        BoardBIOS b = bios(102L, p, true, true, false);   // own deprecated
        BoardBMC bmcA = bmc(201L, p, true, false, false);
        given(boardModelRepository.findByIdAndIsDeletedFalse(7L)).willReturn(Optional.of(p));
        given(biosRepository.findAllByBoardModel_IdOrderByVersionDesc(7L)).willReturn(List.of(a, b));
        given(bmcRepository.findAllByBoardModel_IdOrderByVersionDesc(7L)).willReturn(List.of(bmcA));

        boardModelService.deprecate(7L);

        assertThat(p.isDeprecated()).isTrue();
        assertThat(p.isEnabled()).isTrue();           // 부모 enabled 유지 (deprecate 는 enabled 무관)
        assertThat(a.isDeprecated()).isTrue();        // own_dep=false 이나 부모 deprecated → 강제
        assertThat(a.isOwnDeprecated()).isFalse();    // own 보존
        assertThat(a.isEnabled()).isTrue();           // ★ 차원 독립 : 부모 deprecate 가 자식 enabled 를 끄지 않음
        assertThat(b.isDeprecated()).isTrue();
        assertThat(bmcA.isDeprecated()).isTrue();
    }

    @Test
    @DisplayName("R4-1 ★ force-down-while-explicit : 부모 deprecate→undeprecate 사이클서 own=active 자식 복원, own=deprecated(운영자) 자식 보존.")
    void undeprecate_recoversOwnActive_preservesOwnDeprecated() {
        BoardModel p = parent(true, false);
        BoardBIOS a = bios(101L, p, true, false, false);  // own active (직접 활성 관리)
        BoardBIOS b = bios(102L, p, true, true, false);   // own deprecated (운영자가 직접 deprecate)
        BoardBMC bmcA = bmc(201L, p, true, false, false); // own active
        given(boardModelRepository.findByIdAndIsDeletedFalse(7L)).willReturn(Optional.of(p));
        given(biosRepository.findAllByBoardModel_IdOrderByVersionDesc(7L)).willReturn(List.of(a, b));
        given(bmcRepository.findAllByBoardModel_IdOrderByVersionDesc(7L)).willReturn(List.of(bmcA));

        // ① 부모 deprecate → 전 자식 강제 deprecated
        boardModelService.deprecate(7L);
        assertThat(a.isDeprecated()).isTrue();
        assertThat(b.isDeprecated()).isTrue();
        assertThat(bmcA.isDeprecated()).isTrue();

        // ② 부모 undeprecate → own=active 복원, own=deprecated 보존 (구 결함 = b 도 환원됐었음)
        boardModelService.undeprecate(7L);
        assertThat(p.isDeprecated()).isFalse();
        assertThat(a.isDeprecated()).isFalse();    // ★ own active → 복원
        assertThat(b.isDeprecated()).isTrue();     // ★ own deprecated → 보존
        assertThat(bmcA.isDeprecated()).isFalse(); // own active → 복원
    }

    // ──────────────────────────────────────────────────────────────
    // R3-1 — board-scoped Subprogram cascade parity (공용은 boardModel.id 쿼리로 자연 제외)
    // ──────────────────────────────────────────────────────────────

    private Subprogram subprogram(Long id, BoardModel parent, boolean ownEnabled, boolean ownDeprecated, boolean deleted) {
        Subprogram s = Subprogram.builder()
                .id(id).kind(SubprogramKind.DRIVER).boardModel(parent)
                .name("drv-" + id).version("1." + id).treeRootPath("/sp/" + id)
                .manifestHash("h").fileCount(1).totalBytes(1L)
                .ownEnabled(ownEnabled).ownDeprecated(ownDeprecated).isDeleted(deleted)
                .build();
        s.recomputeEffective();
        return s;
    }

    private BoardModel deletedParent() {
        return BoardModel.builder().id(7L).vendor(Vendor.ASUS).modelName("P13R-E")
                .ownEnabled(true).ownDeprecated(false).isDeleted(true).build();
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
    @DisplayName("R4-1 undeprecate : 부모 undeprecate → 부모가 강제 deprecated 했던(own active) board-scoped Subprogram 복원")
    void undeprecate_recoversBoardScopedSubprogram() {
        BoardModel p = parent(true, true);                          // 부모 deprecated
        Subprogram forced = subprogram(301L, p, true, false, false); // own active → 부모 deprecated 라 effective deprecated(강제)
        given(boardModelRepository.findByIdAndIsDeletedFalse(7L)).willReturn(Optional.of(p));
        given(biosRepository.findAllByBoardModel_IdOrderByVersionDesc(7L)).willReturn(List.of());
        given(bmcRepository.findAllByBoardModel_IdOrderByVersionDesc(7L)).willReturn(List.of());
        given(subprogramRepository.findAllByBoardModel_Id(7L)).willReturn(List.of(forced));

        assertThat(forced.isDeprecated()).isTrue();   // 강제된 상태 확인
        boardModelService.undeprecate(7L);

        assertThat(forced.isDeprecated()).isFalse();  // own active → 부모 해제 시 복원
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
