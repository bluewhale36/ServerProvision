package com.example.serverprovision.global.job;

import com.example.serverprovision.global.job.service.BackgroundJobService;
import com.example.serverprovision.global.job.stage.IntegrityVerificationStage;
import com.example.serverprovision.global.marker.IntegrityStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * {@link IntegrityJobReporter} 의 stage 전이 SSOT 검증.
 *
 * <p>R5-2 — 4 verification launcher 에 복제됐던 applyStatus switch 를 단일 {@code @Component} 로 추출했다.
 * 추출 전 동작(상태별 BackgroundJob 부수효과)이 보존됐는지 mock 호출 시퀀스를 {@link InOrder} 로 검증한다.</p>
 */
@ExtendWith(MockitoExtension.class)
class IntegrityJobReporterTest {

	private static final String JOB_ID = "job-1";

	@Mock
	private BackgroundJobService backgroundJobService;

	@InjectMocks
	private IntegrityJobReporter integrityJobReporter;

	@Test
	@DisplayName("ORIGINAL: completeStage → startStage(RECOMPUTE_HASH) → completeStage → complete (fail 미호출)")
	void report_original_completesBothStagesThenComplete() {
		integrityJobReporter.report(JOB_ID, IntegrityStatus.ORIGINAL);

		InOrder order = inOrder(backgroundJobService);
		order.verify(backgroundJobService).completeStage(JOB_ID);
		order.verify(backgroundJobService).startStage(JOB_ID, IntegrityVerificationStage.RECOMPUTE_HASH);
		order.verify(backgroundJobService).completeStage(JOB_ID);
		order.verify(backgroundJobService).complete(JOB_ID);
		order.verifyNoMoreInteractions();
		// 정상 경로에서는 fail 이 절대 호출되지 않아야 한다.
		verify(backgroundJobService, never()).fail(anyString(), anyString());
	}

	@Test
	@DisplayName("TAMPERED: completeStage → startStage(RECOMPUTE_HASH) → fail(\"변조 감지 (해시 불일치)\")")
	void report_tampered_advancesToHashStageThenFail() {
		integrityJobReporter.report(JOB_ID, IntegrityStatus.TAMPERED);

		InOrder order = inOrder(backgroundJobService);
		order.verify(backgroundJobService).completeStage(JOB_ID);
		order.verify(backgroundJobService).startStage(JOB_ID, IntegrityVerificationStage.RECOMPUTE_HASH);
		order.verify(backgroundJobService).fail(JOB_ID, "변조 감지 (해시 불일치)");
		order.verifyNoMoreInteractions();
	}

	@Test
	@DisplayName("SIGNATURE_INVALID: fail(\"서명 무효\") 단일 호출 (stage 미전이)")
	void report_signatureInvalid_failsImmediately() {
		integrityJobReporter.report(JOB_ID, IntegrityStatus.SIGNATURE_INVALID);

		InOrder order = inOrder(backgroundJobService);
		order.verify(backgroundJobService).fail(JOB_ID, "서명 무효");
		order.verifyNoMoreInteractions();
	}

	@Test
	@DisplayName("MARKER_MISSING: fail(\"마커 파일 없음\") 단일 호출 (stage 미전이)")
	void report_markerMissing_failsImmediately() {
		integrityJobReporter.report(JOB_ID, IntegrityStatus.MARKER_MISSING);

		InOrder order = inOrder(backgroundJobService);
		order.verify(backgroundJobService).fail(JOB_ID, "마커 파일 없음");
		order.verifyNoMoreInteractions();
	}

	@Test
	@DisplayName("NOT_VERIFIED: fail(\"검증 결과를 받지 못했습니다.\") 단일 호출 (별도 안내 문구)")
	void report_notVerified_failsWithDedicatedMessage() {
		integrityJobReporter.report(JOB_ID, IntegrityStatus.NOT_VERIFIED);

		InOrder order = inOrder(backgroundJobService);
		order.verify(backgroundJobService).fail(JOB_ID, "검증 결과를 받지 못했습니다.");
		order.verifyNoMoreInteractions();
	}
}
