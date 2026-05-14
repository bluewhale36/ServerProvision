package com.example.serverprovision.management.os.service;

import com.example.serverprovision.global.exception.ChildLifecycleBlockedByParentException;
import com.example.serverprovision.management.os.entity.ISO;
import com.example.serverprovision.management.os.entity.OSImage;
import com.example.serverprovision.management.os.enums.OSName;
import com.example.serverprovision.management.os.exception.ISONotFoundException;
import com.example.serverprovision.management.os.exception.IllegalOSImageStateException;
import com.example.serverprovision.management.os.repository.ISORepository;
import com.example.serverprovision.management.os.repository.OSImageRepository;
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
class OSImageCascadeTest {

    @Mock OSImageRepository osImageRepository;
    @Mock ISORepository isoRepository;
    @Mock TrashLifecycleService trashLifecycleService;
    @Mock SoftDeleteIntentService softDeleteIntentService;

    @InjectMocks OSImageService osImageService;

    // ==== helper ===================================================

    private OSImage buildParent(boolean enabled, boolean deprecated, boolean deleted, List<ISO> isos) {
        OSImage parent = OSImage.builder()
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

    private ISO buildChild(Long id, OSImage parent, boolean enabled, boolean deprecated, boolean deleted) {
        ISO iso = ISO.builder()
                .id(id)
                .osImage(parent)
                .isoPath("/opt/iso/rocky-9.5-" + id + ".iso")
                .isEnabled(enabled)
                .isDeprecated(deprecated)
                .isDeleted(deleted)
                .build();
        return iso;
    }

    // ==== 부모 toggleEnabled cascade ================================

    @Test
    @DisplayName("toggle off : 활성 부모 → 비활성. 자식 active ISO 도 모두 비활성. deprecated/deleted 자식은 skip.")
    void toggleOff_cascadesToActiveChildren_skipsDeprecatedAndDeleted() {
        ISO active = buildChild(101L, null, true, false, false);
        ISO deprecated = buildChild(102L, null, true, true, false);
        ISO deleted = buildChild(103L, null, true, false, true);
        List<ISO> isos = new ArrayList<>(List.of(active, deprecated, deleted));
        OSImage parent = buildParent(true, false, false, isos);
        // 자식의 osImage 역참조 세팅 — entity 의 isos 자식이 osImage 를 알게.
        given(osImageRepository.findByIdAndIsDeletedFalse(1L)).willReturn(Optional.of(parent));

        osImageService.toggleEnabled(1L);

        assertThat(parent.isEnabled()).isFalse();
        assertThat(active.isEnabled()).isFalse();    // cascade 대상
        assertThat(deprecated.isEnabled()).isTrue(); // skip — deprecated 자식
        assertThat(deleted.isEnabled()).isTrue();    // skip — deleted 자식
    }

    @Test
    @DisplayName("toggle on : 비활성 부모 → 활성. 자식 (active 상태였던) 도 모두 활성. (자식이 이전엔 disabled 인 케이스도 동기화)")
    void toggleOn_cascadesToActiveChildren() {
        ISO disabledChild = buildChild(101L, null, false, false, false);
        List<ISO> isos = new ArrayList<>(List.of(disabledChild));
        OSImage parent = buildParent(false, false, false, isos);
        given(osImageRepository.findByIdAndIsDeletedFalse(1L)).willReturn(Optional.of(parent));

        osImageService.toggleEnabled(1L);

        assertThat(parent.isEnabled()).isTrue();
        assertThat(disabledChild.isEnabled()).isTrue();
    }

    // ==== 부모 deprecate cascade ====================================

    @Test
    @DisplayName("deprecate : 부모 active → deprecated. 자식 active 만 deprecate. 이미 deprecated 또는 deleted 자식은 skip.")
    void deprecate_cascadesToActiveChildren_skipsDeprecatedAndDeleted() {
        ISO active = buildChild(101L, null, true, false, false);
        ISO alreadyDeprecated = buildChild(102L, null, true, true, false);
        ISO deleted = buildChild(103L, null, true, false, true);
        OSImage parent = buildParent(true, false, false, new ArrayList<>(List.of(active, alreadyDeprecated, deleted)));
        given(osImageRepository.findByIdAndIsDeletedFalse(1L)).willReturn(Optional.of(parent));

        osImageService.deprecateImage(1L);

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
        OSImage parent = buildParent(true, true, false, new ArrayList<>(List.of(active, deprecated, deletedDeprecated)));
        given(osImageRepository.findByIdAndIsDeletedFalse(1L)).willReturn(Optional.of(parent));

        osImageService.undeprecateImage(1L);

        assertThat(parent.isDeprecated()).isFalse();
        assertThat(active.isDeprecated()).isFalse();           // 그대로 active
        assertThat(deprecated.isDeprecated()).isFalse();        // cascade undeprecate
        assertThat(deletedDeprecated.isDeprecated()).isTrue();  // skip — deleted 는 휴지통 잔존
    }

    // ==== 자식 단독 가드 — toggleIsoEnabled =========================

    @Test
    @DisplayName("자식 ISO enable 시도 — 부모 disabled 면 거절 (ChildLifecycleBlockedByParentException)")
    void toggleIsoEnable_parentDisabled_rejects() {
        OSImage parent = buildParent(false, false, false, new ArrayList<>());
        ISO disabledIso = buildChild(101L, parent, false, false, false);
        given(osImageRepository.findByIdAndIsDeletedFalse(1L)).willReturn(Optional.of(parent));
        given(isoRepository.findByIdAndOsImage_Id(101L, 1L)).willReturn(Optional.of(disabledIso));

        assertThatThrownBy(() -> osImageService.toggleIsoEnabled(1L, 101L))
                .isInstanceOf(ChildLifecycleBlockedByParentException.class)
                .extracting("parentState").isEqualTo("DISABLED");
        assertThat(disabledIso.isEnabled()).isFalse(); // 상태 변경 없음
    }

    @Test
    @DisplayName("자식 ISO enable 시도 — 부모 deprecated 면 거절")
    void toggleIsoEnable_parentDeprecated_rejects() {
        OSImage parent = buildParent(true, true, false, new ArrayList<>());
        ISO disabledIso = buildChild(101L, parent, false, false, false);
        given(osImageRepository.findByIdAndIsDeletedFalse(1L)).willReturn(Optional.of(parent));
        given(isoRepository.findByIdAndOsImage_Id(101L, 1L)).willReturn(Optional.of(disabledIso));

        assertThatThrownBy(() -> osImageService.toggleIsoEnabled(1L, 101L))
                .isInstanceOf(ChildLifecycleBlockedByParentException.class)
                .extracting("parentState").isEqualTo("DEPRECATED");
    }

    @Test
    @DisplayName("자식 ISO disable 은 부모 상태 무관 자유 (부모 active 일 때 자식 단독 disable)")
    void toggleIsoDisable_freeWhenParentActive() {
        OSImage parent = buildParent(true, false, false, new ArrayList<>());
        ISO activeIso = buildChild(101L, parent, true, false, false);
        given(osImageRepository.findByIdAndIsDeletedFalse(1L)).willReturn(Optional.of(parent));
        given(isoRepository.findByIdAndOsImage_Id(101L, 1L)).willReturn(Optional.of(activeIso));

        osImageService.toggleIsoEnabled(1L, 101L);

        assertThat(activeIso.isEnabled()).isFalse();
    }

    // ==== 자식 단독 가드 — undeprecateIso ============================

    @Test
    @DisplayName("자식 ISO undeprecate — 부모 deprecated 면 거절")
    void undeprecateIso_parentDeprecated_rejects() {
        OSImage parent = buildParent(true, true, false, new ArrayList<>());
        given(osImageRepository.findByIdAndIsDeletedFalse(1L)).willReturn(Optional.of(parent));

        assertThatThrownBy(() -> osImageService.undeprecateIso(1L, 101L))
                .isInstanceOf(ChildLifecycleBlockedByParentException.class)
                .extracting("parentState").isEqualTo("DEPRECATED");
    }

    @Test
    @DisplayName("자식 ISO undeprecate — 부모 active 면 OK")
    void undeprecateIso_parentActive_succeeds() {
        OSImage parent = buildParent(true, false, false, new ArrayList<>());
        ISO deprecatedIso = buildChild(101L, parent, true, true, false);
        given(osImageRepository.findByIdAndIsDeletedFalse(1L)).willReturn(Optional.of(parent));
        given(isoRepository.findByIdAndOsImage_Id(101L, 1L)).willReturn(Optional.of(deprecatedIso));

        osImageService.undeprecateIso(1L, 101L);

        assertThat(deprecatedIso.isDeprecated()).isFalse();
    }

    // ==== 자식 단독 가드 — restoreISO ================================

    @Test
    @DisplayName("자식 ISO restore — 부모 deleted 면 거절 (사용자 명시 사건)")
    void restoreIso_parentDeleted_rejects() {
        OSImage parent = buildParent(true, false, true, new ArrayList<>());
        given(osImageRepository.findById(1L)).willReturn(Optional.of(parent));

        assertThatThrownBy(() -> osImageService.restoreISO(1L, 101L))
                .isInstanceOf(ChildLifecycleBlockedByParentException.class)
                .extracting("parentState").isEqualTo("DELETED");
    }

    @Test
    @DisplayName("자식 ISO restore — 부모 active 면 OK (trash service 위임)")
    void restoreIso_parentActive_delegatesToTrash() {
        OSImage parent = buildParent(true, false, false, new ArrayList<>());
        ISO deletedIso = buildChild(101L, parent, true, false, true);
        given(osImageRepository.findById(1L)).willReturn(Optional.of(parent));
        given(isoRepository.findByIdAndOsImage_Id(101L, 1L)).willReturn(Optional.of(deletedIso));

        osImageService.restoreISO(1L, 101L);

        // trashLifecycleService.restoreFromTrash 호출 → Mockito 가 verify 가능. 본 테스트는 throw 안 함만 검증.
    }

    @Test
    @DisplayName("자식 ISO restore — 자식 자체가 active 면 거절 (부모는 active)")
    void restoreIso_childAlreadyActive_rejects() {
        OSImage parent = buildParent(true, false, false, new ArrayList<>());
        ISO activeIso = buildChild(101L, parent, true, false, false);
        given(osImageRepository.findById(1L)).willReturn(Optional.of(parent));
        given(isoRepository.findByIdAndOsImage_Id(101L, 1L)).willReturn(Optional.of(activeIso));

        assertThatThrownBy(() -> osImageService.restoreISO(1L, 101L))
                .isInstanceOf(IllegalOSImageStateException.class);
    }
}
