package com.example.serverprovision.management.board.service.metadata;

import com.example.serverprovision.global.exception.TypedNameMismatchException;
import com.example.serverprovision.management.bios.entity.BoardBIOS;
import com.example.serverprovision.management.bios.repository.BiosRepository;
import com.example.serverprovision.management.bios.service.BiosService;
import com.example.serverprovision.management.bmc.entity.BoardBMC;
import com.example.serverprovision.management.bmc.repository.BmcRepository;
import com.example.serverprovision.management.bmc.service.BmcService;
import com.example.serverprovision.management.board.entity.BoardModel;
import com.example.serverprovision.management.board.enums.Vendor;
import com.example.serverprovision.management.board.exception.DuplicateBoardModelException;
import com.example.serverprovision.management.board.exception.IllegalBoardModelStateException;
import com.example.serverprovision.management.board.repository.BoardModelRepository;
import com.example.serverprovision.management.common.dto.response.RestoreResponse;
import com.example.serverprovision.management.subprogram.entity.Subprogram;
import com.example.serverprovision.management.subprogram.enums.SubprogramKind;
import com.example.serverprovision.management.subprogram.exception.DuplicateSubprogramVersionException;
import com.example.serverprovision.management.subprogram.repository.SubprogramRepository;
import com.example.serverprovision.management.subprogram.service.SubprogramService;
import com.example.serverprovision.management.subprogram.vo.BoardScope;
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
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * R3-3 — {@link BoardModelLifecycleService} 단위 테스트.
 *
 * <p>구 {@code BoardModelCascadeTest}(부모 lifecycle cascade)와 {@code BoardModelServiceTest} 의
 * softDelete cascade 시나리오를 흡수했다. 자식 service 가 생성자 {@code @Lazy} 주입이라 {@code @InjectMocks}
 * 대신 mock 을 직접 넘겨 인스턴스를 조립한다(OS 의 {@code OSMetadataCascadeTest} 와 동일 패턴).</p>
 */
@ExtendWith(MockitoExtension.class)
class BoardModelLifecycleServiceTest {

    @Mock BoardModelRepository boardModelRepository;
    @Mock BiosRepository biosRepository;
    @Mock BmcRepository bmcRepository;
    @Mock SubprogramRepository subprogramRepository;
    @Mock BiosService biosService;
    @Mock BmcService bmcService;
    @Mock SubprogramService subprogramService;

    BoardModelLifecycleService boardModelService;

    @BeforeEach
    void initService() {
        boardModelService = new BoardModelLifecycleService(
                boardModelRepository, biosRepository, bmcRepository, subprogramRepository,
                biosService, bmcService, subprogramService);
    }

    // ==== helper =====================================================

    // R4-1 — own(운영자 의도) 으로 빌드 후 recomputeEffective() 로 초기 effective = own ⊕ 부모 설정.
    private BoardModel parent(boolean enabled, boolean deprecated) {
        BoardModel p = BoardModel.builder()
                .id(7L).vendor(Vendor.ASUS).modelName("P13R-E")
                .ownEnabled(enabled).ownDeprecated(deprecated).isDeleted(false)
                .build();
        p.recomputeEffective();
        return p;
    }

