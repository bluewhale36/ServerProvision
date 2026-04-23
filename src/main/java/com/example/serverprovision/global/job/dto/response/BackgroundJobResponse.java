package com.example.serverprovision.global.job.dto.response;

import com.example.serverprovision.global.job.BackgroundJob;
import com.example.serverprovision.global.job.enums.JobStatus;
import com.example.serverprovision.global.job.enums.JobType;

import java.time.Instant;
import java.util.Map;

/**
 * 알림 패널 카드 렌더용 Job 스냅샷 응답.
 * 프론트엔드 JS 가 JSON 구조를 얕게 유지할 수 있도록 {@code JobProgress} 와 상태·시각 필드를 평면화한다.
 */
public record BackgroundJobResponse(
        String id,
        JobType type,
        String typeLabel,
        String title,
        String subtitle,
        JobStatus status,
        String stageLabel,
        int percent,
        String message,
        Instant createdAt,
        Instant completedAt,
        Map<String, String> metadata
) {

    public static BackgroundJobResponse from(BackgroundJob job) {
        var p = job.getProgress();
        String msg = job.getErrorMessage() != null ? job.getErrorMessage() : p.message();
        return new BackgroundJobResponse(
                job.getId(),
                job.getType(),
                job.getType().getDisplayName(),
                job.getTitle(),
                job.getSubtitle(),
                job.getStatus(),
                p.stageLabel(),
                p.percent(),
                msg,
                job.getCreatedAt(),
                job.getCompletedAt(),
                job.getMetadata()
        );
    }
}
