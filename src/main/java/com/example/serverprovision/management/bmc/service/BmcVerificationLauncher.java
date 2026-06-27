package com.example.serverprovision.management.bmc.service;

import com.example.serverprovision.global.job.IntegrityJobReporter;
import com.example.serverprovision.global.job.enums.JobType;
import com.example.serverprovision.global.job.service.BackgroundJobService;
import com.example.serverprovision.global.job.stage.IntegrityVerificationStage;
import com.example.serverprovision.global.marker.IntegrityStatus;
import com.example.serverprovision.global.marker.ResourceType;
import com.example.serverprovision.management.bmc.entity.BoardBMC;
import com.example.serverprovision.management.bmc.exception.BmcNotFoundException;
import com.example.serverprovision.management.bmc.repository.BmcRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * BMC 파일 무결성 검증 Job 시작자.
 *
 * <p>R5-2 — 결과 enum → Job stage 전이는 공통 {@link IntegrityJobReporter} 로 위임(4 launcher 복제 제거).</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BmcVerificationLauncher {

	private final BmcService bmcService;
	private final BmcRepository bmcRepository;
	private final BackgroundJobService backgroundJobService;
	private final IntegrityJobReporter integrityJobReporter;

	public String startVerification(Long boardId, Long bmcId) {
		BoardBMC bmc = bmcRepository.findByIdAndBoardModel_Id(bmcId, boardId)
				.orElseThrow(() -> new BmcNotFoundException(boardId, bmcId));
		String jobId = backgroundJobService.register(
				JobType.INTEGRITY_VERIFICATION,
				"BMC 무결성 검증",
				bmc.getName() + " · " + bmc.getVersion(),
				BackgroundJobService.stagesOf(IntegrityVerificationStage.values()),
				Map.of(
						"resourceType", ResourceType.BMC_FIRMWARE.name(),
						"resourceId", String.valueOf(bmcId),
						"parentId", String.valueOf(boardId)
				)
		);
		runAsync(jobId, boardId, bmcId);
		return jobId;
	}

	@Async
	public void runAsync(String jobId, Long boardId, Long bmcId) {
		try {
			backgroundJobService.startStage(jobId, IntegrityVerificationStage.VERIFY_SIGNATURE);
			IntegrityStatus status = bmcService.verifyAndRecordIntegrity(boardId, bmcId);
			integrityJobReporter.report(jobId, status);
		} catch (RuntimeException e) {
			log.error("[verify] BMC 검증 실패. bmcId={}", bmcId, e);
			backgroundJobService.fail(jobId, "검증 실패 : " + e.getMessage());
		}
	}
}
