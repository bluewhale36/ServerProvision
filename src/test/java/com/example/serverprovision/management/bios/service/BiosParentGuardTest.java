package com.example.serverprovision.management.bios.service;

import com.example.serverprovision.global.exception.ChildLifecycleBlockedByParentException;
import com.example.serverprovision.global.marker.service.ProvisionMarkerService;
import com.example.serverprovision.management.bios.entity.BoardBIOS;
import com.example.serverprovision.management.bios.repository.BiosRepository;
import com.example.serverprovision.management.board.entity.BoardModel;
import com.example.serverprovision.management.board.enums.Vendor;
import com.example.serverprovision.management.board.repository.BoardModelRepository;
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
 * S5-2-3-1 — BIOS 자식 단독 lifecycle 의 부모 가드 단위 테스트.
 */
@ExtendWith(MockitoExtension.class)
class BiosParentGuardTest {

    @Mock BiosRepository biosRepository;
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
    @InjectMocks BiosService biosService;

    private BoardModel parent(boolean enabled, boolean deprecated, boolean deleted) {
        return BoardModel.builder()
                .id(2L).vendor(Vendor.ASUS).modelName("P13R-E")
                .isEnabled(enabled).isDeprecated(deprecated).isDeleted(deleted)
                .build();
    }

    private BoardBIOS bios(BoardModel parent, boolean enabled, boolean deprecated, boolean deleted) {
        return BoardBIOS.builder()
                .id(5L).boardModel(parent).name("R23_MS73-HB1_Uni").version("1.0")
                .isEnabled(enabled).isDeprecated(deprecated).isDeleted(deleted)
                .build();
    }

    @Test
    @DisplayName("BIOS toggle enable 시도 — 부모 Board disabled 면 거절")
    void toggleEnable_parentDisabled_rejects() {
        BoardModel p = parent(false, false, false);
        BoardBIOS b = bios(p, false, false, false);
        given(biosRepository.findByIdAndBoardModel_Id(5L, 2L)).willReturn(Optional.of(b));
        given(boardModelRepository.findByIdAndIsDeletedFalse(2L)).willReturn(Optional.of(p));
        given(boardModelRepository.findById(2L)).willReturn(Optional.of(p));

        assertThatThrownBy(() -> biosService.toggleEnabled(2L, 5L))
                .isInstanceOf(ChildLifecycleBlockedByParentException.class)
                .extracting("parentState").isEqualTo("DISABLED");
    }

    @Test
    @DisplayName("BIOS undeprecate 시도 — 부모 Board deprecated 면 거절")
    void undeprecate_parentDeprecated_rejects() {
        BoardModel p = parent(true, true, false);
        given(boardModelRepository.findById(2L)).willReturn(Optional.of(p));

        assertThatThrownBy(() -> biosService.undeprecate(2L, 5L))
                .isInstanceOf(ChildLifecycleBlockedByParentException.class)
                .extracting("parentState").isEqualTo("DEPRECATED");
    }

    @Test
    @DisplayName("BIOS restore 시도 — 부모 Board deleted 면 거절")
    void restore_parentDeleted_rejects() {
        BoardModel p = parent(true, false, true);
        given(boardModelRepository.findById(2L)).willReturn(Optional.of(p));

        assertThatThrownBy(() -> biosService.restore(2L, 5L))
                .isInstanceOf(ChildLifecycleBlockedByParentException.class)
                .extracting("parentState").isEqualTo("DELETED");
    }
}
