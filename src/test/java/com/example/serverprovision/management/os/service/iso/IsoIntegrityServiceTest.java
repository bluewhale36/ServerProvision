package com.example.serverprovision.management.os.service.iso;

import com.example.serverprovision.global.marker.MarkerContent;
import com.example.serverprovision.global.marker.MarkerLayout;
import com.example.serverprovision.global.marker.ResourceType;
import com.example.serverprovision.global.marker.exception.MarkerMissingException;
import com.example.serverprovision.global.marker.service.ProvisionMarkerService;
import com.example.serverprovision.global.marker.IntegrityStatus;
import com.example.serverprovision.management.common.dto.response.IntegrityStatusResponse;
import com.example.serverprovision.management.os.entity.ISO;
import com.example.serverprovision.management.os.entity.OSMetadata;
import com.example.serverprovision.management.os.enums.OSName;
import com.example.serverprovision.management.os.exception.ISONotFoundException;
import com.example.serverprovision.management.os.exception.IllegalOSMetadataStateException;
import com.example.serverprovision.management.os.exception.OSMetadataNotFoundException;
import com.example.serverprovision.management.os.repository.ISORepository;
import com.example.serverprovision.management.os.repository.OSMetadataRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;

/**
 * IsoIntegrityService 단위 테스트.
 *
 * <p>R1-4-3 plan §6 시나리오 1~12 — verify 5 분기 (ORIGINAL / MARKER_MISSING / SIGNATURE_INVALID
 * / TAMPERED file missing / TAMPERED hash mismatch) + verifyAndRecord 영속화 2 + findStatus
 * 2 + 도메인 예외 3 시나리오. controller flow / mock 갱신은 별도 controller flow test 에서 처리.</p>
 */
@ExtendWith(MockitoExtension.class)
class IsoIntegrityServiceTest {

	@Mock OSMetadataRepository osMetadataRepository;
	@Mock ISORepository isoRepository;
	@Mock ProvisionMarkerService markerService;
	@InjectMocks IsoIntegrityService isoIntegrityService;

	private OSMetadata activeParent(Long id) {
		return OSMetadata.builder()
				.id(id).osName(OSName.ROCKY_LINUX).osVersion("9.5")
				.isEnabled(true).isDeleted(false).build();
	}

	private ISO liveIso(Long id, OSMetadata parent, Path isoPath) {
		return ISO.builder()
				.id(id).osMetadata(parent).isoPath(isoPath.toString())
				.checksum("h").manifestHash("h").markerSignature("sig")
				.isEnabled(true).isDeleted(false).build();
	}

	private MarkerContent marker(Long isoId, String manifestHash) {
		return new MarkerContent(
				ResourceType.OS_ISO.name(), isoId, Map.of(), Instant.now(), manifestHash, "sig");
	}

	// ====== verify — 5 분기 ============================================

	@Test
	@DisplayName("verify : happy — 서명/해시 모두 통과 → ORIGINAL")
	void verify_happyOriginal(@TempDir Path tempDir) throws Exception {
		Path isoPath = tempDir.resolve("dvd.iso");
		Files.writeString(isoPath, "iso");
		OSMetadata parent = activeParent(1L);
		ISO iso = liveIso(2L, parent, isoPath);
		MarkerContent m = marker(2L, "expected");

		given(osMetadataRepository.findByIdAndIsDeletedFalse(1L)).willReturn(Optional.of(parent));
		given(isoRepository.findByIdAndOsMetadata_Id(2L, 1L)).willReturn(Optional.of(iso));
		given(markerService.read(isoPath, MarkerLayout.SIDECAR)).willReturn(m);
		given(markerService.verifySignature(m)).willReturn(true);
		given(markerService.verifyManifestHash(eq(m), any())).willReturn(true);

		IntegrityStatus status = isoIntegrityService.verify(1L, 2L);

		assertThat(status).isEqualTo(IntegrityStatus.ORIGINAL);
	}

	@Test
	@DisplayName("verify : 마커 부재 → MARKER_MISSING")
	void verify_markerMissing(@TempDir Path tempDir) throws Exception {
		Path isoPath = tempDir.resolve("dvd.iso");
		Files.writeString(isoPath, "iso");
		OSMetadata parent = activeParent(1L);
		ISO iso = liveIso(2L, parent, isoPath);

		given(osMetadataRepository.findByIdAndIsDeletedFalse(1L)).willReturn(Optional.of(parent));
		given(isoRepository.findByIdAndOsMetadata_Id(2L, 1L)).willReturn(Optional.of(iso));
		given(markerService.read(isoPath, MarkerLayout.SIDECAR))
				.willThrow(new MarkerMissingException(isoPath.toString()));

		IntegrityStatus status = isoIntegrityService.verify(1L, 2L);

		assertThat(status).isEqualTo(IntegrityStatus.MARKER_MISSING);
	}

