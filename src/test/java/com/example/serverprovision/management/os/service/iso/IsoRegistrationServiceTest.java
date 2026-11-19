package com.example.serverprovision.management.os.service.iso;

import com.example.serverprovision.global.job.service.BackgroundJobService;
import com.example.serverprovision.global.marker.MarkerLayout;
import com.example.serverprovision.global.marker.service.ProvisionMarkerService;
import com.example.serverprovision.global.security.FileSystemHardener;
import com.example.serverprovision.global.security.PathPolicyService;
import com.example.serverprovision.management.os.entity.ISO;
import com.example.serverprovision.management.os.entity.OSMetadata;
import com.example.serverprovision.management.os.enums.OSName;
import com.example.serverprovision.management.os.dto.request.ISOCreateRequest;
import com.example.serverprovision.management.os.exception.DuplicateISOContentException;
import com.example.serverprovision.management.os.exception.IsoClientHashMismatchException;
import com.example.serverprovision.management.os.exception.IsoPathIsDirectoryException;
import com.example.serverprovision.management.os.repository.ISORepository;
import com.example.serverprovision.management.os.repository.OSMetadataRepository;
import com.example.serverprovision.management.os.service.iso.IsoRegistrationService.PreparedIsoRegistration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

/**
 * R1-4-2 CP4 — IsoRegistrationService 단위 테스트.
 *
 * <p>등록 흐름의 도메인 분기를 service 단에서 직접 검증한다 (HTTP 매핑은 OSMetadataControllerUploadFlowTest 가
 * 보완). FS 를 건드리는 finalize 분기는 {@code @TempDir} 로 실제 임시 파일을 써서 hash 재계산 경로까지 태운다.</p>
 */
@ExtendWith(MockitoExtension.class)
class IsoRegistrationServiceTest {

	@Mock OSMetadataRepository osMetadataRepository;
	@Mock ISORepository isoRepository;
	@Mock ProvisionMarkerService markerService;
	@Mock BackgroundJobService backgroundJobService;
	@Mock PathPolicyService pathPolicyService;
	@Mock FileSystemHardener fileSystemHardener;

	IsoRegistrationService service;

	@BeforeEach
	void setUp() {
		// postCreationTaskStrategies 는 빈 real list — triggerPostCreationTask 가 short-circuit.
		service = new IsoRegistrationService(
				osMetadataRepository, isoRepository, markerService, backgroundJobService,
				pathPolicyService, fileSystemHardener, List.of());
	}

	private OSMetadata parent(Long id) {
		return OSMetadata.builder()
				.id(id).osName(OSName.ROCKY_LINUX_9).osVersion("9.5")
				.isEnabled(true).isDeleted(false).isDeprecated(false).build();
	}

	private ISO iso(Long id, OSMetadata p) {
		return ISO.builder()
				.id(id).osMetadata(p).isoPath("/opt/iso/" + id + ".iso")
				.checksum("h" + id).manifestHash("h" + id)
				.isEnabled(true).isDeleted(false).isDeprecated(false).build();
	}

	// ==== finalize (실제 임시 파일 — @TempDir) ==========================

	@Test
	@DisplayName("finalize(happy) : 충돌 없음 → 영속화 + 마커 + isoId 반환")
	void finalize_happy(@TempDir Path dir) throws Exception {
		Path isoFile = dir.resolve("dvd.iso");
		Files.writeString(isoFile, "iso-bytes");
		OSMetadata p = parent(1L);
		given(osMetadataRepository.findByIdAndIsDeletedFalse(1L)).willReturn(Optional.of(p));
		given(isoRepository.findFirstByOsMetadata_IdAndIsoPathAndIsDeletedFalse(1L, isoFile.toString())).willReturn(Optional.empty());
		given(isoRepository.findAllByOsMetadata_IdAndManifestHashAndIsDeletedFalse(eq(1L), anyString())).willReturn(List.of());
		given(isoRepository.save(any(ISO.class))).willReturn(iso(303L, p));
		var prepared = new PreparedIsoRegistration(1L, isoFile.toString(), "desc", "dvd.iso", true);

		Long id = service.finalize(null, prepared);

		assertThat(id).isEqualTo(303L);
		verify(isoRepository).save(any(ISO.class));
	}

	@Test
	@DisplayName("finalize : client hash 불일치 → IsoClientHashMismatchException + 임시파일 정리")
	void finalize_clientHashMismatch_throws(@TempDir Path dir) throws Exception {
		Path isoFile = dir.resolve("dvd.iso");
		Files.writeString(isoFile, "iso-bytes");
		given(osMetadataRepository.findByIdAndIsDeletedFalse(1L)).willReturn(Optional.of(parent(1L)));
		given(markerService.resolveMarkerFile(any(Path.class), eq(MarkerLayout.SIDECAR)))
				.willReturn(dir.resolve("dvd.iso.provision.json"));
		var prepared = new PreparedIsoRegistration(1L, isoFile.toString(), "desc", "dvd.iso", true, "deadbeef");

		assertThatThrownBy(() -> service.finalize(null, prepared))
				.isInstanceOf(IsoClientHashMismatchException.class);
	}

