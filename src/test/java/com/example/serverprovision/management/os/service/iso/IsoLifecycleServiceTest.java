package com.example.serverprovision.management.os.service.iso;

import com.example.serverprovision.global.exception.ChildLifecycleBlockedByParentException;
import com.example.serverprovision.global.exception.TypedNameMismatchException;
import com.example.serverprovision.global.lifecycle.SoftDeleteIntentService;
import com.example.serverprovision.global.marker.MarkerLayout;
import com.example.serverprovision.global.marker.service.ProvisionMarkerService;
import com.example.serverprovision.global.trash.TrashLifecycleService;
import com.example.serverprovision.management.os.entity.ISO;
import com.example.serverprovision.management.os.entity.OSMetadata;
import com.example.serverprovision.management.os.enums.OSName;
import com.example.serverprovision.management.os.exception.ISONotFoundException;
import com.example.serverprovision.management.os.exception.IllegalOSMetadataStateException;
import com.example.serverprovision.management.os.repository.ISORepository;
import com.example.serverprovision.management.os.repository.OSMetadataRepository;
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
 * R1-4-1 CP4 — IsoLifecycleService 단위 테스트.
 *
 * <p>옛 {@code OSMetadataCascadeTest} 의 ISO lifecycle 시나리오 8 개를 본 file 로 이동.
 * 시그니처가 단일 isoId 로 정렬됐고 부모 lookup 은 entity 의 {@code osMetadata} reference 로 자체 수행.</p>
 */
@ExtendWith(MockitoExtension.class)
class IsoLifecycleServiceTest {

	@Mock ISORepository isoRepository;
	@Mock OSMetadataRepository osMetadataRepository;
	@Mock TrashLifecycleService trashLifecycleService;
	@Mock SoftDeleteIntentService softDeleteIntentService;
	@Mock ProvisionMarkerService markerService;

	@InjectMocks IsoLifecycleService isoLifecycleService;

	// ==== assertBelongsToOs — URL forging 가드 ============================

	@Test
	@DisplayName("assertBelongsToOs : entity 의 부모 id 와 expectedParentId 일치 → 통과")
	void assertBelongsToOs_matches_passes() {
		OSMetadata parent = buildParent(1L, true, false, false);
		ISO iso = buildChild(101L, parent, true, false, false);
		given(isoRepository.findById(101L)).willReturn(Optional.of(iso));

		isoLifecycleService.assertBelongsToOs(101L, 1L);
		// throw 없으면 통과 (assertion 없음)
	}

	@Test
	@DisplayName("assertBelongsToOs : URL 의 osId 가 entity 부모 id 와 불일치 → ISONotFoundException")
	void assertBelongsToOs_mismatch_throws() {
		OSMetadata parent = buildParent(1L, true, false, false);
		ISO iso = buildChild(101L, parent, true, false, false);
		given(isoRepository.findById(101L)).willReturn(Optional.of(iso));

		assertThatThrownBy(() -> isoLifecycleService.assertBelongsToOs(101L, 999L))
				.isInstanceOf(ISONotFoundException.class);
	}

	// ==== toggleEnabled — 부모 가드 ====================================

	@Test
	@DisplayName("자식 ISO enable 시도 — 부모 disabled 면 거절 (ChildLifecycleBlockedByParentException)")
	void toggleEnable_parentDisabled_rejects() {
		OSMetadata parent = buildParent(1L, false, false, false);  // disabled
		ISO disabledIso = buildChild(101L, parent, false, false, false);
		given(isoRepository.findById(101L)).willReturn(Optional.of(disabledIso));
		given(osMetadataRepository.findByIdAndIsDeletedFalse(1L)).willReturn(Optional.of(parent));

		assertThatThrownBy(() -> isoLifecycleService.toggleEnabled(101L))
				.isInstanceOf(ChildLifecycleBlockedByParentException.class)
				.extracting("parentState").isEqualTo("DISABLED");
		assertThat(disabledIso.isEnabled()).isFalse();
	}

