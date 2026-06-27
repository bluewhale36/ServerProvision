package com.example.serverprovision.management.bios.service;

import com.example.serverprovision.global.job.IntegrityJobReporter;
import com.example.serverprovision.global.job.enums.JobType;
import com.example.serverprovision.global.job.service.BackgroundJobService;
import com.example.serverprovision.global.job.stage.IntegrityVerificationStage;
import com.example.serverprovision.global.marker.IntegrityStatus;
import com.example.serverprovision.global.marker.ResourceType;
import com.example.serverprovision.management.bios.entity.BoardBIOS;
import com.example.serverprovision.management.bios.exception.BiosNotFoundException;
import com.example.serverprovision.management.bios.repository.BiosRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * BIOS 트리 무결성 검증 Job 시작자. {@link BiosService#verifyIntegrity(Long, Long)} 동기 호출을
 * BackgroundJob 으로 감싼다. 트리 manifest 재계산은 파일 수에 비례해 시간이 늘어나므로 비동기화 효과가 크다.
 *
 * <p>구조는 {@link com.example.serverprovision.management.os.service.iso.IsoVerificationLauncher} 와 대칭.
 * R5-2 — 결과 enum → Job stage 전이는 공통 {@link IntegrityJobReporter} 로 위임(4 launcher 복제 제거).</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BiosVerificationLauncher {

	private final BiosService biosService;
	private final BiosRepository biosRepository;
	private final BackgroundJobService backgroundJobService;
	private final IntegrityJobReporter integrityJobReporter;

	public String startVerification(Long boardId, Long biosId) {
		BoardBIOS bios = biosRepository.findByIdAndBoardModel_Id(biosId, boardId)
				.orElseThrow(() -> new BiosNotFoundException(boardId, biosId));
		String jobId = backgroundJobService.register(
				JobType.INTEGRITY_VERIFICATION,
				"BIOS 무결성 검증",
				bios.getName() + " · " + bios.getVersion(),
				BackgroundJobService.stagesOf(IntegrityVerificationStage.values()),
				Map.of(
						"resourceType", ResourceType.BIOS_BUNDLE.name(),
						"resourceId", String.valueOf(biosId),
						"parentId", String.valueOf(boardId)
				)
		);
		runAsync(jobId, boardId, biosId);
		return jobId;
	}

	@Async
	public void runAsync(String jobId, Long boardId, Long biosId) {
		try {
			backgroundJobService.startStage(jobId, IntegrityVerificationStage.VERIFY_SIGNATURE);
			IntegrityStatus status = biosService.verifyAndRecordIntegrity(boardId, biosId);
			integrityJobReporter.report(jobId, status);
		} catch (RuntimeException e) {
			log.error("[verify] BIOS 검증 실패. biosId={}", biosId, e);
			backgroundJobService.fail(jobId, "검증 실패 : " + e.getMessage());
		}
	}
}
