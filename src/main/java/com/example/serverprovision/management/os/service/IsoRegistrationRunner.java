package com.example.serverprovision.management.os.service;

import com.example.serverprovision.global.job.service.BackgroundJobService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * ISO 등록 후처리 비동기 실행기.
 * 업로드/경로 검증이 끝난 뒤 SHA-256 계산·중복 검사·DB/marker 저장을 별도 스레드에서 수행한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class IsoRegistrationRunner {

    private final OSImageService osImageService;
    private final BackgroundJobService backgroundJobService;

    @Async
    public void runAsync(String jobId, OSImageService.PreparedIsoRegistration prepared) {
        try {
            backgroundJobService.startStage(jobId, IsoRegistrationStage.COMPUTE_HASH);
            Long isoId = osImageService.finalizePreparedIsoRegistration(jobId, prepared);
            backgroundJobService.complete(jobId);
            log.info("[IsoRegistrationRunner] ISO 등록 완료. jobId={}, isoId={}, path={}",
                    jobId, isoId, prepared.resolvedPath());
        } catch (RuntimeException e) {
            log.error("[IsoRegistrationRunner] ISO 등록 실패. jobId={}, path={}",
                    jobId, prepared.resolvedPath(), e);
            backgroundJobService.fail(jobId, e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName());
        }
    }
}
