package com.example.serverprovision.global.job;

import com.example.serverprovision.global.job.enums.JobStatus;
import com.example.serverprovision.global.job.enums.JobType;
import com.example.serverprovision.global.job.vo.JobProgress;
import lombok.Getter;

import java.time.Instant;
import java.util.Collections;
import java.util.Map;

/**
 * 백그라운드 Job 메모리 상태 컨테이너.
 * 비동기 작업 스레드와 폴링/알림 UI 스레드 사이에서 안전하게 교환되기 위해 가변 필드는 모두 volatile.
 * 영속성은 in-memory — JVM 재시작 시 소실된다.
 */
@Getter
public class BackgroundJob {

    private final String id;
    private final JobType type;
    private final String title;
    private final String subtitle;
    /** 도메인별 보조 식별자를 담는 자유 맵. 예: ISO 업로드 Job 의 osId. UI 는 이 맵을 통해 타겟 DOM 을 찾는다. */
    private final Map<String, String> metadata;
    private final Instant createdAt;

    private volatile JobProgress progress;
    private volatile JobStatus status;
    private volatile Instant completedAt;
    private volatile String errorMessage;

    public BackgroundJob(String id, JobType type, String title, String subtitle) {
        this(id, type, title, subtitle, Map.of());
    }

    public BackgroundJob(String id, JobType type, String title, String subtitle, Map<String, String> metadata) {
        this.id = id;
        this.type = type;
        this.title = title;
        this.subtitle = subtitle;
        this.metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
        this.createdAt = Instant.now();
        this.progress = JobProgress.INITIAL;
        this.status = JobStatus.PENDING;
    }

    public Map<String, String> getMetadata() {
        return Collections.unmodifiableMap(metadata);
    }

    /** 진행 단계 갱신. PENDING 이었다면 RUNNING 으로 전이. */
    public void report(String stageLabel, int percent, String message) {
        this.progress = new JobProgress(stageLabel, percent, message);
        if (this.status == JobStatus.PENDING) {
            this.status = JobStatus.RUNNING;
        }
    }

    public void complete() {
        this.progress = new JobProgress("완료", 100, "완료");
        this.status = JobStatus.COMPLETED;
        this.completedAt = Instant.now();
    }

    /** 실패 메시지를 기록하고 FAILED 로 전이. percent 는 마지막 단계의 것을 유지해 어디서 죽었는지 단서를 남긴다. */
    public void fail(String message) {
        this.errorMessage = message;
        this.progress = new JobProgress("실패", this.progress.percent(), message);
        this.status = JobStatus.FAILED;
        this.completedAt = Instant.now();
    }
}
