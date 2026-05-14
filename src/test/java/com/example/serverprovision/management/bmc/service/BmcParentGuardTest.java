package com.example.serverprovision.management.bmc.service;

import com.example.serverprovision.global.exception.ChildLifecycleBlockedByParentException;
import com.example.serverprovision.global.marker.service.ProvisionMarkerService;
import com.example.serverprovision.management.bios.service.BundleManifestService;
import com.example.serverprovision.management.bmc.entity.BoardBMC;
import com.example.serverprovision.management.bmc.repository.BmcRepository;
import com.example.serverprovision.management.board.entity.BoardModel;
import com.example.serverprovision.management.board.enums.Vendor;
import com.example.serverprovision.management.board.repository.BoardModelRepository;
import com.example.serverprovision.management.bios.service.BundleEntrypointDetector;
import com.example.serverprovision.management.bios.service.BundleExtractionService;
import com.example.serverprovision.management.common.filesystem.service.TargetDirectoryPolicyService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

/**
 * S5-2-3-1 — BMC 자식 단독 lifecycle 의 부모 가드 단위 테스트.
 */
@ExtendWith(MockitoExtension.class)
class BmcParentGuardTest {

    @Mock BmcRepository bmcRepository;
    @Mock BoardModelRepository boardModelRepository;
    @Mock BundleExtractionService bundleExtractionService;
    @Mock BundleEntrypointDetector bundleEntrypointDetector;
    @Mock BundleManifestService bundleManifestService;
    @Mock ProvisionMarkerService provisionMarkerService;
    @Mock TargetDirectoryPolicyService targetDirectoryPolicyService;
    @Mock com.example.serverprovision.management.common.filesystem.service.BundleTreeCleanupService bundleTreeCleanupService;
    @Mock com.example.serverprovision.global.security.PathPolicyService pathPolicyService;
    @Mock com.example.serverprovision.global.trash.TrashLifecycleService trashLifecycleService;
    @Mock com.example.serverprovision.global.lifecycle.SoftDeleteIntentService softDeleteIntentService;
    @InjectMocks BmcService bmcService;

    private BoardModel parent(boolean enabled, boolean deprecated, boolean deleted) {
        return BoardModel.builder()
                .id(2L).vendor(Vendor.GIGABYTE).modelName("MZ32-AR0")
                .isEnabled(enabled).isDeprecated(deprecated).isDeleted(deleted)
                .build();
    }

    private BoardBMC bmc(BoardModel parent, boolean enabled, boolean deprecated, boolean deleted) {
        return BoardBMC.builder()
                .id(5L).boardModel(parent).name("AST2600").version("12.61")
                .treeRootPath("/fw/bmc").legacyFilePath("/fw/bmc").boardModelIdMirror(2L)
                .entrypointRelativePath("flash.nsh")
                .manifestHash("hash").markerSignature("sig").fileCount(2).totalBytes(128L)
                .isEnabled(enabled).isDeprecated(deprecated).isDeleted(deleted)
                .build();
    }

    @Test
    @DisplayName("BMC toggle enable 시도 — 부모 Board disabled 면 거절")
    void toggleEnable_parentDisabled_rejects() {
        BoardModel p = parent(false, false, false);
        BoardBMC b = bmc(p, false, false, false);
        given(bmcRepository.findByIdAndBoardModel_Id(5L, 2L)).willReturn(Optional.of(b));
        given(boardModelRepository.findByIdAndIsDeletedFalse(2L)).willReturn(Optional.of(p));
        given(boardModelRepository.findById(2L)).willReturn(Optional.of(p));

        assertThatThrownBy(() -> bmcService.toggleEnabled(2L, 5L))
                .isInstanceOf(ChildLifecycleBlockedByParentException.class)
                .extracting("parentState").isEqualTo("DISABLED");
    }

    @Test
    @DisplayName("BMC undeprecate 시도 — 부모 Board deprecated 면 거절")
    void undeprecate_parentDeprecated_rejects() {
        BoardModel p = parent(true, true, false);
        given(boardModelRepository.findById(2L)).willReturn(Optional.of(p));

        assertThatThrownBy(() -> bmcService.undeprecate(2L, 5L))
                .isInstanceOf(ChildLifecycleBlockedByParentException.class)
                .extracting("parentState").isEqualTo("DEPRECATED");
    }

    @Test
    @DisplayName("BMC restore 시도 — 부모 Board deleted 면 거절")
    void restore_parentDeleted_rejects() {
        BoardModel p = parent(true, false, true);
        given(boardModelRepository.findById(2L)).willReturn(Optional.of(p));

        assertThatThrownBy(() -> bmcService.restore(2L, 5L))
                .isInstanceOf(ChildLifecycleBlockedByParentException.class)
                .extracting("parentState").isEqualTo("DELETED");
    }
}
