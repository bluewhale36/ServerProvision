package com.example.serverprovision.management.os.service.iso;

import com.example.serverprovision.global.job.service.BackgroundJobService;
import com.example.serverprovision.global.orphan.OrphanQuarantineRequest;
import com.example.serverprovision.global.orphan.enums.OrphanFailureClass;
import com.example.serverprovision.global.orphan.service.OrphanQuarantineService;
import com.example.serverprovision.management.common.nudge.dto.NudgeRequiredResponse;
import com.example.serverprovision.management.os.exception.DuplicateISOContentException;
import com.example.serverprovision.management.os.exception.ISOFileStorageException;
import com.example.serverprovision.management.os.exception.IsoNudgeRequiredException;
import com.example.serverprovision.management.os.service.iso.IsoRegistrationService.PreparedIsoRegistration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.dao.DataIntegrityViolationException;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * R1-4-4 — Runner 의 disposition() dispatch 검증. 각 처분이 올바른 행동(정리없이 fail / nudge marker /
 * 격리 record)으로 분기하는지, DataIntegrity 어댑터 경로가 DB_CONSTRAINT 를 부여하는지, 격리 record 실패 시
 * 원래 사유로 fail(파일 미삭제)하는지 못박는다.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class IsoRegistrationRunnerDispatchTest {

	@Mock IsoRegistrationService isoRegistrationService;
	@Mock BackgroundJobService backgroundJobService;
	@Mock OrphanQuarantineService orphanQuarantineService;

	@InjectMocks IsoRegistrationRunner runner;

	private static final PreparedIsoRegistration PREPARED =
			new PreparedIsoRegistration(7L, "/opt/iso/rocky.iso", "rocky", "rocky.iso", true, "hash123");

	@Test
	@DisplayName("CLEANUP — 콘텐츠 실패는 격리 없이 fail (record 미호출, ORPHAN/NUDGE prefix 없음)")
	void cleanup_failsWithoutQuarantine() throws Exception {
		given(isoRegistrationService.finalize(eq("job1"), any()))
				.willThrow(new DuplicateISOContentException("/opt/iso/existing.iso"));

		runner.runAsync("job1", PREPARED);

		verify(orphanQuarantineService, never()).record(any());
		ArgumentCaptor<String> msg = ArgumentCaptor.forClass(String.class);
		verify(backgroundJobService).fail(eq("job1"), msg.capture());
		assertThat(msg.getValue()).doesNotStartWith("ORPHAN_RECOVERY:").doesNotStartWith("NUDGE_REQUIRED:");
	}

	@Test
	@DisplayName("NUDGE — nudge 예외는 NUDGE_REQUIRED:{nudgeId} marker, record 미호출")
	void nudge_failsWithNudgeMarker() throws Exception {
		UUID nudgeId = UUID.randomUUID();
		NudgeRequiredResponse payload = NudgeRequiredResponse.of(nudgeId, List.of(), Instant.now());
		given(isoRegistrationService.finalize(eq("job1"), any()))
				.willThrow(new IsoNudgeRequiredException(payload));

		runner.runAsync("job1", PREPARED);

		verify(backgroundJobService).fail("job1", "NUDGE_REQUIRED:" + nudgeId);
		verify(orphanQuarantineService, never()).record(any());
	}

	@Test
	@DisplayName("QUARANTINE(STORAGE_IO) — 저장 IO 실패는 OS_ISO 격리 + ORPHAN_RECOVERY:{id} marker")
	void quarantine_storageIo() throws Exception {
		given(isoRegistrationService.finalize(eq("job1"), any()))
				.willThrow(new ISOFileStorageException("io fail", new IOException()));
		given(orphanQuarantineService.record(any())).willReturn("rec-1");

		runner.runAsync("job1", PREPARED);

		ArgumentCaptor<OrphanQuarantineRequest> req = ArgumentCaptor.forClass(OrphanQuarantineRequest.class);
		verify(orphanQuarantineService).record(req.capture());
		assertThat(req.getValue().failureClass()).isEqualTo(OrphanFailureClass.STORAGE_IO);
		assertThat(req.getValue().resourceType()).isEqualTo(com.example.serverprovision.global.marker.ResourceType.OS_ISO);
		assertThat(req.getValue().parentId()).isEqualTo(7L);
		verify(backgroundJobService).fail("job1", "ORPHAN_RECOVERY:rec-1");
	}

	@Test
	@DisplayName("DataIntegrity 어댑터 — Spring 예외는 disposition 우회 catch 로 DB_CONSTRAINT 격리")
	void dataIntegrity_adapterMapsToDbConstraint() throws Exception {
		given(isoRegistrationService.finalize(eq("job1"), any()))
				.willThrow(new DataIntegrityViolationException("dup key"));
		given(orphanQuarantineService.record(any())).willReturn("rec-2");

		runner.runAsync("job1", PREPARED);

		ArgumentCaptor<OrphanQuarantineRequest> req = ArgumentCaptor.forClass(OrphanQuarantineRequest.class);
		verify(orphanQuarantineService).record(req.capture());
		assertThat(req.getValue().failureClass()).isEqualTo(OrphanFailureClass.DB_CONSTRAINT);
	}

	@Test
	@DisplayName("미분류 RuntimeException — UNEXPECTED 로 안전 격리")
	void unclassified_isUnexpectedQuarantine() throws Exception {
		given(isoRegistrationService.finalize(eq("job1"), any()))
				.willThrow(new IllegalStateException("boom"));
		given(orphanQuarantineService.record(any())).willReturn("rec-3");

		runner.runAsync("job1", PREPARED);

		ArgumentCaptor<OrphanQuarantineRequest> req = ArgumentCaptor.forClass(OrphanQuarantineRequest.class);
		verify(orphanQuarantineService).record(req.capture());
		assertThat(req.getValue().failureClass()).isEqualTo(OrphanFailureClass.UNEXPECTED);
	}

	@Test
	@DisplayName("격리 record 자체 실패 — 원래 사유로 fail (ORPHAN prefix 없음, 파일 미삭제 보장)")
	void recordThrows_failsWithOriginalDetail() throws Exception {
		given(isoRegistrationService.finalize(eq("job1"), any()))
				.willThrow(new ISOFileStorageException("io fail", new IOException()));
		given(orphanQuarantineService.record(any())).willThrow(new RuntimeException("db down"));

		runner.runAsync("job1", PREPARED);

		verify(backgroundJobService).fail("job1", "io fail");
	}
}
