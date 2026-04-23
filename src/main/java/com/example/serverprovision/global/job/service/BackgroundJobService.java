package com.example.serverprovision.global.job.service;

import com.example.serverprovision.global.job.BackgroundJob;
import com.example.serverprovision.global.job.JobStage;
import com.example.serverprovision.global.job.enums.JobType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * 백그라운드 Job 의 등록 / 조회 / 상태 보고 / 정리 중앙 조정자.
 * <p>영속성은 in-memory ({@link ConcurrentHashMap}). JVM 재시작 시 Job 목록은 리셋된다.</p>
 * <p>Pruner 는 {@code @Scheduled} 로 주기 실행되며 두 조건으로 종료 Job 을 자동 정리한다:
 * (1) 종료 후 {@code job.retention.keep-after-terminal-ms} 이상 경과 (2) 총 보관 개수가 {@code job.retention.max-count}
 * 초과 시 종료 Job 중 오래된 순으로 삭제. 활성(PENDING/RUNNING) Job 은 pruner 가 건드리지 않는다.</p>
 */
@Slf4j
@Service
public class BackgroundJobService {

    private final ConcurrentMap<String, BackgroundJob> jobs = new ConcurrentHashMap<>();

    @Value("${job.retention.max-count:100}")
    private int maxCount;

    @Value("${job.retention.keep-after-terminal-ms:600000}")
    private long keepAfterTerminalMs;

    /** 새 Job 을 등록하고 jobId 를 반환. 상태는 PENDING. */
    public String register(JobType type, String title, String subtitle) {
        return register(type, title, subtitle, Map.of());
    }

    /** 도메인별 보조 식별자(metadata)가 필요한 경우 사용한다. 예: ISO 업로드 Job 의 osId. */
    public String register(JobType type, String title, String subtitle, Map<String, String> metadata) {
        String jobId = UUID.randomUUID().toString();
        jobs.put(jobId, new BackgroundJob(jobId, type, title, subtitle, metadata));
        log.info("[BackgroundJobService] Job 등록. jobId={}, type={}, title={}, meta={}",
                jobId, type, title, metadata);
        return jobId;
    }

    public Optional<BackgroundJob> find(String jobId) {
        return Optional.ofNullable(jobs.get(jobId));
    }

    /**
     * 활성 Job + 최근 종료 Job 을 시간 역순(최신 먼저) 스냅샷으로 반환.
     * UI 알림 패널이 이 목록을 그대로 렌더한다.
     */
    public List<BackgroundJob> snapshot() {
        return jobs.values().stream()
                .sorted(Comparator.comparing(BackgroundJob::getCreatedAt).reversed())
                .toList();
    }

    /** stage 의 label + 기본 percent + label 을 메시지로 사용. */
    public void report(String jobId, JobStage stage) {
        report(jobId, stage, stage.label());
    }

    /** stage 의 label + 기본 percent + 사용자 메시지. */
    public void report(String jobId, JobStage stage, String message) {
        BackgroundJob j = jobs.get(jobId);
        if (j == null) return;
        j.report(stage.label(), stage.percent(), message);
    }

    /** stage 의 label + 동적으로 계산된 percent + 사용자 메시지. 업로드처럼 진행률이 외부 입력에서 오는 경우. */
    public void report(String jobId, JobStage stage, int percent, String message) {
        BackgroundJob j = jobs.get(jobId);
        if (j == null) return;
        j.report(stage.label(), percent, message);
    }

    public void complete(String jobId) {
        BackgroundJob j = jobs.get(jobId);
        if (j == null) return;
        j.complete();
    }

    public void fail(String jobId, String message) {
        BackgroundJob j = jobs.get(jobId);
        if (j == null) return;
        j.fail(message);
    }

    /**
     * 완료/실패 Job 을 목록에서 제거. 활성 Job(PENDING/RUNNING) 은 무시.
     * 사용자가 알림 카드의 X 버튼을 눌렀을 때 호출된다.
     */
    public void dismiss(String jobId) {
        BackgroundJob j = jobs.get(jobId);
        if (j == null) return;
        if (!j.getStatus().isTerminal()) {
            log.debug("[BackgroundJobService] dismiss 무시 (활성 Job). jobId={}, status={}", jobId, j.getStatus());
            return;
        }
        jobs.remove(jobId);
    }

    /**
     * 주기적으로 호출되는 pruner 엔트리. 현재 시각을 기준으로 내부 {@link #prune(Instant)} 에 위임한다.
     * {@code fixedDelay} 인터벌은 {@code job.retention.prune-interval-ms} 로 조정 가능.
     */
    @Scheduled(fixedDelayString = "${job.retention.prune-interval-ms:60000}")
    public void prune() {
        prune(Instant.now());
    }

    /**
     * 실제 정리 로직. 테스트에서는 가짜 {@code now} 를 주입해 pruner 결정 경계를 검증한다.
     */
    void prune(Instant now) {
        List<BackgroundJob> terminal = new ArrayList<>();
        for (BackgroundJob j : jobs.values()) {
            if (j.getStatus().isTerminal()) terminal.add(j);
        }

        // (1) 시간 경과 기반 삭제
        Duration retain = Duration.ofMillis(keepAfterTerminalMs);
        List<BackgroundJob> expired = terminal.stream()
                .filter(j -> j.getCompletedAt() != null
                        && Duration.between(j.getCompletedAt(), now).compareTo(retain) > 0)
                .toList();
        expired.forEach(j -> jobs.remove(j.getId()));
        terminal.removeAll(expired);

        // (2) 총 보관 개수 상한 — 초과분은 종료 Job 중 오래된 순으로 삭제
        int total = jobs.size();
        int over = total - maxCount;
        if (over > 0 && !terminal.isEmpty()) {
            terminal.sort(Comparator.comparing(
                    BackgroundJob::getCompletedAt,
                    Comparator.nullsFirst(Comparator.naturalOrder())
            ));
            for (int i = 0; i < over && i < terminal.size(); i++) {
                jobs.remove(terminal.get(i).getId());
            }
        }
    }
}
