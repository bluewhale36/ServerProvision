package com.example.serverprovision.management.os.service.iso;

import com.example.serverprovision.global.job.service.BackgroundJobService;
import com.example.serverprovision.management.os.enums.OrphanFailureClass;
import com.example.serverprovision.management.os.exception.DuplicateFilenameException;
import com.example.serverprovision.management.os.exception.DuplicateISOContentException;
import com.example.serverprovision.management.os.exception.ISOFileStorageException;
import com.example.serverprovision.management.os.exception.IsoClientHashMismatchException;
import com.example.serverprovision.management.os.exception.IsoMarkerWriteFailedException;
import com.example.serverprovision.management.os.exception.IsoNudgeRequiredException;
import com.example.serverprovision.management.os.exception.IsoUploadIntentConflictException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * ISO 등록 후처리 비동기 실행기.
 * 업로드/경로 검증이 끝난 뒤 SHA-256 계산·중복 검사·DB/marker 저장을 별도 스레드에서 수행한다.
 *
 * <p>실패는 <b>예외 타입별 catch(Java dispatch)</b> 로 분류한다(분기문 무분별 확장 회피):</p>
 * <ul>
 *   <li>NUDGE — soft-deleted/deprecated 충돌. 임시 파일 보존, {@code NUDGE_REQUIRED:<id>} marker.</li>
 *   <li>CONTENT/PERMANENT(해시 불일치·중복·경로/파일명 충돌) — finalize 가 이미 파일 정리. 격리하지 않고 fail.</li>
 *   <li>INFRA/TRANSIENT(저장 IO·DB 제약·마커 기록) + 미분류 — 파일을 <b>삭제하지 않고 격리</b> + durable 기록 후
 *       {@code ORPHAN_RECOVERY:<recoveryId>} marker (재시도/폐기 가능). 대용량 업로드를 일시 장애로 잃지 않기 위함.</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class IsoRegistrationRunner {

	/** UI 가 fail 메시지에서 nudge 흐름임을 인식하는 prefix. */
	public static final String NUDGE_FAIL_PREFIX = "NUDGE_REQUIRED:";
	/** UI 가 fail 메시지에서 오펀 복구 흐름임을 인식하는 prefix. */
	public static final String ORPHAN_FAIL_PREFIX = "ORPHAN_RECOVERY:";

	private final IsoRegistrationService isoRegistrationService;
	private final BackgroundJobService backgroundJobService;
	private final OrphanIsoRecoveryService orphanIsoRecoveryService;

	@Async
	public void runAsync(String jobId, IsoRegistrationService.PreparedIsoRegistration prepared) {
		try {
			backgroundJobService.startStage(jobId, IsoRegistrationStage.COMPUTE_HASH);
			Long isoId = isoRegistrationService.finalize(jobId, prepared);
			backgroundJobService.complete(jobId);
			log.info("[IsoRegistrationRunner] ISO 등록 완료. jobId={}, isoId={}, path={}",
					jobId, isoId, prepared.resolvedPath());
		} catch (IsoNudgeRequiredException nudge) {
			// 단계 B nudge — 임시 파일은 confirm 시 정식 등록될 수 있으므로 정리하지 않는다.
			backgroundJobService.fail(jobId, NUDGE_FAIL_PREFIX + nudge.payload().nudgeId());
			log.info("[IsoRegistrationRunner] nudge 필요 — job 일시 fail 표시. jobId={}, nudgeId={}",
					jobId, nudge.payload().nudgeId());
		} catch (IsoClientHashMismatchException | DuplicateISOContentException
				| IsoUploadIntentConflictException | DuplicateFilenameException e) {
			// CONTENT/PERMANENT — finalize 가 이미 우리 업로드 파일을 정리(또는 우리 바이트 아님). 격리하지 않는다.
			log.warn("[IsoRegistrationRunner] 콘텐츠 실패 — 격리 없이 fail. jobId={}, reason={}", jobId, e.getMessage());
			backgroundJobService.fail(jobId, e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName());
		} catch (ISOFileStorageException e) {
			quarantineAndFail(jobId, prepared, OrphanFailureClass.STORAGE_IO, e);
		} catch (DataIntegrityViolationException e) {
			quarantineAndFail(jobId, prepared, OrphanFailureClass.DB_CONSTRAINT, e);
		} catch (IsoMarkerWriteFailedException e) {
			quarantineAndFail(jobId, prepared, OrphanFailureClass.MARKER_WRITE, e);
		} catch (RuntimeException e) {
			// 미분류 → 안전하게 TRANSIENT 취급 (대용량 업로드를 silently 잃지 않기 위해).
			quarantineAndFail(jobId, prepared, OrphanFailureClass.UNEXPECTED, e);
		}
	}

	private void quarantineAndFail(String jobId, IsoRegistrationService.PreparedIsoRegistration prepared,
	                               OrphanFailureClass failureClass, RuntimeException cause) {
		String detail = cause.getMessage() != null ? cause.getMessage() : cause.getClass().getSimpleName();
		try {
			String recoveryId = orphanIsoRecoveryService.recordOrphan(prepared, failureClass, detail, jobId);
			backgroundJobService.fail(jobId, ORPHAN_FAIL_PREFIX + recoveryId);
			log.error("[IsoRegistrationRunner] ISO 등록 실패 — 격리 후 복구 대기. jobId={}, recoveryId={}, class={}, path={}",
					jobId, recoveryId, failureClass, prepared.resolvedPath(), cause);
		} catch (RuntimeException recordError) {
			// 격리 기록 자체 실패 — 파일은 절대 삭제하지 않고, job 은 원래 오류로 fail.
			log.error("[IsoRegistrationRunner] 격리 기록 실패 — 원래 오류로 fail. jobId={}, path={}",
					jobId, prepared.resolvedPath(), recordError);
			backgroundJobService.fail(jobId, detail);
		}
	}
}
