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
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * 백그라운드 Job 의 등록 / 조회 / 단계 전환 / 정리 중앙 조정자.
 *
 * <p>chunk progress bar 모델 (사용자 결정) :
 * <ul>
 *   <li>등록 시점에 단계 라벨 리스트(stages) 를 함께 받는다 — 도메인 enum 의 {@code values()} 가 자연스러운 출처</li>
 *   <li>caller 는 단계 진입(startStage) / 단계 완료(completeStage) / 실패(fail) / 전체 완료(complete) 4 개 이벤트만 보고</li>
 *   <li>진행률 % 계산 / 보고는 폐기 — 프론트가 chunk 색상으로 시각화</li>
 * </ul>
 *
 * <p>영속성은 in-memory ({@link ConcurrentHashMap}). JVM 재시작 시 Job 목록은 리셋된다.</p>
 */
@Slf4j
@Service
public class BackgroundJobService {

    private final ConcurrentMap<String, BackgroundJob> jobs = new ConcurrentHashMap<>();

    @Value("${job.retention.max-count:100}")
    private int maxCount;

    @Value("${job.retention.keep-after-terminal-ms:600000}")
    private long keepAfterTerminalMs;

    /** 단순 등록 — stages 라벨 리스트만 직접 제공. caller 는 보통 {@code stagesOf(MyStage.values())} 헬퍼 사용. */
    public String register(JobType type, String title, String subtitle, List<String> stageLabels) {
        return register(type, title, subtitle, stageLabels, Map.of());
    }

    /** 도메인 {@link JobStage} enum 의 values() 를 라벨 리스트로 변환. caller 가벼운 호출용 헬퍼. */
    public static List<String> stagesOf(JobStage[] stages) {
        return Arrays.stream(stages).map(JobStage::label).toList();
    }

    /** 도메인별 보조 식별자(metadata) 가 필요한 경우. UI 가 타겟 DOM 을 찾는 데 쓴다. */
    public String register(JobType type, String title, String subtitle,
                           List<String> stageLabels, Map<String, String> metadata) {
        String jobId = UUID.randomUUID().toString();
        jobs.put(jobId, new BackgroundJob(jobId, type, title, subtitle, stageLabels, metadata));
        log.info("[BackgroundJobService] Job 등록. jobId={}, type={}, title={}, stages={}, meta={}",
                jobId, type, title, stageLabels, metadata);
        return jobId;
    }

    public Optional<BackgroundJob> find(String jobId) {
        return Optional.ofNullable(jobs.get(jobId));
    }

    /** 활성 + 최근 종료 Job 의 시간 역순 스냅샷. UI 알림 패널 렌더용. */
    public List<BackgroundJob> snapshot() {
        return jobs.values().stream()
                .sorted(Comparator.comparing(BackgroundJob::getCreatedAt).reversed())
                .toList();
    }

    // ==== 단계 전환 이벤트 =================================================

    /** 특정 stage 인덱스 진입 — RUNNING 상태로 전환. */
    public void startStage(String jobId, int stageIndex) {
        BackgroundJob j = jobs.get(jobId);
        if (j == null) return;
        j.startStage(stageIndex);
    }

    /** 도메인 enum 의 ordinal 을 stage index 로 사용. 가장 단순한 호출 형태. */
    public void startStage(String jobId, Enum<?> stage) {
        startStage(jobId, stage.ordinal());
    }

    /** 현재 RUNNING 단계를 DONE 으로 마감. 다음 startStage 호출 전 명시적 종료가 필요할 때. */
    public void completeStage(String jobId) {
        BackgroundJob j = jobs.get(jobId);
        if (j == null) return;
        j.completeCurrentStage();
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

    /** 완료/실패 Job 을 목록에서 제거. 활성 Job 은 무시. */
    public void dismiss(String jobId) {
        BackgroundJob j = jobs.get(jobId);
        if (j == null) return;
        if (!j.getStatus().isTerminal()) {
            log.debug("[BackgroundJobService] dismiss 무시 (활성 Job). jobId={}, status={}", jobId, j.getStatus());
            return;
        }
        jobs.remove(jobId);
    }

    @Scheduled(fixedDelayString = "${job.retention.prune-interval-ms:60000}")
    public void prune() {
        prune(Instant.now());
    }

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

        // (2) 총 보관 개수 상한
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
