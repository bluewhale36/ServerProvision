package com.example.serverprovision.domain.os.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.concurrent.Executor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * {@link RepoIndexingTaskService} 단위 테스트.
 *
 * <p>UUID 태스크 저장/조회, executor 실행, 예외별 상태 전이를 검증한다.
 * Executor 는 동기로 실행되도록 람다로 직접 구현한 Inline Executor 를 주입해
 * 테스트 스레드에서 바로 run() 이 호출되게 한다.</p>
 */
class RepoIndexingTaskServiceTest {

    private RepoIndexingService repoIndexingService;
    private Executor inlineExecutor;
    private RepoIndexingTaskService service;

    @BeforeEach
    void setUp() {
        repoIndexingService = mock(RepoIndexingService.class);
        // 테스트에서는 동기 실행 — 실제로 extractionExecutor 는 별도 풀이지만 단위 테스트 목적상 직접 실행.
        inlineExecutor = Runnable::run;
        service = new RepoIndexingTaskService(repoIndexingService, inlineExecutor);
    }

    @Test
    @DisplayName("startIndexing 은 UUID 태스크 ID 를 반환하고 getTask 로 조회 가능하다")
    void startIndexing_returnsRetrievableTaskId() {
        when(repoIndexingService.indexAndSave(any(), any()))
                .thenReturn(new RepoIndexingService.IndexingSummary(2, 3));

        String taskId = service.startIndexing(1L);

        assertThat(taskId).isNotBlank();
        assertThat(service.getTask(taskId)).isNotNull();
        assertThat(service.getTask(taskId).getOsMetadataId()).isEqualTo(1L);
    }

    @Test
    @DisplayName("정상 완료 시 태스크 상태가 COMPLETED 로 전이하고 메시지에 요약이 담긴다")
    void onSuccess_taskBecomesCompletedWithSummary() {
        when(repoIndexingService.indexAndSave(eq(1L), any()))
                .thenReturn(new RepoIndexingService.IndexingSummary(5, 7));

        String taskId = service.startIndexing(1L);
        RepoIndexingTask task = service.getTask(taskId);

        assertThat(task.getStatus()).isEqualTo(RepoIndexingTask.Status.COMPLETED);
        assertThat(task.getMessage()).contains("5").contains("7");
    }

    @Test
    @DisplayName("UnsupportedOperationException 발생 시 태스크는 FAILED 상태로 전환되고 메시지가 저장된다")
    void onUnsupportedOperation_taskBecomesFailed() {
        doThrow(new UnsupportedOperationException("HTTP URL 불가"))
                .when(repoIndexingService).indexAndSave(eq(1L), any());

        String taskId = service.startIndexing(1L);
        RepoIndexingTask task = service.getTask(taskId);

        assertThat(task.getStatus()).isEqualTo(RepoIndexingTask.Status.FAILED);
        assertThat(task.getMessage()).isEqualTo("HTTP URL 불가");
    }

    @Test
    @DisplayName("일반 Exception 발생 시 태스크는 FAILED + '인덱싱 실패:' 프리픽스 메시지")
    void onGeneralException_taskBecomesFailedWithPrefix() {
        doThrow(new IllegalStateException("repodata 없음"))
                .when(repoIndexingService).indexAndSave(eq(1L), any());

        String taskId = service.startIndexing(1L);
        RepoIndexingTask task = service.getTask(taskId);

        assertThat(task.getStatus()).isEqualTo(RepoIndexingTask.Status.FAILED);
        assertThat(task.getMessage()).startsWith("인덱싱 실패:").contains("repodata 없음");
    }

    @Test
    @DisplayName("존재하지 않는 taskId 는 null 을 반환한다")
    void getTask_returnsNullForUnknownId() {
        assertThat(service.getTask("does-not-exist")).isNull();
    }

    @Test
    @DisplayName("startIndexing 은 executor 에 작업을 제출한다")
    void startIndexing_submitsToExecutor() {
        when(repoIndexingService.indexAndSave(any(), any()))
                .thenReturn(new RepoIndexingService.IndexingSummary(0, 0));

        // Lambda 는 Mockito 가 spy 할 수 없으므로 카운팅용 wrapper 클래스를 사용한다.
        CountingInlineExecutor counting = new CountingInlineExecutor();
        RepoIndexingTaskService taskService =
                new RepoIndexingTaskService(repoIndexingService, counting);

        taskService.startIndexing(1L);
        assertThat(counting.executeCallCount).isEqualTo(1);
    }

    /** 인라인 실행 + 호출 횟수 카운트를 겸하는 간단한 Executor. */
    private static final class CountingInlineExecutor implements Executor {
        int executeCallCount = 0;
        @Override
        public void execute(Runnable command) {
            executeCallCount++;
            command.run();
        }
    }
}