	@Test
	@DisplayName("자식 ISO enable — 부모 deprecated 이어도 허용 (R4-1 차원 독립: deprecated ≠ disabled)")
	void toggleEnable_parentDeprecated_allows() {
		OSMetadata parent = buildParent(1L, true, true, false);  // enabled + deprecated
		ISO disabledIso = buildChild(101L, parent, false, false, false);  // own_en=false
		given(isoRepository.findById(101L)).willReturn(Optional.of(disabledIso));
		given(osMetadataRepository.findByIdAndIsDeletedFalse(1L)).willReturn(Optional.of(parent));

		isoLifecycleService.toggleEnabled(101L);

		assertThat(disabledIso.isEnabled()).isTrue();        // 활성화 허용 (부모 deprecated 무관)
		assertThat(disabledIso.isDeprecated()).isTrue();      // 부모 deprecated 라 effective deprecated 유지
	}

	@Test
	@DisplayName("자식 ISO disable 은 부모 상태 무관 자유")
	void toggleDisable_freeWhenParentActive() {
		OSMetadata parent = buildParent(1L, true, false, false);
		ISO activeIso = buildChild(101L, parent, true, false, false);
		given(isoRepository.findById(101L)).willReturn(Optional.of(activeIso));
		given(osMetadataRepository.findByIdAndIsDeletedFalse(1L)).willReturn(Optional.of(parent));

		isoLifecycleService.toggleEnabled(101L);

		assertThat(activeIso.isEnabled()).isFalse();
	}

	// ==== undeprecate — 부모 가드 ======================================

	@Test
	@DisplayName("자식 ISO undeprecate — 부모 deprecated 면 거절")
	void undeprecate_parentDeprecated_rejects() {
		OSMetadata parent = buildParent(1L, true, true, false);
		ISO deprecatedIso = buildChild(101L, parent, true, true, false);
		given(isoRepository.findById(101L)).willReturn(Optional.of(deprecatedIso));
		given(osMetadataRepository.findByIdAndIsDeletedFalse(1L)).willReturn(Optional.of(parent));

		assertThatThrownBy(() -> isoLifecycleService.undeprecate(101L))
				.isInstanceOf(ChildLifecycleBlockedByParentException.class)
				.extracting("parentState").isEqualTo("DEPRECATED");
	}

	@Test
	@DisplayName("자식 ISO undeprecate — 부모 active 면 OK")
	void undeprecate_parentActive_succeeds() {
		OSMetadata parent = buildParent(1L, true, false, false);
		ISO deprecatedIso = buildChild(101L, parent, true, true, false);
		given(isoRepository.findById(101L)).willReturn(Optional.of(deprecatedIso));
		given(osMetadataRepository.findByIdAndIsDeletedFalse(1L)).willReturn(Optional.of(parent));

		isoLifecycleService.undeprecate(101L);

		assertThat(deprecatedIso.isDeprecated()).isFalse();
	}

	// ==== restore — 부모 가드 ==========================================

	@Test
	@DisplayName("자식 ISO restore — 부모 deleted 면 거절 (사용자 명시 사건)")
	void restore_parentDeleted_rejects() {
		OSMetadata parent = buildParent(1L, true, false, true);  // deleted
		ISO deletedIso = buildChild(101L, parent, true, false, true);
		given(isoRepository.findById(101L)).willReturn(Optional.of(deletedIso));
		given(osMetadataRepository.findById(1L)).willReturn(Optional.of(parent));

		assertThatThrownBy(() -> isoLifecycleService.restore(101L))
				.isInstanceOf(ChildLifecycleBlockedByParentException.class)
				.extracting("parentState").isEqualTo("DELETED");
	}

	@Test
	@DisplayName("자식 ISO restore — 부모 active 면 OK (trash service 위임)")
	void restore_parentActive_delegatesToTrash() {
		OSMetadata parent = buildParent(1L, true, false, false);
		ISO deletedIso = buildChild(101L, parent, true, false, true);
		// MK3 — trashedPath 가 있어야 restore 시 originalFilename 추출 가능
		deletedIso.markTrashed("/var/trash/iso/dvd.iso");
		given(isoRepository.findById(101L)).willReturn(Optional.of(deletedIso));
		given(osMetadataRepository.findById(1L)).willReturn(Optional.of(parent));

		isoLifecycleService.restore(101L);

		verify(trashLifecycleService).restoreFromTrash(eq(deletedIso), any());
	}

