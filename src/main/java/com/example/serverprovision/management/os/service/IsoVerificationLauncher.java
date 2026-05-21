package com.example.serverprovision.management.os.service;

import com.example.serverprovision.global.job.enums.JobType;
import com.example.serverprovision.global.job.service.BackgroundJobService;
import com.example.serverprovision.global.job.stage.IntegrityVerificationStage;
import com.example.serverprovision.global.marker.ResourceType;
import com.example.serverprovision.management.bios.vo.IntegrityStatus;
import com.example.serverprovision.management.os.entity.ISO;
import com.example.serverprovision.management.os.exception.ISONotFoundException;
import com.example.serverprovision.management.os.repository.ISORepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * ISO 무결성 검증 Job 시작자. {@link OSImageService#verifyIntegrity(Long, Long)} 의 동기 호출을
 * BackgroundJob 으로 감싸서 호출 스레드(HTTP 요청 스레드)는 즉시 jobId 만 반환받고,
 * 실제 검증(특히 SHA-256 재계산 — 대용량 ISO 는 수십 초~수 분) 은 별도 스레드에서 수행한다.
 *
 * <p>알림 센터(서류가방) 에 작업 카드가 노출되며, 단계 색상으로 결과를 식별한다.
 * 향후 알림 기능이 도입되면 본 launcher 에 알림 발행 단계를 덧붙이거나 호출 측이 알림으로 대체한다.</p>
 *
 * <p>별도 컴포넌트로 분리한 이유 : {@code @Async} 는 Spring proxy 경유 호출에서만 동작하는데,
 * 같은 클래스 내부 메서드 호출은 프록시를 우회한다. {@link OSImageService} 안에 두면 self-proxy
 * 패턴이 필요한데 {@code @RequiredArgsConstructor} 와 충돌한다. launcher 를 별도 빈으로 분리하면
 * Service 본체 변경 없이 비동기 진입점만 추가로 갖는다.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class IsoVerificationLauncher {

	private final OSImageService osImageService;
	private final ISORepository isoRepository;
	private final BackgroundJobService backgroundJobService;

	/**
	 * 검증 Job 등록 후 비동기 실행. 호출 스레드는 jobId 만 받고 즉시 반환.
	 */
	public String startVerification(Long osImageId, Long isoId) {
		ISO iso = isoRepository.findByIdAndOsImage_Id(isoId, osImageId)
				.orElseThrow(() -> new ISONotFoundException(osImageId, isoId));
		String jobId = backgroundJobService.register(
				JobType.INTEGRITY_VERIFICATION,
				"ISO 무결성 검증",
				iso.getIsoPath(),
				BackgroundJobService.stagesOf(IntegrityVerificationStage.values()),
				Map.of(
						"resourceType", ResourceType.OS_ISO.name(),
						"resourceId", String.valueOf(isoId),
						"parentId", String.valueOf(osImageId)
				)
		);
		runAsync(jobId, osImageId, isoId);
		return jobId;
	}

	/**
	 * 실제 검증 수행. {@code verifyIntegrity} 는 단일 호출이지만 결과 enum 으로 단계별 전이를 역추적한다.
	 * <ul>
	 *   <li>{@code MARKER_MISSING / SIGNATURE_INVALID} : 서명 단계에서 막힘 → 1단계 ERROR</li>
	 *   <li>{@code TAMPERED} : 서명 통과 후 해시 불일치 → 1단계 DONE, 2단계 ERROR</li>
	 *   <li>{@code ORIGINAL} : 둘 다 DONE → Job COMPLETED</li>
	 * </ul>
	 */
	@Async
	public void runAsync(String jobId, Long osImageId, Long isoId) {
		try {
			backgroundJobService.startStage(jobId, IntegrityVerificationStage.VERIFY_SIGNATURE);
			IntegrityStatus status = osImageService.verifyAndRecordIntegrity(osImageId, isoId);
			applyStatus(jobId, status);
		} catch (RuntimeException e) {
			log.error("[verify] ISO 검증 실패. isoId={}", isoId, e);
			backgroundJobService.fail(jobId, "검증 실패 : " + e.getMessage());
		}
	}

	private void applyStatus(String jobId, IntegrityStatus status) {
		switch (status) {
			case MARKER_MISSING, SIGNATURE_INVALID -> backgroundJobService.fail(jobId, statusMessage(status));
			case TAMPERED -> {
				backgroundJobService.completeStage(jobId);
				backgroundJobService.startStage(jobId, IntegrityVerificationStage.RECOMPUTE_HASH);
				backgroundJobService.fail(jobId, statusMessage(status));
			}
			case ORIGINAL -> {
				backgroundJobService.completeStage(jobId);
				backgroundJobService.startStage(jobId, IntegrityVerificationStage.RECOMPUTE_HASH);
				backgroundJobService.completeStage(jobId);
				backgroundJobService.complete(jobId);
			}
			case NOT_VERIFIED -> backgroundJobService.fail(jobId, "검증 결과를 받지 못했습니다.");
		}
	}

	private String statusMessage(IntegrityStatus status) {
		return switch (status) {
			case ORIGINAL -> "원본 유지";
			case TAMPERED -> "변조 감지 (해시 불일치)";
			case SIGNATURE_INVALID -> "서명 무효";
			case MARKER_MISSING -> "마커 파일 없음";
			case NOT_VERIFIED -> "미검증";
		};
	}
}
