package com.example.serverprovision.management.os.service.metadata;

import com.example.serverprovision.global.exception.ChildLifecycleBlockedByParentException;
import com.example.serverprovision.management.os.entity.ISO;
import com.example.serverprovision.management.os.entity.OSMetadata;
import com.example.serverprovision.management.os.enums.OSName;
import com.example.serverprovision.management.os.exception.ISONotFoundException;
import com.example.serverprovision.management.os.exception.IllegalOSMetadataStateException;
import com.example.serverprovision.management.os.repository.ISORepository;
import com.example.serverprovision.management.os.repository.OSMetadataRepository;
import com.example.serverprovision.global.lifecycle.SoftDeleteIntentService;
import com.example.serverprovision.global.trash.TrashLifecycleService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

/**
 * S5-2-3-1 — OS 부모 lifecycle cascade + 자식 단독 부모 가드 단위 테스트.
 *
 * <p>plan §6 의 시나리오를 plan §UX 미리보기의 모든 분기까지 전수 검증.</p>
 */
@ExtendWith(MockitoExtension.class)
class OSMetadataCascadeTest {

    @Mock OSMetadataRepository osMetadataRepository;
    @Mock ISORepository isoRepository;
    @Mock TrashLifecycleService trashLifecycleService;
    com.example.serverprovision.management.os.service.iso.IsoLifecycleService isoLifecycleService;
    @Mock SoftDeleteIntentService softDeleteIntentService;
    @Mock com.example.serverprovision.global.marker.service.ProvisionMarkerService markerService;

    @InjectMocks OSMetadataService osMetadataService;

    OSMetadataLifecycleService osMetadataLifecycleService;

    @BeforeEach
    void initLifecycleService() {
        isoLifecycleService = new com.example.serverprovision.management.os.service.iso.IsoLifecycleService(
                isoRepository, osMetadataRepository, trashLifecycleService, softDeleteIntentService, markerService);
        osMetadataLifecycleService = new OSMetadataLifecycleService(
                osMetadataRepository, trashLifecycleService, isoLifecycleService);
    }

    // ==== helper ===================================================

    private OSMetadata buildParent(boolean enabled, boolean deprecated, boolean deleted, List<ISO> isos) {
        OSMetadata parent = OSMetadata.builder()
                .id(1L)
                .osName(OSName.ROCKY_LINUX)
                .osVersion("9.5")
                .isEnabled(enabled)
                .isDeprecated(deprecated)
                .isDeleted(deleted)
                .isos(isos)
                .build();
        return parent;
    }

    private ISO buildChild(Long id, OSMetadata parent, boolean enabled, boolean deprecated, boolean deleted) {
        ISO iso = ISO.builder()
                .id(id)
                .osMetadata(parent)
                .isoPath("/opt/iso/rocky-9.5-" + id + ".iso")
                .isEnabled(enabled)
                .isDeprecated(deprecated)
                .isDeleted(deleted)
                .build();
        return iso;
    }

    // ==== 부모 toggleEnabled cascade ================================

    @Test
    @DisplayName("HF-2 toggle off : 부모 → 비활성. enabled 자식 전부(active + deprecated + soft-deleted) 비활성 동기화 (비대칭 disable).")
    void toggleOff_disablesAllEnabledChildren_includingTrashedAndDeprecated() {
        ISO active = buildChild(101L, null, true, false, false);
        ISO deprecated = buildChild(102L, null, true, true, false);
        ISO deleted = buildChild(103L, null, true, false, true);
        List<ISO> isos = new ArrayList<>(List.of(active, deprecated, deleted));
        OSMetadata parent = buildParent(true, false, false, isos);
        given(osMetadataRepository.findByIdAndIsDeletedFalse(1L)).willReturn(Optional.of(parent));

        osMetadataLifecycleService.toggleEnabled(1L);

        assertThat(parent.isEnabled()).isFalse();
        assertThat(active.isEnabled()).isFalse();      // 동기화
        assertThat(deprecated.isEnabled()).isFalse();  // HF-2 — deprecated 자식도 동기화 (invariant: 자식 enabled ≤ 부모 enabled)
        assertThat(deleted.isEnabled()).isFalse();     // HF-2 — soft-deleted(trash) 자식도 동기화 → stale 씨앗(Leg A) 차단
    }

