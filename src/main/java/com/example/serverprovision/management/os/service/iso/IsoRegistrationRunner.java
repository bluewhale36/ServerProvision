package com.example.serverprovision.management.os.service.iso;

import com.example.serverprovision.global.job.service.BackgroundJobService;
import com.example.serverprovision.management.os.exception.IsoNudgeRequiredException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * ISO 등록 후처리 비동기 실행기.
 * 업로드/경로 검증이 끝난 뒤 SHA-256 계산·중복 검사·DB/marker 저장을 별도 스레드에서 수행한다.
 *
 * <p>MK2 — soft-deleted/deprecated 자원과의 해시 충돌이 발견되면 {@link IsoNudgeRequiredException} 이
 * 던져진다. 본 runner 는 fail 메시지에 {@code NUDGE_REQUIRED:<nudgeId>} 식별자를 동봉해 UI 가 알림 센터
 * 에서 그 jobId 를 클릭했을 때 nudge modal 을 띄울 단서를 남긴다.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class IsoRegistrationRunner {

	/**
	 * UI 가 fail 메시지에서 nudge 흐름임을 인식하는 prefix.
	 */
	public static final String NUDGE_FAIL_PREFIX = "NUDGE_REQUIRED:";

	private final IsoRegistrationService isoRegistrationService;
	private final BackgroundJobService backgroundJobService;

	@Async
	public void runAsync(String jobId, IsoRegistrationService.PreparedIsoRegistration prepared) {
		try {
			backgroundJobService.startStage(jobId, IsoRegistrationStage.COMPUTE_HASH);
			Long isoId = isoRegistrationService.finalize(jobId, prepared);
			backgroundJobService.complete(jobId);
			log.info(
					"[IsoRegistrationRunner] ISO 등록 완료. jobId={}, isoId={}, path={}",
					jobId, isoId, prepared.resolvedPath()
			);
		} catch (IsoNudgeRequiredException nudge) {
			// 단계 B nudge 흐름 — 임시 파일은 confirm 시 정식 등록될 수 있으므로 정리하지 않는다.
			String marker = NUDGE_FAIL_PREFIX + nudge.payload().nudgeId();
			backgroundJobService.fail(jobId, marker);
			log.info(
					"[IsoRegistrationRunner] nudge 필요 — job 일시 fail 표시. jobId={}, nudgeId={}",
					jobId, nudge.payload().nudgeId()
			);
		} catch (RuntimeException e) {
			log.error(
					"[IsoRegistrationRunner] ISO 등록 실패. jobId={}, path={}",
					jobId, prepared.resolvedPath(), e
			);
			backgroundJobService.fail(jobId, e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName());
		}
	}
}
