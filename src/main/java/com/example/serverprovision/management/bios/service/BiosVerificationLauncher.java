package com.example.serverprovision.management.bios.service;

import com.example.serverprovision.global.job.enums.JobType;
import com.example.serverprovision.global.job.service.BackgroundJobService;
import com.example.serverprovision.global.job.stage.IntegrityVerificationStage;
import com.example.serverprovision.management.bios.entity.BoardBIOS;
import com.example.serverprovision.management.bios.exception.BiosNotFoundException;
import com.example.serverprovision.management.bios.repository.BiosRepository;
import com.example.serverprovision.management.bios.vo.IntegrityStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * BIOS 트리 무결성 검증 Job 시작자. {@link BiosService#verifyIntegrity(Long, Long)} 동기 호출을
 * BackgroundJob 으로 감싼다. 트리 manifest 재계산은 파일 수에 비례해 시간이 늘어나므로 비동기화 효과가 크다.
 *
 * <p>구조는 {@link com.example.serverprovision.management.os.service.IsoVerificationLauncher} 와 대칭.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BiosVerificationLauncher {

    private final BiosService biosService;
    private final BiosRepository biosRepository;
    private final BackgroundJobService backgroundJobService;

    public String startVerification(Long boardId, Long biosId) {
        BoardBIOS bios = biosRepository.findByIdAndBoardModel_Id(biosId, boardId)
                .orElseThrow(() -> new BiosNotFoundException(boardId, biosId));
        String jobId = backgroundJobService.register(
                JobType.INTEGRITY_VERIFICATION,
                "BIOS 무결성 검증",
                bios.getName() + " · " + bios.getVersion(),
                BackgroundJobService.stagesOf(IntegrityVerificationStage.values()));
        runAsync(jobId, boardId, biosId);
        return jobId;
    }

    @Async
    public void runAsync(String jobId, Long boardId, Long biosId) {
        try {
            backgroundJobService.startStage(jobId, IntegrityVerificationStage.VERIFY_SIGNATURE);
            IntegrityStatus status = biosService.verifyIntegrity(boardId, biosId);
            applyStatus(jobId, status);
        } catch (RuntimeException e) {
            log.error("[verify] BIOS 검증 실패. biosId={}", biosId, e);
            backgroundJobService.fail(jobId, "검증 실패 : " + e.getMessage());
        }
    }

    private void applyStatus(String jobId, IntegrityStatus status) {
        switch (status) {
            case MARKER_MISSING, SIGNATURE_INVALID ->
                    backgroundJobService.fail(jobId, statusMessage(status));
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