    private BoardModel deletedParent() {
        return BoardModel.builder().id(7L).vendor(Vendor.ASUS).modelName("P13R-E")
                .ownEnabled(true).ownDeprecated(false).isDeleted(true).build();
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

    // ==== toggleEnabled cascade ======================================

    @Test
    @DisplayName("R4-1 toggle off : 부모 비활성 → 비삭제 자식 effective 비활성(own 보존). soft-deleted 자식은 cascade 제외.")
    void toggleOff_recomputesNonDeletedChildrenDisabled() {
        BoardModel p = parent(true, false);
        BoardBIOS active = bios(101L, p, true, false, false);
        BoardBIOS opDeprecated = bios(102L, p, true, true, false);
        BoardBMC bmcActive = bmc(201L, p, true, false, false);
        BoardBMC bmcDeleted = bmc(202L, p, true, false, true);
        given(boardModelRepository.findByIdAndIsDeletedFalse(7L)).willReturn(Optional.of(p));
        given(biosRepository.findAllByBoardModel_IdOrderByVersionDesc(7L))
                .willReturn(List.of(active, opDeprecated));
        given(bmcRepository.findAllByBoardModel_IdOrderByVersionDesc(7L))
                .willReturn(List.of(bmcActive, bmcDeleted));
        given(subprogramRepository.findAllByBoardModel_Id(7L)).willReturn(List.of());

        boardModelService.toggleEnabled(7L);

        assertThat(p.isEnabled()).isFalse();
        assertThat(active.isEnabled()).isFalse();
        assertThat(active.isOwnEnabled()).isTrue();
        assertThat(opDeprecated.isEnabled()).isFalse();
        assertThat(opDeprecated.isOwnDeprecated()).isTrue();
        assertThat(bmcActive.isEnabled()).isFalse();
        assertThat(bmcDeleted.isEnabled()).isTrue();   // soft-deleted → cascade 제외
    }

    @Test
    @DisplayName("R4-1 toggle on (양방향) : 부모 활성 → own_enabled=true 자식만 복원, own_enabled=false 자식 보존.")
    void toggleOn_recomputesChildren_bidirectional() {
        BoardModel p = parent(false, false);
        BoardBIOS ownEnabled = bios(101L, p, true, false, false);
        BoardBIOS ownDisabled = bios(102L, p, false, false, false);
        given(boardModelRepository.findByIdAndIsDeletedFalse(7L)).willReturn(Optional.of(p));
        given(biosRepository.findAllByBoardModel_IdOrderByVersionDesc(7L))
                .willReturn(List.of(ownEnabled, ownDisabled));
        given(bmcRepository.findAllByBoardModel_IdOrderByVersionDesc(7L)).willReturn(List.of());
        given(subprogramRepository.findAllByBoardModel_Id(7L)).willReturn(List.of());

        boardModelService.toggleEnabled(7L);

        assertThat(p.isEnabled()).isTrue();
        assertThat(ownEnabled.isEnabled()).isTrue();
        assertThat(ownDisabled.isEnabled()).isFalse();
    }

    @Test
    @DisplayName("R3-1 toggle off : 부모 비활성 → board-scoped Subprogram(enabled) 동반 비활성")
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

    // ==== deprecate / undeprecate cascade ============================

    @Test
    @DisplayName("R4-1 deprecate : 부모 deprecate → 전 자식 effective 강제 deprecated (own 보존, enabled 무관).")
    void deprecate_forcesAllChildrenEffectiveDeprecated() {
        BoardModel p = parent(true, false);
        BoardBIOS a = bios(101L, p, true, false, false);
        BoardBIOS b = bios(102L, p, true, true, false);
        BoardBMC bmcA = bmc(201L, p, true, false, false);
        Subprogram sp = subprogram(301L, p, true, false, false);
        given(boardModelRepository.findByIdAndIsDeletedFalse(7L)).willReturn(Optional.of(p));
        given(biosRepository.findAllByBoardModel_IdOrderByVersionDesc(7L)).willReturn(List.of(a, b));
        given(bmcRepository.findAllByBoardModel_IdOrderByVersionDesc(7L)).willReturn(List.of(bmcA));
        given(subprogramRepository.findAllByBoardModel_Id(7L)).willReturn(List.of(sp));

        boardModelService.deprecate(7L);

        assertThat(p.isDeprecated()).isTrue();
        assertThat(p.isEnabled()).isTrue();          // deprecate 는 enabled 무관
        assertThat(a.isDeprecated()).isTrue();
        assertThat(a.isOwnDeprecated()).isFalse();   // own 보존
        assertThat(a.isEnabled()).isTrue();          // ★ 차원 독립
        assertThat(b.isDeprecated()).isTrue();
        assertThat(bmcA.isDeprecated()).isTrue();
        assertThat(sp.isDeprecated()).isTrue();
    }

    @Test
    @DisplayName("R4-1 ★ force-down-while-explicit : 부모 deprecate→undeprecate 사이클서 own=active 자식 복원, own=deprecated(운영자) 자식 보존.")
    void undeprecate_recoversOwnActive_preservesOwnDeprecated() {
        BoardModel p = parent(true, false);
        BoardBIOS a = bios(101L, p, true, false, false);   // own active
        BoardBIOS b = bios(102L, p, true, true, false);    // own deprecated (운영자)
        BoardBMC bmcA = bmc(201L, p, true, false, false);
        Subprogram sp = subprogram(301L, p, true, false, false);
        given(boardModelRepository.findByIdAndIsDeletedFalse(7L)).willReturn(Optional.of(p));
        given(biosRepository.findAllByBoardModel_IdOrderByVersionDesc(7L)).willReturn(List.of(a, b));
        given(bmcRepository.findAllByBoardModel_IdOrderByVersionDesc(7L)).willReturn(List.of(bmcA));
        given(subprogramRepository.findAllByBoardModel_Id(7L)).willReturn(List.of(sp));

        boardModelService.deprecate(7L);
        assertThat(a.isDeprecated()).isTrue();
        assertThat(b.isDeprecated()).isTrue();
        assertThat(bmcA.isDeprecated()).isTrue();
        assertThat(sp.isDeprecated()).isTrue();

        boardModelService.undeprecate(7L);
        assertThat(p.isDeprecated()).isFalse();
        assertThat(a.isDeprecated()).isFalse();    // own active → 복원
        assertThat(b.isDeprecated()).isTrue();     // own deprecated → 보존
        assertThat(bmcA.isDeprecated()).isFalse();
        assertThat(sp.isDeprecated()).isFalse();   // own active → 복원
    }

    // ==== softDelete cascade =========================================

    @Test
    @DisplayName("softDelete : 부모 + 활성 자식 BIOS/BMC/Subprogram service.softDelete 위임 검증")
    void softDelete_cascadesToActiveChildren() {
        BoardModel board = parent(true, false);
        given(boardModelRepository.findByIdAndIsDeletedFalse(7L)).willReturn(Optional.of(board));
        BoardBIOS activeBios = bios(101L, board, true, false, false);
        BoardBMC activeBmc = bmc(201L, board, true, false, false);
        Subprogram sp = subprogram(301L, board, true, false, false);
        given(biosRepository.findAllByBoardModel_IdAndIsDeletedFalseOrderByVersionDesc(7L))
                .willReturn(List.of(activeBios));
        given(bmcRepository.findAllByBoardModel_IdAndIsDeletedFalseOrderByVersionDesc(7L))
                .willReturn(List.of(activeBmc));
        given(subprogramRepository.findAllByBoardModel_IdAndIsDeletedFalse(7L))
                .willReturn(List.of(sp));

        boardModelService.softDelete(7L);

        assertThat(board.isDeleted()).isTrue();
        verify(biosService).softDelete(7L, 101L);
        verify(bmcService).softDelete(7L, 201L);
        verify(subprogramService).softDelete(301L);   // 단일 인자
    }

    // ==== restore ====================================================

    @Test
    @DisplayName("restore(cascade=false) : 부모만 복구 → RestoreResponse.none, 자식 service 미호출")
    void restore_noCascade_returnsNone() {
        BoardModel p = deletedParent();
        given(boardModelRepository.findByIdAndIsDeletedTrue(7L)).willReturn(Optional.of(p));
        given(boardModelRepository.existsByVendorAndModelNameAndIsDeletedFalse(Vendor.ASUS, "P13R-E"))
                .willReturn(false);

        RestoreResponse response = boardModelService.restore(7L, false);

        assertThat(response.cascadedChildren()).isZero();
        assertThat(p.isDeleted()).isFalse();
        verify(biosService, never()).restore(org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.anyLong());
        verify(subprogramService, never()).restore(org.mockito.ArgumentMatchers.anyLong());
    }

    @Test
    @DisplayName("restore(cascade=true) : board.restore 後 deleted 자식 service.restore 위임 + 건수 집계")
    void restore_cascadeTrue_restoresChildren() {
        BoardModel p = deletedParent();
        BoardBIOS deletedBios = bios(101L, p, true, false, true);
        BoardBMC deletedBmc = bmc(201L, p, true, false, true);
        Subprogram sp = subprogram(301L, p, true, false, true);
        given(boardModelRepository.findByIdAndIsDeletedTrue(7L)).willReturn(Optional.of(p));
        given(boardModelRepository.existsByVendorAndModelNameAndIsDeletedFalse(Vendor.ASUS, "P13R-E"))
                .willReturn(false);
        given(biosRepository.findAllByBoardModel_IdAndIsDeletedTrue(7L)).willReturn(List.of(deletedBios));
        given(bmcRepository.findAllByBoardModel_IdAndIsDeletedTrue(7L)).willReturn(List.of(deletedBmc));
        given(subprogramRepository.findAllByBoardModel_IdAndIsDeletedTrue(7L)).willReturn(List.of(sp));

        RestoreResponse response = boardModelService.restore(7L, true);

        assertThat(p.isDeleted()).isFalse();
        assertThat(response.cascadedChildren()).isEqualTo(3);
        verify(biosService).restore(7L, 101L);
        verify(bmcService).restore(7L, 201L);
        verify(subprogramService).restore(301L);
    }

    @Test
    @DisplayName("restore : 복구하려는 (vendor, modelName) 이 이미 활성 → DuplicateBoardModelException")
    void restore_whenActiveKeyExists_throwsDuplicate() {
        BoardModel p = deletedParent();
        given(boardModelRepository.findByIdAndIsDeletedTrue(7L)).willReturn(Optional.of(p));
        given(boardModelRepository.existsByVendorAndModelNameAndIsDeletedFalse(Vendor.ASUS, "P13R-E"))
                .willReturn(true);

        assertThatThrownBy(() -> boardModelService.restore(7L, true))
                .isInstanceOf(DuplicateBoardModelException.class);
    }

    @Test
    @DisplayName("restore(cascade=true) : 자식 Subprogram 활성 동일키 충돌 → DuplicateSubprogramVersionException 전파(전체 롤백)")
    void restoreCascade_subprogramConflict_propagates() {
        BoardModel p = deletedParent();
        Subprogram sp = subprogram(301L, p, true, false, true);
        given(boardModelRepository.findByIdAndIsDeletedTrue(7L)).willReturn(Optional.of(p));
        given(boardModelRepository.existsByVendorAndModelNameAndIsDeletedFalse(Vendor.ASUS, "P13R-E"))
                .willReturn(false);
        given(biosRepository.findAllByBoardModel_IdAndIsDeletedTrue(7L)).willReturn(List.of());
        given(bmcRepository.findAllByBoardModel_IdAndIsDeletedTrue(7L)).willReturn(List.of());
        given(subprogramRepository.findAllByBoardModel_IdAndIsDeletedTrue(7L)).willReturn(List.of(sp));
        willThrow(new DuplicateSubprogramVersionException(SubprogramKind.DRIVER, BoardScope.ofBoard(7L), "drv-301", "1.301"))
                .given(subprogramService).restore(301L);

        assertThatThrownBy(() -> boardModelService.restore(7L, true))
                .isInstanceOf(DuplicateSubprogramVersionException.class);
    }

    // ==== purge ======================================================

    @Test
    @DisplayName("purge : 자식 BIOS/BMC/Subprogram 0 → 영구 삭제 허용")
    void purge_okWhenChildless() {
        BoardModel p = deletedParent();
        given(boardModelRepository.findByIdAndIsDeletedTrue(7L)).willReturn(Optional.of(p));
        given(biosRepository.findAllByBoardModel_IdOrderByVersionDesc(7L)).willReturn(List.of());
        given(bmcRepository.findAllByBoardModel_IdOrderByVersionDesc(7L)).willReturn(List.of());
        given(subprogramRepository.findAllByBoardModel_Id(7L)).willReturn(List.of());

        boardModelService.purge(7L);

        verify(boardModelRepository).delete(p);
    }

    @Test
    @DisplayName("purge : 자식 Subprogram 잔존 시 영구삭제 거절 → IllegalBoardModelStateException")
    void purge_blockedByRemainingSubprogram() {
        BoardModel p = deletedParent();
        given(boardModelRepository.findByIdAndIsDeletedTrue(7L)).willReturn(Optional.of(p));
        given(biosRepository.findAllByBoardModel_IdOrderByVersionDesc(7L)).willReturn(List.of());
        given(bmcRepository.findAllByBoardModel_IdOrderByVersionDesc(7L)).willReturn(List.of());
        given(subprogramRepository.findAllByBoardModel_Id(7L))
                .willReturn(List.of(subprogram(301L, p, true, false, true)));

        assertThatThrownBy(() -> boardModelService.purge(7L))
                .isInstanceOf(IllegalBoardModelStateException.class);
        verify(boardModelRepository, never()).delete(p);
    }

    // ==== purgeWithTypedNameCheck ====================================

    @Test
    @DisplayName("purgeWithTypedNameCheck(happy) : typedName 일치(displayName) → 영구 삭제")
    void purgeWithTypedNameCheck_match_deletes() {
        BoardModel p = deletedParent();
        given(boardModelRepository.findByIdAndIsDeletedTrue(7L)).willReturn(Optional.of(p));
        given(biosRepository.findAllByBoardModel_IdOrderByVersionDesc(7L)).willReturn(List.of());
        given(bmcRepository.findAllByBoardModel_IdOrderByVersionDesc(7L)).willReturn(List.of());
        given(subprogramRepository.findAllByBoardModel_Id(7L)).willReturn(List.of());

        // displayName = vendor.displayName + " " + modelName = "Asus P13R-E"
        boardModelService.purgeWithTypedNameCheck(7L, "Asus P13R-E");

        verify(boardModelRepository).delete(p);
    }

    @Test
    @DisplayName("purgeWithTypedNameCheck(mismatch) : typedName 불일치 → TypedNameMismatchException")
    void purgeWithTypedNameCheck_mismatch_throws() {
        BoardModel p = deletedParent();
        given(boardModelRepository.findByIdAndIsDeletedTrue(7L)).willReturn(Optional.of(p));

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

    // ==== findDeletedChildLabels =====================================

    @Test
    @DisplayName("findDeletedChildLabels : soft-deleted BIOS/BMC/Subprogram 라벨을 종류별 접두로 수집")
    void findDeletedChildLabels_collectsLabels() {
        BoardModel p = deletedParent();
        BoardBIOS deletedBios = bios(101L, p, true, false, true);
        BoardBMC deletedBmc = bmc(201L, p, true, false, true);
        Subprogram sp = subprogram(301L, p, true, false, true);
        given(biosRepository.findAllByBoardModel_IdAndIsDeletedTrue(7L)).willReturn(List.of(deletedBios));
        given(bmcRepository.findAllByBoardModel_IdAndIsDeletedTrue(7L)).willReturn(List.of(deletedBmc));
        given(subprogramRepository.findAllByBoardModel_IdAndIsDeletedTrue(7L)).willReturn(List.of(sp));

        List<String> labels = boardModelService.findDeletedChildLabels(7L);

        assertThat(labels).hasSize(3);
        assertThat(labels).anyMatch(l -> l.startsWith("BIOS: "));
        assertThat(labels).anyMatch(l -> l.startsWith("BMC: "));
        assertThat(labels).anyMatch(l -> l.contains(SubprogramKind.DRIVER.getDisplayName() + ": "));
    }
}
