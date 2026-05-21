package com.example.serverprovision.management.os.service;

import com.example.serverprovision.global.job.enums.JobType;
import com.example.serverprovision.global.job.service.BackgroundJobService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * ISO 등록 후처리 job 시작자.
 */
@Component
@RequiredArgsConstructor
public class IsoRegistrationLauncher {

	private final BackgroundJobService backgroundJobService;
	private final IsoRegistrationRunner isoRegistrationRunner;

	public String startRegistration(OSImageService.PreparedIsoRegistration prepared) {
		String jobId = backgroundJobService.register(
				JobType.ISO_REGISTRATION,
				"ISO 등록",
				prepared.resolvedPath(),
				BackgroundJobService.stagesOf(IsoRegistrationStage.values()),
				Map.of("osId", String.valueOf(prepared.osImageId()))
		);
		isoRegistrationRunner.runAsync(jobId, prepared);
		return jobId;
	}
}
