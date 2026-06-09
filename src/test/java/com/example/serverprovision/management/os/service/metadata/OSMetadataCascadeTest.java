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

    // R4-1 — 부모 먼저 빌드(effective 설정) → 자식은 osMetadata=parent + parent.isos 에 추가 + recompute.
    private OSMetadata buildParent(boolean enabled, boolean deprecated, boolean deleted) {
        OSMetadata parent = OSMetadata.builder()
                .id(1L)
                .osName(OSName.ROCKY_LINUX)
                .osVersion("9.5")
                .ownEnabled(enabled)
                .ownDeprecated(deprecated)
                .isDeleted(deleted)
                .isos(new ArrayList<>())
                .build();
        parent.recomputeEffective();
        return parent;
    }

    private ISO buildChild(Long id, OSMetadata parent, boolean ownEnabled, boolean ownDeprecated, boolean deleted) {
        ISO iso = ISO.builder()
                .id(id)
                .osMetadata(parent)
                .isoPath("/opt/iso/rocky-9.5-" + id + ".iso")
                .ownEnabled(ownEnabled)
                .ownDeprecated(ownDeprecated)
                .isDeleted(deleted)
                .build();
        iso.recomputeEffective();          // 초기 effective = own ⊕ 부모
        parent.getIsos().add(iso);         // service 가 parent.getIsos() 순회
        return iso;
    }

    // ==== 부모 toggleEnabled cascade ================================

    @Test
    @DisplayName("R4-1 toggle off : 부모 비활성 → 비삭제 자식 effective 비활성(own 보존). soft-deleted 자식은 cascade 제외.")
    void toggleOff_recomputesNonDeletedChildrenDisabled() {
        OSMetadata parent = buildParent(true, false, false);
        ISO active = buildChild(101L, parent, true, false, false);
        ISO opDeprecated = buildChild(102L, parent, true, true, false);  // own deprecated
        ISO deleted = buildChild(103L, parent, true, false, true);       // soft-deleted
        given(osMetadataRepository.findByIdAndIsDeletedFalse(1L)).willReturn(Optional.of(parent));

        osMetadataLifecycleService.toggleEnabled(1L);

        assertThat(parent.isEnabled()).isFalse();
        assertThat(active.isEnabled()).isFalse();           // 강제 비활성
        assertThat(active.isOwnEnabled()).isTrue();         // own 보존
        assertThat(opDeprecated.isEnabled()).isFalse();
        assertThat(opDeprecated.isOwnDeprecated()).isTrue();
        assertThat(deleted.isEnabled()).isTrue();           // soft-deleted → cascade 제외, 그대로
    }

    @Test
    @DisplayName("R4-1 toggle on (양방향) : 부모 활성 → own_enabled=true 자식 복원, own_enabled=false 자식 보존.")
    void toggleOn_recomputesChildren_bidirectional() {
        OSMetadata parent = buildParent(false, false, false);
        ISO ownEnabled = buildChild(101L, parent, true, false, false);   // own_en=true → 현재 부모비활성으로 effective 비활성
        ISO ownDisabled = buildChild(102L, parent, false, false, false); // own_en=false
        given(osMetadataRepository.findByIdAndIsDeletedFalse(1L)).willReturn(Optional.of(parent));

        osMetadataLifecycleService.toggleEnabled(1L);

        assertThat(parent.isEnabled()).isTrue();
        assertThat(ownEnabled.isEnabled()).isTrue();     // own_en=true → 부모 따라 복원 (양방향)
        assertThat(ownDisabled.isEnabled()).isFalse();   // own_en=false → 보존
    }

    @Test
    @DisplayName("R4-1 : soft-deleted 자식(own active)은 부모 disable cascade 제외 → restore 시 부모 기준 재계산으로 disabled (stale enabled 부활 없음).")
    void deletedChild_recomputedToParentOnRestore() {
        OSMetadata parent = buildParent(true, false, false);
        ISO trashed = buildChild(101L, parent, true, false, true);   // soft-deleted, own active
        given(osMetadataRepository.findByIdAndIsDeletedFalse(1L)).willReturn(Optional.of(parent));

        osMetadataLifecycleService.toggleEnabled(1L);                 // 부모 disable
        assertThat(parent.isEnabled()).isFalse();
        assertThat(trashed.isEnabled()).isTrue();                    // deleted → cascade 제외, own 기준 그대로

        trashed.restore();                                           // restore → recompute (현재 부모 disabled 기준)
        assertThat(trashed.isDeleted()).isFalse();
        assertThat(trashed.isEnabled()).isFalse();                   // 부모 disabled → effective disabled (Leg B 부활 없음)
    }

    // ==== 부모 deprecate cascade ====================================

    @Test
    @DisplayName("R4-1 deprecate : 부모 deprecate → 비삭제 자식 effective 강제 deprecated (own 보존). deleted 자식 skip.")
    void deprecate_forcesNonDeletedChildren() {
        OSMetadata parent = buildParent(true, false, false);
        ISO active = buildChild(101L, parent, true, false, false);
        ISO opDeprecated = buildChild(102L, parent, true, true, false);
        ISO deleted = buildChild(103L, parent, true, false, true);
        given(osMetadataRepository.findByIdAndIsDeletedFalse(1L)).willReturn(Optional.of(parent));

        osMetadataLifecycleService.deprecate(1L);

        assertThat(parent.isDeprecated()).isTrue();
        assertThat(active.isDeprecated()).isTrue();              // 강제
        assertThat(active.isOwnDeprecated()).isFalse();          // own 보존
        assertThat(opDeprecated.isDeprecated()).isTrue();
        assertThat(deleted.isDeprecated()).isFalse();            // skip
    }

    // ==== 부모 undeprecate cascade ==================================

    @Test
    @DisplayName("R4-1 ★ force-down-while-explicit : 부모 undeprecate → own active ISO 복원, own deprecated ISO 보존.")
    void undeprecate_recoversOwnActive_preservesOwnDeprecated() {
        OSMetadata parent = buildParent(true, false, false);
        ISO a = buildChild(101L, parent, true, false, false);   // own active
        ISO b = buildChild(102L, parent, true, true, false);    // own deprecated (운영자 직접)
        given(osMetadataRepository.findByIdAndIsDeletedFalse(1L)).willReturn(Optional.of(parent));

        osMetadataLifecycleService.deprecate(1L);
        assertThat(a.isDeprecated()).isTrue();
        assertThat(b.isDeprecated()).isTrue();

        osMetadataLifecycleService.undeprecate(1L);
        assertThat(parent.isDeprecated()).isFalse();
        assertThat(a.isDeprecated()).isFalse();   // ★ own active → 복원
        assertThat(b.isDeprecated()).isTrue();    // ★ own deprecated → 보존
    }

    @Test
    @DisplayName("R4-1 회귀(★ 차원 독립) : ISO 를 deprecate 후 OS deprecate → ISO 는 deprecated 이되 enabled 유지 (deprecated ≠ disabled).")
    void parentDeprecate_doesNotDisableEnabledChild() {
        OSMetadata parent = buildParent(true, false, false);
        ISO iso = buildChild(101L, parent, true, true, false);   // own_en=true, own_dep=true (운영자가 직접 deprecate)
        given(osMetadataRepository.findByIdAndIsDeletedFalse(1L)).willReturn(Optional.of(parent));
        assertThat(iso.isEnabled()).isTrue();        // deprecated 이되 enabled
        assertThat(iso.isDeprecated()).isTrue();

        osMetadataLifecycleService.deprecate(1L);    // 부모 deprecate

        assertThat(parent.isEnabled()).isTrue();     // 부모 enabled 그대로
        assertThat(iso.isDeprecated()).isTrue();
        assertThat(iso.isEnabled()).isTrue();        // ★ 버그였던 지점 : ISO enabled 유지 (부모 deprecate 가 안 끔)
    }

    // R1-4-1 — 자식 ISO lifecycle 단위 시나리오 (toggleIsoEnabled / undeprecateIso / restoreISO)
    // 는 IsoLifecycleServiceTest 로 이동.
}
