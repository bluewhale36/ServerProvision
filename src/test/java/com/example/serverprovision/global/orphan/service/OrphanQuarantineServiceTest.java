package com.example.serverprovision.global.orphan.service;

import com.example.serverprovision.global.marker.MarkerLayout;
import com.example.serverprovision.global.marker.ResourceType;
import com.example.serverprovision.global.marker.service.ProvisionMarkerService;
import com.example.serverprovision.global.orphan.OrphanQuarantineRequest;
import com.example.serverprovision.global.orphan.entity.OrphanQuarantine;
import com.example.serverprovision.global.orphan.enums.OrphanFailureClass;
import com.example.serverprovision.global.orphan.enums.OrphanRecoveryState;
import com.example.serverprovision.global.orphan.repository.OrphanQuarantineRepository;
import com.example.serverprovision.global.trash.TrashService;
import com.example.serverprovision.management.os.service.iso.IsoRecoveryPayload;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * R1-4-4 — OrphanQuarantineService(write-side) 단위 테스트 :
 * 격리 happy · 격리 이동 실패 degraded(파일 원위치) · in-place 비격리 · TTL purge.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class OrphanQuarantineServiceTest {

	@Mock OrphanQuarantineRepository repository;
	@Mock TrashService trashService;
	@Mock ProvisionMarkerService markerService;

	OrphanQuarantineService service;

	@TempDir Path tmp;

	@BeforeEach
	void setUp() {
		service = new OrphanQuarantineService(repository, trashService, markerService);
		ReflectionTestUtils.setField(service, "quarantineRoot", tmp.resolve("q").toString());
		ReflectionTestUtils.setField(service, "ttlDays", 7L);
	}

	private OrphanQuarantineRequest request(String resolvedPath, boolean uploaded, OrphanFailureClass fc) {
		return new OrphanQuarantineRequest(
				ResourceType.OS_ISO, 7L, resolvedPath, uploaded, "rocky.iso",
				new IsoRecoveryPayload("rocky", "hash123"), fc, "boom", "job-1");
	}

	@Test
	@DisplayName("record — 업로드 파일을 격리(mv)하고 PENDING row 저장 (SIDECAR 마커 제거)")
	void record_uploaded_quarantinesAndSaves() throws IOException {
		Path resolved = tmp.resolve("rocky.iso");
		Files.writeString(resolved, "iso-bytes");
		given(markerService.resolveMarkerFile(any(), eq(MarkerLayout.SIDECAR)))
				.willReturn(tmp.resolve("rocky.iso.provision.json"));

		String recoveryId = service.record(request(resolved.toString(), true, OrphanFailureClass.STORAGE_IO));

		assertThat(recoveryId).isNotBlank();
		verify(trashService).relocate(eq(resolved), any(Path.class));
		// SIDECAR 자원이므로 형제 마커 제거 시도.
		verify(markerService).resolveMarkerFile(eq(resolved), eq(MarkerLayout.SIDECAR));

		ArgumentCaptor<OrphanQuarantine> captor = ArgumentCaptor.forClass(OrphanQuarantine.class);
		verify(repository).save(captor.capture());
		OrphanQuarantine saved = captor.getValue();
		assertThat(saved.getResourceType()).isEqualTo(ResourceType.OS_ISO);
		assertThat(saved.getParentId()).isEqualTo(7L);
		assertThat(saved.getOriginalFilename()).isEqualTo("rocky.iso");
		assertThat(saved.isRegisterExisting()).isFalse();
		assertThat(saved.getPayload()).isInstanceOf(IsoRecoveryPayload.class);
		assertThat(saved.getFailureClass()).isEqualTo(OrphanFailureClass.STORAGE_IO);
		assertThat(saved.getState()).isEqualTo(OrphanRecoveryState.PENDING);
		assertThat(saved.getRetryCount()).isZero();
		assertThat(saved.getQuarantinePath()).isNotNull();
	}

	@Test
	@DisplayName("record degraded — 격리 이동 실패 시 파일 원위치 유지(quarantinePath=null), 절대 삭제 안 함")
	void record_relocateFails_keepsInPlace() throws IOException {
		Path resolved = tmp.resolve("rocky.iso");
		Files.writeString(resolved, "iso-bytes");
		willThrow(new RuntimeException("disk full")).given(trashService).relocate(any(), any());

		service.record(request(resolved.toString(), true, OrphanFailureClass.DB_CONSTRAINT));

		assertThat(Files.exists(resolved)).isTrue(); // 절대 삭제 안 함
		ArgumentCaptor<OrphanQuarantine> captor = ArgumentCaptor.forClass(OrphanQuarantine.class);
		verify(repository).save(captor.capture());
		assertThat(captor.getValue().getQuarantinePath()).isNull(); // 이동 실패 → null
	}

	@Test
	@DisplayName("record in-place(운영자 자산) — 격리 이동 없음, registerExisting=true")
	void record_inPlace_noQuarantine() {
		service.record(request("/opt/iso/operator-owned.iso", false, OrphanFailureClass.MARKER_WRITE));

		verify(trashService, never()).relocate(any(), any());
		ArgumentCaptor<OrphanQuarantine> captor = ArgumentCaptor.forClass(OrphanQuarantine.class);
		verify(repository).save(captor.capture());
		assertThat(captor.getValue().isRegisterExisting()).isTrue();
		assertThat(captor.getValue().getQuarantinePath()).isNull();
	}

	@Test
	@DisplayName("record IN_TREE 자원(BIOS) — sidecar 마커 제거 시도 안 함 (layout 다형성의 false-갈래)")
	void record_inTreeResource_noMarkerRemoval() throws IOException {
		Path resolved = tmp.resolve("bios-tree");
		Files.createDirectories(resolved); // IN_TREE 자원은 디렉토리
		OrphanQuarantineRequest req = new OrphanQuarantineRequest(
				ResourceType.BIOS_BUNDLE, 3L, resolved.toString(), true, "bios-v1",
				new IsoRecoveryPayload("bios", "h"), OrphanFailureClass.MARKER_WRITE, "boom", "job-2");

		service.record(req);

		// BIOS_BUNDLE = IN_TREE → 형제 sidecar 마커가 없으므로 제거 시도조차 안 한다(if 누적 대신 다형성).
		verify(markerService, never()).resolveMarkerFile(any(), any());
	}

	@Test
	@DisplayName("originalFilename 폴백 — provided blank + 경로 파일명 없음 → 'unknown' (typed-name 가드 SSOT 고정)")
	void record_blankFilename_fallback() {
		OrphanQuarantineRequest req = new OrphanQuarantineRequest(
				ResourceType.OS_ISO, 1L, "/", false, "   ",
				new IsoRecoveryPayload("x", "y"), OrphanFailureClass.UNEXPECTED, "boom", "job-3");

		service.record(req);

		ArgumentCaptor<OrphanQuarantine> captor = ArgumentCaptor.forClass(OrphanQuarantine.class);
		verify(repository).save(captor.capture());
		assertThat(captor.getValue().getOriginalFilename()).isEqualTo("unknown");
	}

	@Test
	@DisplayName("purgeExpired — TTL 만료 PENDING 격리 파일 삭제 + DISCARDED, 건수 반환")
	void purgeExpired_discardsExpired() throws IOException {
		Path q1 = tmp.resolve("q1.iso");
		Files.writeString(q1, "x");
		OrphanQuarantine row = OrphanQuarantine.builder()
				.recoveryId("r1").resourceType(ResourceType.OS_ISO).parentId(1L)
				.resolvedPath("/opt/iso/r1.iso").quarantinePath(q1.toString()).originalFilename("r1.iso")
				.registerExisting(false).failureClass(OrphanFailureClass.STORAGE_IO)
				.state(OrphanRecoveryState.PENDING).retryCount(0).build();
		given(repository.findByStateAndCreatedAtBefore(eq(OrphanRecoveryState.PENDING), any()))
				.willReturn(List.of(row));

		int purged = service.purgeExpired();

		assertThat(purged).isEqualTo(1);
		assertThat(row.getState()).isEqualTo(OrphanRecoveryState.DISCARDED);
		assertThat(Files.exists(q1)).isFalse(); // 격리 파일 삭제
	}
}
