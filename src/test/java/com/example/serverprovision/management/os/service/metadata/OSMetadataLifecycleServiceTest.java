package com.example.serverprovision.management.os.service.metadata;

import com.example.serverprovision.global.exception.TypedNameMismatchException;
import com.example.serverprovision.global.trash.TrashLifecycleService;
import com.example.serverprovision.management.os.entity.OSMetadata;
import com.example.serverprovision.management.os.enums.OSName;
import com.example.serverprovision.management.os.exception.DuplicateOSMetadataException;
import com.example.serverprovision.management.os.exception.IllegalOSMetadataStateException;
import com.example.serverprovision.management.os.exception.OSMetadataNotFoundException;
import com.example.serverprovision.management.os.repository.OSMetadataRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * R1-3 — OSMetadataLifecycleService 단위 테스트.
 *
 * <p>cascade 동작의 동치성 (자식 ISO 까지 같은 lifecycle 적용) 은 {@code OSMetadataCascadeTest} 가 담당.
 * 본 test 는 lifecycle service 자체의 분기 / 예외 / default 메서드 위임을 검증한다.</p>
 */
@ExtendWith(MockitoExtension.class)
class OSMetadataLifecycleServiceTest {

	@Mock OSMetadataRepository osMetadataRepository;
	@Mock TrashLifecycleService trashLifecycleService;
	@Mock com.example.serverprovision.management.os.service.iso.IsoLifecycleService isoLifecycleService;

	// R7-2 — purgeWithTypedNameCheck 가 의존성 0 인 static TypedNameGuard 를 직접 호출하므로
	// TypedNameVerifier 빈 주입/mock 이 사라졌다. typed-name 일치/불일치는 entity 의 displayName() 으로 결정.
	@InjectMocks OSMetadataLifecycleService osMetadataLifecycleService;

	// ==== restore default 위임 =========================================

	@Test
	@DisplayName("restore(Long) default : 단순 restore 는 interface default 가 restore(id, false) 로 위임 — cascade 없음")
	void restoreDefault_delegatesToCascadeFalse() {
		OSMetadata image = OSMetadata.builder()
				.id(1L).osName(OSName.ROCKY_LINUX).osVersion("9.6")
				.isEnabled(false).isDeprecated(false).isDeleted(true).build();
		given(osMetadataRepository.findByIdAndIsDeletedTrue(1L)).willReturn(Optional.of(image));

		// interface default 호출 — abstract restore(Long, boolean) 가 실행되고 cascade=false 로 처리됨.
		osMetadataLifecycleService.restore(1L);

		assertThat(image.isDeleted()).isFalse();
		// cascade=false 였으므로 자식 ISO restoreISO 호출 없음.
		verify(isoLifecycleService, never()).restore(any());
	}

	// ==== restore — duplicate 충돌 =====================================

	@Test
	@DisplayName("restore(409) : 같은 (osName, osVersion) 활성 자원이 있으면 DuplicateOSMetadataException 으로 거절")
	void restore_whenActiveConflict_throwsDuplicate() {
		OSMetadata image = OSMetadata.builder()
				.id(1L).osName(OSName.ROCKY_LINUX).osVersion("9.6")
				.isEnabled(false).isDeprecated(false).isDeleted(true).build();
		given(osMetadataRepository.findByIdAndIsDeletedTrue(1L)).willReturn(Optional.of(image));
		given(osMetadataRepository.existsByOsNameAndOsVersionAndIsDeletedFalse(OSName.ROCKY_LINUX, "9.6"))
				.willReturn(true);

		assertThatThrownBy(() -> osMetadataLifecycleService.restore(1L, false))
				.isInstanceOf(DuplicateOSMetadataException.class);
		// entity 변경 안 됨 — duplicate 검사 fail 후 restore() 호출 전.
		assertThat(image.isDeleted()).isTrue();
	}

	// ==== softDelete — 활성 자원 부재 ===================================

	@Test
	@DisplayName("softDelete(404) : 존재하지 않는 활성 자원 id → OSMetadataNotFoundException")
	void softDelete_whenNotFound_throws() {
		given(osMetadataRepository.findByIdAndIsDeletedFalse(99L)).willReturn(Optional.empty());

		assertThatThrownBy(() -> osMetadataLifecycleService.softDelete(99L))
				.isInstanceOf(OSMetadataNotFoundException.class);
		verify(trashLifecycleService, never()).softDeleteToTrash(any());
	}

