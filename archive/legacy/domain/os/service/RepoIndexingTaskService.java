package com.example.serverprovision.domain.os.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;

/**
 * 저장소 인덱싱 비동기 태스크 오케스트레이션.
 *
 * <p>{@link ExtractionTaskService} 와 동일 패턴으로 구성된다. 기존
 * {@code extractionExecutor} 풀을 재사용해 별도 스레드 풀을 추가하지 않는다 (comps 추출과
 * 동시에 돌더라도 큐에서 순차 처리되면 충분하며, 관리자가 동시에 여러 OS 를 인덱싱하는
 * 케이스는 드물다).</p>
 */
@Slf4j
@Service
public class RepoIndexingTaskService {

    private final RepoIndexingService repoIndexingService;
    private final Executor executor;
    private final Map<String, RepoIndexingTask> taskStore = new ConcurrentHashMap<>();

    public RepoIndexingTaskService(
            RepoIndexingService repoIndexingService,
            @Qualifier("extractionExecutor") Executor executor) {
        this.repoIndexingService = repoIndexingService;
        this.executor = executor;
    }

    public String startIndexing(Long osMetadataId) {
        String taskId = UUID.randomUUID().toString();
        RepoIndexingTask task = new RepoIndexingTask(taskId, osMetadataId);
        taskStore.put(taskId, task);
        log.info("[RepoIndexingTaskService] 인덱싱 태스크 생성. taskId={}, osMetadataId={}",
                taskId, osMetadataId);

        executor.execute(() -> run(task));
        return taskId;
    }

    private void run(RepoIndexingTask task) {
        try {
            task.markProcessing();
            RepoIndexingService.IndexingSummary summary =
                    repoIndexingService.indexAndSave(task.getOsMetadataId(), task::update);
            task.complete(summary.packageCount() + "개 패키지, "
                    + summary.serviceCount() + "개 서비스 인덱싱 완료");
            log.info("[RepoIndexingTaskService] 인덱싱 완료. taskId={}", task.getTaskId());
        } catch (UnsupportedOperationException e) {
            task.fail(e.getMessage());
            log.warn("[RepoIndexingTaskService] 지원되지 않는 경로. taskId={}, msg={}",
                    task.getTaskId(), e.getMessage());
        } catch (Exception e) {
            task.fail("인덱싱 실패: " + e.getMessage());
            log.error("[RepoIndexingTaskService] 인덱싱 실패. taskId={}", task.getTaskId(), e);
        }
    }

    public RepoIndexingTask getTask(String taskId) {
        return taskStore.get(taskId);
    }
}
