package com.example.serverprovision.management.subprogram.service;

import com.example.serverprovision.global.job.enums.JobType;
import com.example.serverprovision.global.job.service.BackgroundJobService;
import com.example.serverprovision.global.job.stage.IntegrityVerificationStage;
import com.example.serverprovision.global.marker.ResourceType;
import com.example.serverprovision.management.bios.vo.IntegrityStatus;
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
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SubprogramVerificationLauncher {

    private final SubprogramService subprogramService;
    private final SubprogramRepository subprogramRepository;
    private final BackgroundJobService backgroundJobService;

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
                metadata);
        runAsync(jobId, subprogramId);
        return jobId;
    }

    @Async
    public void runAsync(String jobId, Long subprogramId) {
        try {
            backgroundJobService.startStage(jobId, IntegrityVerificationStage.VERIFY_SIGNATURE);
            IntegrityStatus status = subprogramService.verifyAndRecordIntegrity(subprogramId);
            applyStatus(jobId, status);
        } catch (RuntimeException e) {
            log.error("[verify] Subprogram 검증 실패. id={}", subprogramId, e);
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