    @Test
    @DisplayName("HF-2 toggle on : 부모 → 활성. 자식 cascade 미적용 — active 자식도 disabled 유지 (부모 ceiling 만 상승, 자식 개별 활성은 운영자 몫).")
    void toggleOn_doesNotCascadeToChildren() {
        ISO activeDisabled = buildChild(101L, null, false, false, false);
        ISO trashedDisabled = buildChild(102L, null, false, false, true);
        ISO deprecatedDisabled = buildChild(103L, null, false, true, false);
        OSMetadata parent = buildParent(false, false, false,
                new ArrayList<>(List.of(activeDisabled, trashedDisabled, deprecatedDisabled)));
        given(osMetadataRepository.findByIdAndIsDeletedFalse(1L)).willReturn(Optional.of(parent));

        osMetadataLifecycleService.toggleEnabled(1L);

        assertThat(parent.isEnabled()).isTrue();
        assertThat(activeDisabled.isEnabled()).isFalse();      // 활성화 cascade 안 함 — active 자식도 그대로 disabled
        assertThat(trashedDisabled.isEnabled()).isFalse();
        assertThat(deprecatedDisabled.isEnabled()).isFalse();
    }

    @Test
    @DisplayName("HF-2 핵심 : 부모 disable 시 enabled 인 soft-deleted 자식이 disable → 이후 restore 해도 disabled 보존 (Leg B 모순 미발생).")
    void toggleOff_thenChildStaysDisabledAcrossRestore() {
        ISO trashedEnabled = buildChild(101L, null, true, false, true);  // 모순 씨앗 : soft-deleted 인데 enabled
        OSMetadata parent = buildParent(true, false, false, new ArrayList<>(List.of(trashedEnabled)));
        given(osMetadataRepository.findByIdAndIsDeletedFalse(1L)).willReturn(Optional.of(parent));

        osMetadataLifecycleService.toggleEnabled(1L);                 // 부모 disable
        assertThat(trashedEnabled.isEnabled()).isFalse();            // Leg A 차단 — trash 자식도 disable

        // restore 는 is_deleted 만 되돌리고 is_enabled 는 보존 (LifecycleEntity.restore 계약).
        trashedEnabled.restore();
        assertThat(trashedEnabled.isEnabled()).isFalse();            // 부모 disabled 와 정합 — stale enabled 부활 없음
        assertThat(trashedEnabled.isDeleted()).isFalse();
    }

    // ==== 부모 deprecate cascade ====================================

    @Test
    @DisplayName("deprecate : 부모 active → deprecated. 자식 active 만 deprecate. 이미 deprecated 또는 deleted 자식은 skip.")
    void deprecate_cascadesToActiveChildren_skipsDeprecatedAndDeleted() {
        ISO active = buildChild(101L, null, true, false, false);
        ISO alreadyDeprecated = buildChild(102L, null, true, true, false);
        ISO deleted = buildChild(103L, null, true, false, true);
        OSMetadata parent = buildParent(true, false, false, new ArrayList<>(List.of(active, alreadyDeprecated, deleted)));
        given(osMetadataRepository.findByIdAndIsDeletedFalse(1L)).willReturn(Optional.of(parent));

        osMetadataLifecycleService.deprecate(1L);

        assertThat(parent.isDeprecated()).isTrue();
        assertThat(active.isDeprecated()).isTrue();
        assertThat(alreadyDeprecated.isDeprecated()).isTrue();   // 그대로
        assertThat(deleted.isDeprecated()).isFalse();             // skip
    }

    // ==== 부모 undeprecate cascade ==================================

    @Test
    @DisplayName("undeprecate : 부모 deprecated → active. 자식 deprecated 만 undeprecate. active / deleted 는 skip.")
    void undeprecate_cascadesToDeprecatedChildren() {
        ISO active = buildChild(101L, null, true, false, false);
        ISO deprecated = buildChild(102L, null, true, true, false);
        ISO deletedDeprecated = buildChild(103L, null, true, true, true);
        OSMetadata parent = buildParent(true, true, false, new ArrayList<>(List.of(active, deprecated, deletedDeprecated)));
        given(osMetadataRepository.findByIdAndIsDeletedFalse(1L)).willReturn(Optional.of(parent));

        osMetadataLifecycleService.undeprecate(1L);

        assertThat(parent.isDeprecated()).isFalse();
        assertThat(active.isDeprecated()).isFalse();           // 그대로 active
        assertThat(deprecated.isDeprecated()).isFalse();        // cascade undeprecate
        assertThat(deletedDeprecated.isDeprecated()).isTrue();  // skip — deleted 는 휴지통 잔존
    }

    // R1-4-1 — 자식 ISO lifecycle 단위 시나리오 (toggleIsoEnabled / undeprecateIso / restoreISO)
    // 는 IsoLifecycleServiceTest 로 이동.
}
