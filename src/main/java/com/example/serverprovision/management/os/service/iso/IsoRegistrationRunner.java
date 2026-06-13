package com.example.serverprovision.management.os.service.iso;

import com.example.serverprovision.global.job.service.BackgroundJobService;
import com.example.serverprovision.global.marker.ResourceType;
import com.example.serverprovision.global.orphan.OrphanQuarantineRequest;
import com.example.serverprovision.global.orphan.enums.OrphanFailureClass;
import com.example.serverprovision.global.orphan.service.OrphanQuarantineService;
import com.example.serverprovision.global.registration.FailureDisposition;
import com.example.serverprovision.global.registration.RegistrationFailure;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * ISO 등록 후처리 비동기 실행기.
 * 업로드/경로 검증이 끝난 뒤 SHA-256 계산·중복 검사·DB/marker 저장을 별도 스레드에서 수행한다.
 *
 * <p>R1-4-4 — 실패-종류별 multi-catch 를 <b>예외 다형성 {@link RegistrationFailure#disposition()} dispatch</b> 로
 * 대체(분기문 무분별 확장 회피). 등록 실패 예외가 자기 처분을 선언하고, 본 실행기는 단일 catch 에서 sealed
 * {@link FailureDisposition}(Cleanup / Nudge / Quarantine)을 <b>exhaustive switch</b> 한다 — 새 실패 예외는 정의 시점에
 * 처분을 선언해야 하므로 분류 누락이 구조적으로 불가능하다. 외부 클래스인 {@link DataIntegrityViolationException} 만
 * disposition 을 구현할 수 없어 프레임워크-경계 어댑터 catch 1곳을 유지한다(유일 허용 잔존 분기).</p>
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
	private final OrphanQuarantineService orphanQuarantineService;

	@Async
	public void runAsync(String jobId, IsoRegistrationService.PreparedIsoRegistration prepared) {
		try {
			backgroundJobService.startStage(jobId, IsoRegistrationStage.COMPUTE_HASH);
			Long isoId = isoRegistrationService.finalize(jobId, prepared);
			backgroundJobService.complete(jobId);
			log.info("[IsoRegistrationRunner] ISO 등록 완료. jobId={}, isoId={}, path={}",
					jobId, isoId, prepared.resolvedPath());
		} catch (DataIntegrityViolationException e) {
			// 외부(Spring) 예외 — disposition 구현 불가. 프레임워크-경계 어댑터(유일 허용 잔존 분기). RuntimeException 보다 먼저.
			quarantineAndFail(jobId, prepared, OrphanFailureClass.DB_CONSTRAINT, e);
		} catch (RuntimeException e) {
			// RegistrationFailure 는 인터페이스라 catch 절 타입이 될 수 없으므로(Throwable 비상속) instanceof 로 분기한다.
			// 이 분기는 새 실패 타입이 늘어도 자라지 않는다 — 실패 분류는 disposition() 다형성이 전담하고,
			// 여기는 "자기-분류 실패인가 / 미분류인가" 의 고정 2-갈래일 뿐이다.
			if (e instanceof RegistrationFailure f) {
				dispatch(jobId, prepared, e, f);
			} else {
				// 미분류 → 안전하게 격리 (대용량 업로드를 silently 잃지 않기 위해).
				quarantineAndFail(jobId, prepared, OrphanFailureClass.UNEXPECTED, e);
			}
		}
	}

	/** 실패 예외의 처분에 따라 분기. sealed {@link FailureDisposition} 이라 exhaustive(default 불요). */
	private void dispatch(String jobId, IsoRegistrationService.PreparedIsoRegistration prepared,
	                      RuntimeException ex, RegistrationFailure f) {
		switch (f.disposition()) {
			case FailureDisposition.Cleanup ignored -> {
				// CONTENT/PERMANENT — finalize 가 이미 우리 업로드 파일을 정리. 격리하지 않고 fail.
				log.warn("[IsoRegistrationRunner] 콘텐츠 실패 — 격리 없이 fail. jobId={}, reason={}", jobId, ex.getMessage());
				backgroundJobService.fail(jobId, messageOf(ex));
			}
			case FailureDisposition.Nudge nudge -> {
				// nudge — 임시 파일은 confirm 시 정식 등록될 수 있으므로 정리하지 않는다.
				backgroundJobService.fail(jobId, NUDGE_FAIL_PREFIX + nudge.nudgeId());
				log.info("[IsoRegistrationRunner] nudge 필요 — job 일시 fail 표시. jobId={}, nudgeId={}",
						jobId, nudge.nudgeId());
			}
			case FailureDisposition.Quarantine q -> quarantineAndFail(jobId, prepared, q.failureClass(), ex);
		}
	}

	private void quarantineAndFail(String jobId, IsoRegistrationService.PreparedIsoRegistration prepared,
	                               OrphanFailureClass failureClass, RuntimeException cause) {
		String detail = messageOf(cause);
		try {
			OrphanQuarantineRequest request = new OrphanQuarantineRequest(
					ResourceType.OS_ISO,
					prepared.osMetadataId(),
					prepared.resolvedPath(),
					prepared.uploadedFile(),
					prepared.originalFilename(),
					new IsoRecoveryPayload(prepared.description(), prepared.clientHash()),
					failureClass,
					detail,
					jobId
			);
			String recoveryId = orphanQuarantineService.record(request);
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

	private static String messageOf(RuntimeException e) {
		return e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
	}
}