	@Test
	@DisplayName("verify : 서명 무효 → SIGNATURE_INVALID")
	void verify_signatureInvalid(@TempDir Path tempDir) throws Exception {
		Path isoPath = tempDir.resolve("dvd.iso");
		Files.writeString(isoPath, "iso");
		OSMetadata parent = activeParent(1L);
		ISO iso = liveIso(2L, parent, isoPath);
		MarkerContent m = marker(2L, "expected");

		given(osMetadataRepository.findByIdAndIsDeletedFalse(1L)).willReturn(Optional.of(parent));
		given(isoRepository.findByIdAndOsMetadata_Id(2L, 1L)).willReturn(Optional.of(iso));
		given(markerService.read(isoPath, MarkerLayout.SIDECAR)).willReturn(m);
		given(markerService.verifySignature(m)).willReturn(false);

		IntegrityStatus status = isoIntegrityService.verify(1L, 2L);

		assertThat(status).isEqualTo(IntegrityStatus.SIGNATURE_INVALID);
	}

	@Test
	@DisplayName("verify : 파일 부재 → TAMPERED")
	void verify_fileMissing(@TempDir Path tempDir) {
		Path isoPath = tempDir.resolve("missing.iso");
		// 디스크에 파일 없음 — Files.isRegularFile → false
		OSMetadata parent = activeParent(1L);
		ISO iso = liveIso(2L, parent, isoPath);
		MarkerContent m = marker(2L, "expected");

		given(osMetadataRepository.findByIdAndIsDeletedFalse(1L)).willReturn(Optional.of(parent));
		given(isoRepository.findByIdAndOsMetadata_Id(2L, 1L)).willReturn(Optional.of(iso));
		given(markerService.read(isoPath, MarkerLayout.SIDECAR)).willReturn(m);
		given(markerService.verifySignature(m)).willReturn(true);

		IntegrityStatus status = isoIntegrityService.verify(1L, 2L);

		assertThat(status).isEqualTo(IntegrityStatus.TAMPERED);
	}

	@Test
	@DisplayName("verify : manifestHash 불일치 → TAMPERED")
	void verify_hashMismatch(@TempDir Path tempDir) throws Exception {
		Path isoPath = tempDir.resolve("dvd.iso");
		Files.writeString(isoPath, "iso");
		OSMetadata parent = activeParent(1L);
		ISO iso = liveIso(2L, parent, isoPath);
		MarkerContent m = marker(2L, "expected");

		given(osMetadataRepository.findByIdAndIsDeletedFalse(1L)).willReturn(Optional.of(parent));
		given(isoRepository.findByIdAndOsMetadata_Id(2L, 1L)).willReturn(Optional.of(iso));
		given(markerService.read(isoPath, MarkerLayout.SIDECAR)).willReturn(m);
		given(markerService.verifySignature(m)).willReturn(true);
		given(markerService.verifyManifestHash(eq(m), any())).willReturn(false);

		IntegrityStatus status = isoIntegrityService.verify(1L, 2L);

		assertThat(status).isEqualTo(IntegrityStatus.TAMPERED);
	}

	// ====== verifyAndRecord — 영속화 2 시나리오 =========================

	@Test
	@DisplayName("verifyAndRecord : ORIGINAL 결과를 entity snapshot 에 기록")
	void verifyAndRecord_persistsOriginal(@TempDir Path tempDir) throws Exception {
		Path isoPath = tempDir.resolve("dvd.iso");
		Files.writeString(isoPath, "iso");
		OSMetadata parent = activeParent(1L);
		ISO iso = liveIso(2L, parent, isoPath);
		MarkerContent m = marker(2L, "expected");

		given(osMetadataRepository.findByIdAndIsDeletedFalse(1L)).willReturn(Optional.of(parent));
		given(isoRepository.findByIdAndOsMetadata_Id(2L, 1L)).willReturn(Optional.of(iso));
		given(markerService.read(isoPath, MarkerLayout.SIDECAR)).willReturn(m);
		given(markerService.verifySignature(m)).willReturn(true);
		given(markerService.verifyManifestHash(eq(m), any())).willReturn(true);

		IntegrityStatus status = isoIntegrityService.verifyAndRecord(1L, 2L);

		assertThat(status).isEqualTo(IntegrityStatus.ORIGINAL);
		assertThat(iso.getLastIntegrityStatus()).isEqualTo(IntegrityStatus.ORIGINAL);
		assertThat(iso.getLastVerifiedAt()).isNotNull();
	}