	// ==== purge — soft-deleted 상태 검증 ===============================

	@Test
	@DisplayName("purge(happy) : soft-deleted 자원 → row 영구 삭제 + 자식 sidecar 정리 위임")
	void purge_whenSoftDeleted_deletesEntity() {
		OSMetadata image = OSMetadata.builder()
				.id(1L).osName(OSName.ROCKY_LINUX).osVersion("9.6")
				.isEnabled(false).isDeprecated(false).isDeleted(true).build();
		given(osMetadataRepository.findByIdAndIsDeletedTrue(1L)).willReturn(Optional.of(image));

		osMetadataLifecycleService.purge(1L);

		verify(osMetadataRepository).delete(image);
		// 자식 ISO 0 — cleanupIsoArtifacts 호출 없음 (image.getIsos() 가 빈 리스트)
		verify(isoLifecycleService, never()).cleanupArtifacts(any());
	}

	@Test
	@DisplayName("purge(fail) : 활성 자원에 purge → IllegalOSMetadataStateException")
	void purge_whenActive_throws() {
		given(osMetadataRepository.findByIdAndIsDeletedTrue(1L)).willReturn(Optional.empty());

		assertThatThrownBy(() -> osMetadataLifecycleService.purge(1L))
				.isInstanceOf(IllegalOSMetadataStateException.class);
		verify(osMetadataRepository, never()).delete(any());
	}

	// ==== purgeWithTypedNameCheck =====================================

	@Test
	@DisplayName("purgeWithTypedNameCheck(mismatch) : 입력명이 displayName 과 불일치 → TypedNameMismatchException 전파 + entity 삭제 안 됨")
	void purgeWithTypedNameCheck_nameMismatch_propagatesAndNoDelete() {
		OSMetadata image = OSMetadata.builder()
				.id(1L).osName(OSName.ROCKY_LINUX).osVersion("9.6")
				.isEnabled(false).isDeprecated(false).isDeleted(true).build();
		given(osMetadataRepository.findByIdAndIsDeletedTrue(1L)).willReturn(Optional.of(image));

		// R7-2 — static TypedNameGuard 가 로딩한 image 의 displayName 과 입력("wrong") 불일치를 판정해 예외를 던진다.
		// service 는 흡수하지 않고 그대로 전파해야 하고, purge 본체 진입 전이라 delete 미호출.
		assertThatThrownBy(() -> osMetadataLifecycleService.purgeWithTypedNameCheck(1L, "wrong"))
				.isInstanceOf(TypedNameMismatchException.class);
		verify(osMetadataRepository, never()).delete(any());
	}

	@Test
	@DisplayName("purgeWithTypedNameCheck(happy) : 입력명이 displayName 과 일치 → purge 본체 호출, row 삭제")
	void purgeWithTypedNameCheck_nameMatches_purges() {
		OSMetadata image = OSMetadata.builder()
				.id(1L).osName(OSName.ROCKY_LINUX).osVersion("9.6")
				.isEnabled(false).isDeprecated(false).isDeleted(true).build();
		// purgeWithTypedNameCheck 가 내부에서 findByIdAndIsDeletedTrue 를 2 번 호출 (자기 + purge 위임).
		given(osMetadataRepository.findByIdAndIsDeletedTrue(1L)).willReturn(Optional.of(image));

		// R7-2 — typedName = image.displayName() → static guard no-op → purge 진행.
		osMetadataLifecycleService.purgeWithTypedNameCheck(1L, image.displayName());

		verify(osMetadataRepository, times(1)).delete(image);
	}

	@Test
	@DisplayName("purgeWithTypedNameCheck(상태위반) : soft-deleted 가 아니면 IllegalOSMetadataStateException (검증 진입 전 차단)")
	void purgeWithTypedNameCheck_notSoftDeleted_throwsBeforeCheck() {
		// soft-deleted 자원이 없음 → 상태 가드가 typed-name 검증 진입 전에 차단.
		given(osMetadataRepository.findByIdAndIsDeletedTrue(1L)).willReturn(Optional.empty());

		assertThatThrownBy(() -> osMetadataLifecycleService.purgeWithTypedNameCheck(1L, "anything"))
				.isInstanceOf(IllegalOSMetadataStateException.class);
		verify(osMetadataRepository, never()).delete(any());
	}
}