	@Test
	@DisplayName("자식 ISO restore — 자식 자체가 active 면 거절 (부모는 active)")
	void restore_childAlreadyActive_rejects() {
		OSMetadata parent = buildParent(1L, true, false, false);
		ISO activeIso = buildChild(101L, parent, true, false, false);  // not deleted
		given(isoRepository.findById(101L)).willReturn(Optional.of(activeIso));
		given(osMetadataRepository.findById(1L)).willReturn(Optional.of(parent));

		assertThatThrownBy(() -> isoLifecycleService.restore(101L))
				.isInstanceOf(IllegalOSMetadataStateException.class);
		verify(trashLifecycleService, never()).restoreFromTrash(any(), any());
	}

	// ==== purge ========================================================

	@Test
	@DisplayName("purge(happy) : soft-deleted ISO 영구 삭제 + sidecar 정리")
	void purge_whenSoftDeleted_deletesEntity() {
		OSMetadata parent = buildParent(1L, true, false, false);
		ISO softDeletedIso = buildChild(101L, parent, false, false, true);  // deleted=true
		given(isoRepository.findById(101L)).willReturn(Optional.of(softDeletedIso));
		given(markerService.resolveMarkerFile(any(), eq(MarkerLayout.SIDECAR)))
				.willReturn(Path.of("/var/trash/iso/dvd.iso.provision.json"));

		isoLifecycleService.purge(101L);

		verify(isoRepository).delete(softDeletedIso);
	}

	@Test
	@DisplayName("purge(fail) : 활성 ISO 에 purge → IllegalOSMetadataStateException")
	void purge_whenActive_throws() {
		OSMetadata parent = buildParent(1L, true, false, false);
		ISO activeIso = buildChild(101L, parent, true, false, false);
		given(isoRepository.findById(101L)).willReturn(Optional.of(activeIso));

		assertThatThrownBy(() -> isoLifecycleService.purge(101L))
				.isInstanceOf(IllegalOSMetadataStateException.class);
		verify(isoRepository, never()).delete(any());
	}

	@Test
	@DisplayName("purgeWithTypedNameCheck : typed-name 불일치 → TypedNameMismatchException")
	void purgeWithTypedNameCheck_mismatch_throws() {
		OSMetadata parent = buildParent(1L, true, false, false);
		ISO softDeletedIso = buildChild(101L, parent, false, false, true);
		given(isoRepository.findById(101L)).willReturn(Optional.of(softDeletedIso));

		assertThatThrownBy(() -> isoLifecycleService.purgeWithTypedNameCheck(101L, "wrong"))
				.isInstanceOf(TypedNameMismatchException.class);
		verify(isoRepository, never()).delete(any());
	}

	// ==== fixtures =====================================================

	private static OSMetadata buildParent(Long id, boolean enabled, boolean deprecated, boolean deleted) {
		OSMetadata p = OSMetadata.builder()
				.id(id)
				.osName(OSName.ROCKY_LINUX)
				.osVersion("9.6")
				.ownEnabled(enabled)
				.ownDeprecated(deprecated)
				.isDeleted(deleted)
				.build();
		p.recomputeEffective();   // R4-1 — 루트 → effective = own
		return p;
	}

	private static ISO buildChild(Long id, OSMetadata parent, boolean enabled, boolean deprecated, boolean deleted) {
		ISO iso = ISO.builder()
				.id(id)
				.osMetadata(parent)
				.isoPath("/var/iso/dvd.iso")
				.checksum("hash")
				.manifestHash("hash")
				.ownEnabled(enabled)
				.ownDeprecated(deprecated)
				.isDeleted(deleted)
				.build();
		iso.recomputeEffective();   // R4-1 — effective = own ⊕ 부모
		return iso;
	}
}
