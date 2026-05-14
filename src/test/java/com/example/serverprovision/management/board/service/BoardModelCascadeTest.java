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
import com.example.serverprovision.management.common.nudge.NudgeRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

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
    @DisplayName("toggle off : 부모 비활성 → 자식 BIOS / BMC 모두 비활성. deprecated/deleted 자식은 skip.")
    void toggleOff_cascadesBothChildTypes() {
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
        assertThat(biosActive.isEnabled()).isFalse();      // cascade
        assertThat(biosDeprecated.isEnabled()).isTrue();   // skip
        assertThat(bmcActive.isEnabled()).isFalse();       // cascade
        assertThat(bmcDeleted.isEnabled()).isTrue();       // skip — deleted
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
}
