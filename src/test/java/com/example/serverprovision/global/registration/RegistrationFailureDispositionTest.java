package com.example.serverprovision.global.registration;

import com.example.serverprovision.global.orphan.enums.OrphanFailureClass;
import com.example.serverprovision.management.common.nudge.dto.NudgeRequiredResponse;
import com.example.serverprovision.management.os.exception.DuplicateFilenameException;
import com.example.serverprovision.management.os.exception.DuplicateISOContentException;
import com.example.serverprovision.management.os.exception.ISOFileStorageException;
import com.example.serverprovision.management.os.exception.IsoClientHashMismatchException;
import com.example.serverprovision.management.os.exception.IsoMarkerWriteFailedException;
import com.example.serverprovision.management.os.exception.IsoNudgeRequiredException;
import com.example.serverprovision.management.os.exception.IsoUploadIntentConflictException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * R1-4-4 — ISO 등록 실패 예외 7종의 {@link RegistrationFailure#disposition()} 진리표.
 * 처분 다형성이 Runner 의 dispatch 와 1:1 이어야 하므로(드리프트 0), 각 예외의 처분을 못박는다.
 */
class RegistrationFailureDispositionTest {

	@Test
	@DisplayName("콘텐츠/영구 실패 4종 → CLEANUP")
	void contentFailures_areCleanup() {
		assertThat(new IsoClientHashMismatchException("a", "b").disposition())
				.isInstanceOf(FailureDisposition.Cleanup.class);
		assertThat(new DuplicateISOContentException("/opt/iso/x.iso").disposition())
				.isInstanceOf(FailureDisposition.Cleanup.class);
		assertThat(new DuplicateFilenameException("/opt/iso/x.iso").disposition())
				.isInstanceOf(FailureDisposition.Cleanup.class);
		assertThat(new IsoUploadIntentConflictException("conflict").disposition())
				.isInstanceOf(FailureDisposition.Cleanup.class);
	}

	@Test
	@DisplayName("저장 IO 실패 → QUARANTINE(STORAGE_IO)")
	void storageFailure_isQuarantineStorageIo() {
		FailureDisposition d = new ISOFileStorageException("io", new IOException()).disposition();
		assertThat(d).isInstanceOf(FailureDisposition.Quarantine.class);
		assertThat(((FailureDisposition.Quarantine) d).failureClass()).isEqualTo(OrphanFailureClass.STORAGE_IO);
	}

	@Test
	@DisplayName("마커 기록 실패 → QUARANTINE(MARKER_WRITE)")
	void markerFailure_isQuarantineMarkerWrite() {
		FailureDisposition d = new IsoMarkerWriteFailedException("marker", new RuntimeException()).disposition();
		assertThat(d).isInstanceOf(FailureDisposition.Quarantine.class);
		assertThat(((FailureDisposition.Quarantine) d).failureClass()).isEqualTo(OrphanFailureClass.MARKER_WRITE);
	}

	@Test
	@DisplayName("nudge 필요 → NUDGE(nudgeId), nudgeId 는 payload 의 UUID 를 lossless 전달")
	void nudge_isNudgeWithUuid() {
		UUID nudgeId = UUID.randomUUID();
		NudgeRequiredResponse payload = NudgeRequiredResponse.of(nudgeId, List.of(), Instant.now());

		FailureDisposition d = new IsoNudgeRequiredException(payload).disposition();

		assertThat(d).isInstanceOf(FailureDisposition.Nudge.class);
		assertThat(((FailureDisposition.Nudge) d).nudgeId()).isEqualTo(nudgeId);
	}
}