	@Test
	@DisplayName("verifyAndRecord : TAMPERED 결과도 entity snapshot 에 기록")
	void verifyAndRecord_persistsTampered(@TempDir Path tempDir) throws Exception {
		Path isoPath = tempDir.resolve("dvd.iso");
		Files.writeString(isoPath, "iso");
		OSMetadata parent = activeParent(1L);
		ISO iso = liveIso(2L, parent, isoPath);
		MarkerContent m = marker(2L, "other-hash");

		given(osMetadataRepository.findByIdAndIsDeletedFalse(1L)).willReturn(Optional.of(parent));
		given(isoRepository.findByIdAndOsMetadata_Id(2L, 1L)).willReturn(Optional.of(iso));
		given(markerService.read(isoPath, MarkerLayout.SIDECAR)).willReturn(m);
		given(markerService.verifySignature(m)).willReturn(true);
		given(markerService.verifyManifestHash(any(), any())).willReturn(false);

		IntegrityStatus status = isoIntegrityService.verifyAndRecord(1L, 2L);

		assertThat(status).isEqualTo(IntegrityStatus.TAMPERED);
		assertThat(iso.getLastIntegrityStatus()).isEqualTo(IntegrityStatus.TAMPERED);
		assertThat(iso.getLastVerifiedAt()).isNotNull();
	}

	// ====== findStatus — 2 시나리오 ====================================

	@Test
	@DisplayName("findStatus : 마지막 검증 결과 + verifiedAt 응답")
	void findStatus_returnsLastSnapshot(@TempDir Path tempDir) {
		Path isoPath = tempDir.resolve("dvd.iso");
		OSMetadata parent = activeParent(1L);
		ISO iso = liveIso(2L, parent, isoPath);
		Instant verifiedAt = Instant.now();
		iso.recordIntegritySnapshot(IntegrityStatus.SIGNATURE_INVALID, verifiedAt);

		given(osMetadataRepository.findByIdAndIsDeletedFalse(1L)).willReturn(Optional.of(parent));
		given(isoRepository.findByIdAndOsMetadata_Id(2L, 1L)).willReturn(Optional.of(iso));

		IntegrityStatusResponse response = isoIntegrityService.findStatus(1L, 2L);

		assertThat(response.resourceId()).isEqualTo(2L);
		assertThat(response.integrityStatus()).isEqualTo(IntegrityStatus.SIGNATURE_INVALID);
		assertThat(response.verifiedAt()).isEqualTo(verifiedAt);
	}

	@Test
	@DisplayName("findStatus : lastIntegrityStatus 가 null 이면 NOT_VERIFIED 기본값")
	void findStatus_defaultsToNotVerified(@TempDir Path tempDir) {
		Path isoPath = tempDir.resolve("dvd.iso");
		OSMetadata parent = activeParent(1L);
		ISO iso = liveIso(2L, parent, isoPath);
		// recordIntegritySnapshot 호출 X — lastIntegrityStatus = null

		given(osMetadataRepository.findByIdAndIsDeletedFalse(1L)).willReturn(Optional.of(parent));
		given(isoRepository.findByIdAndOsMetadata_Id(2L, 1L)).willReturn(Optional.of(iso));

		IntegrityStatusResponse response = isoIntegrityService.findStatus(1L, 2L);

		assertThat(response.integrityStatus()).isEqualTo(IntegrityStatus.NOT_VERIFIED);
		assertThat(response.verifiedAt()).isNull();
	}

	// ====== 도메인 예외 — 3 시나리오 ===================================

	@Test
	@DisplayName("verify : 삭제된 ISO → IllegalOSMetadataStateException")
	void verify_deletedIso_throws(@TempDir Path tempDir) {
		Path isoPath = tempDir.resolve("dvd.iso");
		OSMetadata parent = activeParent(1L);
		ISO iso = ISO.builder()
				.id(2L).osMetadata(parent).isoPath(isoPath.toString())
				.checksum("h").manifestHash("h").markerSignature("sig")
				.isEnabled(true).isDeleted(true).build();

		given(osMetadataRepository.findByIdAndIsDeletedFalse(1L)).willReturn(Optional.of(parent));
		given(isoRepository.findByIdAndOsMetadata_Id(2L, 1L)).willReturn(Optional.of(iso));

		assertThatThrownBy(() -> isoIntegrityService.verify(1L, 2L))
				.isInstanceOf(IllegalOSMetadataStateException.class)
				.hasMessageContaining("isoId=2");
	}

	@Test
	@DisplayName("verify : 존재하지 않는 ISO → ISONotFoundException")
	void verify_unknownIso_throws() {
		OSMetadata parent = activeParent(1L);

		given(osMetadataRepository.findByIdAndIsDeletedFalse(1L)).willReturn(Optional.of(parent));
		given(isoRepository.findByIdAndOsMetadata_Id(99L, 1L)).willReturn(Optional.empty());

		assertThatThrownBy(() -> isoIntegrityService.verify(1L, 99L))
				.isInstanceOf(ISONotFoundException.class);
	}

	@Test
	@DisplayName("verify : 존재하지 않는 OS → OSMetadataNotFoundException")
	void verify_unknownOs_throws() {
		given(osMetadataRepository.findByIdAndIsDeletedFalse(404L)).willReturn(Optional.empty());

		assertThatThrownBy(() -> isoIntegrityService.verify(404L, 1L))
				.isInstanceOf(OSMetadataNotFoundException.class);
	}
}