	@Test
	@DisplayName("finalize : 같은 OS 의 활성 ISO 와 내용이 같음 → DuplicateISOContentException(활성 안내)")
	void finalize_activeDup_throws(@TempDir Path dir) throws Exception {
		Path isoFile = dir.resolve("dvd.iso");
		Files.writeString(isoFile, "iso-bytes");
		given(osMetadataRepository.findByIdAndIsDeletedFalse(1L)).willReturn(Optional.of(parent(1L)));
		given(isoRepository.findFirstByOsMetadata_IdAndIsoPathAndIsDeletedFalse(1L, isoFile.toString())).willReturn(Optional.empty());
		given(isoRepository.findAllByOsMetadata_IdAndManifestHashAndIsDeletedFalse(eq(1L), anyString())).willReturn(List.of(iso(9L, parent(1L))));
		given(markerService.resolveMarkerFile(any(Path.class), eq(MarkerLayout.SIDECAR)))
				.willReturn(dir.resolve("dvd.iso.provision.json"));
		var prepared = new PreparedIsoRegistration(1L, isoFile.toString(), "desc", "dvd.iso", true);

		assertThatThrownBy(() -> service.finalize(null, prepared))
				.isInstanceOf(DuplicateISOContentException.class);
	}

	@Test
	@DisplayName("finalize : 휴지통의 같은 내용은 창 없이 등록한다(HF30-2 — 업로드 전 창이 이미 물었다)")
	void finalize_trashedSameContent_registers(@TempDir Path dir) throws Exception {
		Path isoFile = dir.resolve("dvd.iso");
		Files.writeString(isoFile, "iso-bytes");
		OSMetadata p = parent(1L);
		given(osMetadataRepository.findByIdAndIsDeletedFalse(1L)).willReturn(Optional.of(p));
		given(isoRepository.findFirstByOsMetadata_IdAndIsoPathAndIsDeletedFalse(1L, isoFile.toString())).willReturn(Optional.empty());
		given(isoRepository.findAllByOsMetadata_IdAndManifestHashAndIsDeletedFalse(eq(1L), anyString())).willReturn(List.of());
		given(isoRepository.save(any(ISO.class))).willReturn(iso(304L, p));
		var prepared = new PreparedIsoRegistration(1L, isoFile.toString(), "desc", "dvd.iso", true);

		Long id = service.finalize(null, prepared);

		assertThat(id).isEqualTo(304L);
	}

	// ==== prepare (HF4-3 F-4 — 디렉토리 경로 가드) ======================

	@Test
	@DisplayName("S23-1 prepare : 부모 OS 가 미지원 상수(CentOS)면 경로를 보기 전에 UnsupportedOsIsoRegistrationException")
	void prepare_legacyOs_throws() {
		given(osMetadataRepository.findByIdAndIsDeletedFalse(1L)).willReturn(Optional.of(OSMetadata.builder()
				.id(1L).osName(OSName.CENTOS).osVersion("7.9").isEnabled(true).isDeleted(false).isDeprecated(false).build()));
		var request = new ISOCreateRequest("/opt/iso/c.iso", "desc", false);

		assertThatThrownBy(() -> service.prepare(1L, request, null))
				.isInstanceOf(com.example.serverprovision.management.os.exception.UnsupportedOsIsoRegistrationException.class);
		org.mockito.Mockito.verifyNoInteractions(pathPolicyService);
	}

	@Test
	@DisplayName("prepare : resolved 경로가 기존 디렉토리 → IsoPathIsDirectoryException (field=isoPath)")
	void prepare_directoryPath_throws(@TempDir Path dir) {
		given(osMetadataRepository.findByIdAndIsDeletedFalse(1L)).willReturn(Optional.of(parent(1L)));
		given(pathPolicyService.assertWritablePath(dir.toString())).willReturn(dir);
		var request = new ISOCreateRequest(dir.toString(), "desc", false);
		var file = new MockMultipartFile("file", "dvd.iso", "application/octet-stream", new byte[]{1});

		IsoPathIsDirectoryException thrown = catchThrowableOfType(
				() -> service.prepare(1L, request, file), IsoPathIsDirectoryException.class);

		assertThat(thrown.fieldName()).isEqualTo("isoPath");
		assertThat(thrown.getMessage()).contains("디렉토리");
	}

	@Test
	@DisplayName("prepare : 미존재 파일 경로 + 업로드 파일 → 저장 후 PreparedIsoRegistration 반환 (happy)")
	void prepare_uploadToNewPath_happy(@TempDir Path dir) throws Exception {
		Path target = dir.resolve("new.iso");
		given(osMetadataRepository.findByIdAndIsDeletedFalse(1L)).willReturn(Optional.of(parent(1L)));
		given(pathPolicyService.assertWritablePath(target.toString())).willReturn(target);
		given(isoRepository.findFirstByOsMetadata_IdAndIsoPathAndIsDeletedFalse(1L, target.toString()))
				.willReturn(Optional.empty());
		given(markerService.resolveMarkerFile(target, MarkerLayout.SIDECAR))
				.willReturn(dir.resolve("new.iso.provision.json"));
		var request = new ISOCreateRequest(target.toString(), "desc", false);
		var file = new MockMultipartFile("file", "dvd.iso", "application/octet-stream", "iso-bytes".getBytes());

		var prepared = service.prepare(1L, request, file);

		assertThat(prepared.resolvedPath()).isEqualTo(target.toString());
		assertThat(prepared.uploadedFile()).isTrue();
		assertThat(Files.exists(target)).isTrue();
	}
}
