package com.example.serverprovision.management.bmc.service;

import com.example.serverprovision.global.job.enums.JobType;
import com.example.serverprovision.global.job.service.BackgroundJobService;
import com.example.serverprovision.global.job.stage.IntegrityVerificationStage;
import com.example.serverprovision.global.marker.ResourceType;
import com.example.serverprovision.management.bios.vo.IntegrityStatus;
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
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BmcVerificationLauncher {

	private final BmcService bmcService;
	private final BmcRepository bmcRepository;
	private final BackgroundJobService backgroundJobService;

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
			applyStatus(jobId, status);
		} catch (RuntimeException e) {
			log.error("[verify] BMC 검증 실패. bmcId={}", bmcId, e);
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
