package com.example.serverprovision.domain.os.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;

// comps 추출 비동기 태스크 오케스트레이션 — 인메모리 저장소 + 백그라운드 실행.
// 클라이언트는 startExtraction() 으로 태스크를 시작하고 taskId 로 진행 상황을 폴링한다.
@Slf4j
@Service
public class ExtractionTaskService {

    private final CompsExtractionService compsExtractionService;
    private final Executor extractionExecutor;
    private final Map<String, ExtractionTask> taskStore = new ConcurrentHashMap<>();

    public ExtractionTaskService(
            CompsExtractionService compsExtractionService,
            @Qualifier("extractionExecutor") Executor extractionExecutor) {
        this.compsExtractionService = compsExtractionService;
        this.extractionExecutor = extractionExecutor;
    }

    // 추출 태스크 생성 후 즉시 taskId 를 반환하고, 백그라운드 스레드에서 실제 추출 수행
    public String startExtraction(Long osMetadataId) {
        String taskId = UUID.randomUUID().toString();
        ExtractionTask task = new ExtractionTask(taskId, osMetadataId);
        taskStore.put(taskId, task);
        log.info("[ExtractionTaskService] 추출 태스크 생성. taskId={}, osMetadataId={}", taskId, osMetadataId);

        extractionExecutor.execute(() -> runExtraction(task));
        return taskId;
    }

    // 백그라운드 스레드에서 실행되는 추출 작업 본문
    // CompsExtractionService.extractAndSave 는 @Transactional 이므로 프록시 호출로 트랜잭션이 생성된다.
    private void runExtraction(ExtractionTask task) {
        try {
            task.markProcessing();
            CompsExtractionService.CompsExtractionSummary summary =
                    compsExtractionService.extractAndSave(task.getOsMetadataId(), task::update);
            task.complete(summary.environmentCount() + "개 환경, "
                    + summary.packageGroupCount() + "개 패키지 그룹 추출 완료");
            log.info("[ExtractionTaskService] 추출 완료. taskId={}", task.getTaskId());
        } catch (UnsupportedOperationException e) {
            task.fail(e.getMessage());
            log.warn("[ExtractionTaskService] 지원되지 않는 OS. taskId={}, msg={}",
                    task.getTaskId(), e.getMessage());
        } catch (Exception e) {
            task.fail("추출 실패: " + e.getMessage());
            log.error("[ExtractionTaskService] 추출 실패. taskId={}",
                    task.getTaskId(), e);
        }
    }

    public ExtractionTask getTask(String taskId) {
        return taskStore.get(taskId);
    }
}
