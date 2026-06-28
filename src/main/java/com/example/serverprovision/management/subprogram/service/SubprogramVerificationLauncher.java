package com.example.serverprovision.management.subprogram.service;

import com.example.serverprovision.global.job.IntegrityJobReporter;
import com.example.serverprovision.global.job.enums.JobType;
import com.example.serverprovision.global.job.service.BackgroundJobService;
import com.example.serverprovision.global.job.stage.IntegrityVerificationStage;
import com.example.serverprovision.global.marker.IntegrityStatus;
import com.example.serverprovision.global.marker.ResourceType;
import com.example.serverprovision.management.subprogram.entity.Subprogram;
import com.example.serverprovision.management.subprogram.exception.SubprogramNotFoundException;
import com.example.serverprovision.management.subprogram.repository.SubprogramRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Subprogram 무결성 검증 BackgroundJob 시작자. BMC / BIOS Launcher 와 동일 패턴.
 *
 * <p>R5-2 — 결과 enum → Job stage 전이는 공통 {@link IntegrityJobReporter} 로 위임(4 launcher 복제 제거).</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SubprogramVerificationLauncher {

	private final SubprogramIntegrityService subprogramIntegrityService;
	private final SubprogramRepository subprogramRepository;
	private final BackgroundJobService backgroundJobService;
	private final IntegrityJobReporter integrityJobReporter;

	public String startVerification(Long subprogramId) {
		Subprogram sp = subprogramRepository.findById(subprogramId)
				.orElseThrow(() -> new SubprogramNotFoundException(subprogramId));

		Map<String, String> metadata = new LinkedHashMap<>();
		metadata.put("resourceType", ResourceType.SUBPROGRAM.name());
		metadata.put("resourceId", String.valueOf(subprogramId));
		if (sp.getBoardId() != null) {
			metadata.put("parentId", String.valueOf(sp.getBoardId()));
		}
		metadata.put("kind", sp.getKind().pathToken());

		String jobId = backgroundJobService.register(
				JobType.INTEGRITY_VERIFICATION,
				sp.getKind().getDisplayName() + " 무결성 검증",
				sp.getName() + " · " + sp.getVersion(),
				BackgroundJobService.stagesOf(IntegrityVerificationStage.values()),
				metadata
		);
		runAsync(jobId, subprogramId);
		return jobId;
	}

	@Async
	public void runAsync(String jobId, Long subprogramId) {
		try {
			backgroundJobService.startStage(jobId, IntegrityVerificationStage.VERIFY_SIGNATURE);
			IntegrityStatus status = subprogramIntegrityService.verifyAndRecordIntegrity(subprogramId);
			integrityJobReporter.report(jobId, status);
		} catch (RuntimeException e) {
			log.error("[verify] Subprogram 검증 실패. id={}", subprogramId, e);
			backgroundJobService.fail(jobId, "검증 실패 : " + e.getMessage());
		}
	}
}
