package com.example.serverprovision.management.os.service.iso;

import com.example.serverprovision.global.job.IntegrityJobReporter;
import com.example.serverprovision.global.job.enums.JobType;
import com.example.serverprovision.global.job.service.BackgroundJobService;
import com.example.serverprovision.global.job.stage.IntegrityVerificationStage;
import com.example.serverprovision.global.marker.IntegrityStatus;
import com.example.serverprovision.global.marker.ResourceType;
import com.example.serverprovision.management.os.entity.ISO;
import com.example.serverprovision.management.os.exception.ISONotFoundException;
import com.example.serverprovision.management.os.repository.ISORepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * ISO 무결성 검증 Job 시작자. {@link IsoIntegrityService#verify(Long, Long)} 의 동기 호출을
 * BackgroundJob 으로 감싸서 호출 스레드(HTTP 요청 스레드)는 즉시 jobId 만 반환받고,
 * 실제 검증(특히 SHA-256 재계산 — 대용량 ISO 는 수십 초~수 분) 은 별도 스레드에서 수행한다.
 *
 * <p>알림 센터(서류가방) 에 작업 카드가 노출되며, 단계 색상으로 결과를 식별한다.
 * 향후 알림 기능이 도입되면 본 launcher 에 알림 발행 단계를 덧붙이거나 호출 측이 알림으로 대체한다.</p>
 *
 * <p>별도 컴포넌트로 분리한 이유 : {@code @Async} 는 Spring proxy 경유 호출에서만 동작하는데,
 * 같은 클래스 내부 메서드 호출은 프록시를 우회한다. {@link IsoIntegrityService} 안에 두면 self-proxy
 * 패턴이 필요한데 {@code @RequiredArgsConstructor} 와 충돌한다. launcher 를 별도 빈으로 분리하면
 * Service 본체 변경 없이 비동기 진입점만 추가로 갖는다.</p>
 *
 * <p>R5-2 — 결과 enum → Job stage 전이는 공통 {@link IntegrityJobReporter} 로 위임(4 launcher 복제 제거).</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class IsoVerificationLauncher {

	private final IsoIntegrityService isoIntegrityService;
	private final ISORepository isoRepository;
	private final BackgroundJobService backgroundJobService;
	private final IntegrityJobReporter integrityJobReporter;

	/**
	 * 검증 Job 등록 후 비동기 실행. 호출 스레드는 jobId 만 받고 즉시 반환.
	 */
	public String startVerification(Long osMetadataId, Long isoId) {
		ISO iso = isoRepository.findByIdAndOsMetadata_Id(isoId, osMetadataId)
				.orElseThrow(() -> new ISONotFoundException(osMetadataId, isoId));
		String jobId = backgroundJobService.register(
				JobType.INTEGRITY_VERIFICATION,
				"ISO 무결성 검증",
				iso.getIsoPath(),
				BackgroundJobService.stagesOf(IntegrityVerificationStage.values()),
				Map.of(
						"resourceType", ResourceType.OS_ISO.name(),
						"resourceId", String.valueOf(isoId),
						"parentId", String.valueOf(osMetadataId)
				)
		);
		runAsync(jobId, osMetadataId, isoId);
		return jobId;
	}

	/**
	 * 실제 검증 수행. {@code verifyAndRecord} 는 단일 호출이지만 결과 enum 으로 단계별 전이를 역추적한다.
	 * <ul>
	 *   <li>{@code MARKER_MISSING / SIGNATURE_INVALID} : 서명 단계에서 막힘 → 1단계 ERROR</li>
	 *   <li>{@code TAMPERED} : 서명 통과 후 해시 불일치 → 1단계 DONE, 2단계 ERROR</li>
	 *   <li>{@code ORIGINAL} : 둘 다 DONE → Job COMPLETED</li>
	 * </ul>
	 */
	@Async
	public void runAsync(String jobId, Long osMetadataId, Long isoId) {
		try {
			backgroundJobService.startStage(jobId, IntegrityVerificationStage.VERIFY_SIGNATURE);
			IntegrityStatus status = isoIntegrityService.verifyAndRecord(osMetadataId, isoId);
			integrityJobReporter.report(jobId, status);
		} catch (RuntimeException e) {
			log.error("[verify] ISO 검증 실패. isoId={}", isoId, e);
			backgroundJobService.fail(jobId, "검증 실패 : " + e.getMessage());
		}
	}
}
