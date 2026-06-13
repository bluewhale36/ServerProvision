package com.example.serverprovision.global.orphan.service;

import com.example.serverprovision.global.exception.TypedNameMismatchException;
import com.example.serverprovision.global.marker.ResourceType;
import com.example.serverprovision.global.orphan.OrphanRecoverySpi;
import com.example.serverprovision.global.orphan.dto.OrphanRetryResponse;
import com.example.serverprovision.global.orphan.entity.OrphanQuarantine;
import com.example.serverprovision.global.orphan.enums.OrphanFailureClass;
import com.example.serverprovision.global.orphan.enums.OrphanRecoveryState;
import com.example.serverprovision.global.orphan.exception.OrphanRecoveryAlreadyResolvedException;
import com.example.serverprovision.global.orphan.repository.OrphanQuarantineRepository;
import com.example.serverprovision.global.trash.TrashService;
import com.example.serverprovision.management.os.service.iso.IsoRecoveryPayload;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * R1-4-4 — OrphanRecoveryService(action-side) 단위 테스트 :
 * 재시도(복원+SPI 위임) · 멱등(이미 active 면 relocate skip) · 폐기 typed-name 가드 · already-resolved 차단.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class OrphanRecoveryServiceTest {

	@Mock OrphanQuarantineRepository repository;
	@Mock TrashService trashService;
	@Mock OrphanRecoverySpi isoSpi;

	OrphanRecoveryService service;

	@TempDir Path tmp;

	@BeforeEach
	void setUp() {
		given(isoSpi.supportedType()).willReturn(ResourceType.OS_ISO);
		service = new OrphanRecoveryService(repository, trashService, List.of(isoSpi));
	}

	private OrphanQuarantine.OrphanQuarantineBuilder pending(String resolvedPath, String quarantinePath) {
		return OrphanQuarantine.builder()
				.recoveryId("r1").resourceType(ResourceType.OS_ISO).parentId(7L)
				.resolvedPath(resolvedPath).quarantinePath(quarantinePath).originalFilename("rocky.iso")
				.registerExisting(false).payload(new IsoRecoveryPayload("rocky", "hash123"))
				.failureClass(OrphanFailureClass.DB_CONSTRAINT).state(OrphanRecoveryState.PENDING).retryCount(0);
	}

	@Test
	@DisplayName("retry — 격리 파일 복원(active 부재) + SPI 위임, 행 RECOVERED 로 소비")
	void retry_restoresAndDelegates() {
		OrphanQuarantine row = pending("/opt/iso/rocky.iso", "/q/r1/rocky.iso").build();
		given(repository.getByRecoveryIdOrThrow("r1")).willReturn(row);
		given(isoSpi.relaunch(any())).willReturn(new OrphanRetryResponse("job-x", "/management/os?selectId=7"));

		OrphanRetryResponse resp = service.retry("r1");

		assertThat(resp.jobId()).isEqualTo("job-x");
		assertThat(resp.redirect()).contains("selectId=7");
		assertThat(row.getState()).isEqualTo(OrphanRecoveryState.RECOVERED);
		assertThat(row.getRetryCount()).isEqualTo(1);
		// active 경로 부재 → 격리 파일 복원 이동.
		verify(trashService).relocate(Path.of("/q/r1/rocky.iso"), Path.of("/opt/iso/rocky.iso"));
	}

	@Test
	@DisplayName("retry 멱등 — 파일이 이미 active 경로에 있으면 relocate SKIP (복구 경로 FS-DB 비원자성 방어)")
	void retry_idempotent_skipsRelocateWhenActiveExists() throws IOException {
		Path resolved = tmp.resolve("rocky.iso");
		Files.writeString(resolved, "already-here"); // 이전 retry 가 옮겨놨다고 가정
		OrphanQuarantine row = pending(resolved.toString(), tmp.resolve("q/rocky.iso").toString()).build();
		given(repository.getByRecoveryIdOrThrow("r1")).willReturn(row);
		given(isoSpi.relaunch(any())).willReturn(new OrphanRetryResponse("job-y", "/management/os?selectId=7"));

		service.retry("r1");

		verify(trashService, never()).relocate(any(), any()); // 이미 active → skip
		assertThat(row.getState()).isEqualTo(OrphanRecoveryState.RECOVERED);
	}

	@Test
	@DisplayName("retry degraded — quarantinePath=null(격리 실패로 제자리 잔존)은 relocate 없이 제자리 재시도")
	void retry_degraded_inPlaceWithoutRelocate() {
		OrphanQuarantine row = pending("/opt/iso/rocky.iso", null).build();
		given(repository.getByRecoveryIdOrThrow("r1")).willReturn(row);
		given(isoSpi.relaunch(any())).willReturn(new OrphanRetryResponse("job-z", "/management/os?selectId=7"));

		service.retry("r1");

		verify(trashService, never()).relocate(any(), any()); // quarantinePath null → 복원 없음
		verify(isoSpi).relaunch(any());                        // SPI 재등록은 그대로
		assertThat(row.getState()).isEqualTo(OrphanRecoveryState.RECOVERED);
	}

	@Test
	@DisplayName("retry — 해당 ResourceType 의 SPI 미등록이면 IllegalStateException (onboarding 전 진단 가능 실패)")
	void retry_noSpiForType_throws() {
		OrphanRecoveryService noSpi = new OrphanRecoveryService(repository, trashService, List.of());
		OrphanQuarantine row = pending("/opt/iso/rocky.iso", "/q/r1/rocky.iso").build();
		given(repository.getByRecoveryIdOrThrow("r1")).willReturn(row);

		assertThatThrownBy(() -> noSpi.retry("r1")).isInstanceOf(IllegalStateException.class);
	}

	@Test
	@DisplayName("retry — 이미 RECOVERED/DISCARDED 면 OrphanRecoveryAlreadyResolvedException(409)")
	void retry_alreadyResolved_throws() {
		OrphanQuarantine row = pending("/opt/iso/rocky.iso", "/q/r1/rocky.iso")
				.state(OrphanRecoveryState.RECOVERED).build();
		given(repository.getByRecoveryIdOrThrow("r1")).willReturn(row);

		assertThatThrownBy(() -> service.retry("r1"))
				.isInstanceOf(OrphanRecoveryAlreadyResolvedException.class);
		verify(trashService, never()).relocate(any(), any());
	}

	@Test
	@DisplayName("discard — 파일명 불일치면 TypedNameMismatchException, 행/파일 보존")
	void discard_typedNameMismatch_throws() {
		OrphanQuarantine row = pending("/opt/iso/rocky.iso", "/q/r1/rocky.iso").build();
		given(repository.getByRecoveryIdOrThrow("r1")).willReturn(row);

		assertThatThrownBy(() -> service.discard("r1", "wrong.iso"))
				.isInstanceOf(TypedNameMismatchException.class);
		assertThat(row.getState()).isEqualTo(OrphanRecoveryState.PENDING);
	}

	@Test
	@DisplayName("discard — 파일명 일치 시 격리 파일 삭제 + DISCARDED")
	void discard_success_deletesAndMarks() throws IOException {
		Path q = tmp.resolve("q/rocky.iso");
		Files.createDirectories(q.getParent());
		Files.writeString(q, "quarantined");
		OrphanQuarantine row = pending("/opt/iso/rocky.iso", q.toString()).build();
		given(repository.getByRecoveryIdOrThrow("r1")).willReturn(row);

		service.discard("r1", "rocky.iso");

		assertThat(row.getState()).isEqualTo(OrphanRecoveryState.DISCARDED);
		assertThat(Files.exists(q)).isFalse();
	}
}
