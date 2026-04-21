package com.example.serverprovision.domain.os.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link RepoIndexingTask} 상태 전이 단위 테스트.
 *
 * <p>PENDING → PROCESSING → COMPLETED/FAILED 경로와 progress·stage 필드가
 * 기대대로 변경되는지 회귀 검증한다.</p>
 */
class RepoIndexingTaskTest {

    @Test
    @DisplayName("초기 상태는 PENDING + progress 0 + stage='시작 대기 중'")
    void initialState() {
        RepoIndexingTask task = new RepoIndexingTask("task-id", 1L);

        assertThat(task.getTaskId()).isEqualTo("task-id");
        assertThat(task.getOsMetadataId()).isEqualTo(1L);
        assertThat(task.getStatus()).isEqualTo(RepoIndexingTask.Status.PENDING);
        assertThat(task.getProgress()).isZero();
        assertThat(task.getStage()).isEqualTo("시작 대기 중");
        assertThat(task.getMessage()).isNull();
    }

    @Test
    @DisplayName("markProcessing 호출 시 PROCESSING 상태로 전이한다")
    void markProcessing_transitionsStatus() {
        RepoIndexingTask task = new RepoIndexingTask("t", 1L);
        task.markProcessing();
        assertThat(task.getStatus()).isEqualTo(RepoIndexingTask.Status.PROCESSING);
    }

    @Test
    @DisplayName("update 는 stage 와 progress 만 변경하고 status 는 유지한다")
    void update_changesStageAndProgressOnly() {
        RepoIndexingTask task = new RepoIndexingTask("t", 1L);
        task.markProcessing();
        task.update("패키지 파싱 중", 42);

        assertThat(task.getStage()).isEqualTo("패키지 파싱 중");
        assertThat(task.getProgress()).isEqualTo(42);
        assertThat(task.getStatus()).isEqualTo(RepoIndexingTask.Status.PROCESSING);
    }

    @Test
    @DisplayName("complete 는 COMPLETED + progress 100 + stage='완료' 로 전환하고 메시지를 저장한다")
    void complete_setsCompletionState() {
        RepoIndexingTask task = new RepoIndexingTask("t", 1L);
        task.markProcessing();
        task.update("중간 단계", 50);

        task.complete("2개 패키지, 1개 서비스");

        assertThat(task.getStatus()).isEqualTo(RepoIndexingTask.Status.COMPLETED);
        assertThat(task.getProgress()).isEqualTo(100);
        assertThat(task.getStage()).isEqualTo("완료");
        assertThat(task.getMessage()).isEqualTo("2개 패키지, 1개 서비스");
    }

    @Test
    @DisplayName("fail 은 FAILED 로 전환하고 에러 메시지를 저장하지만 progress/stage 는 이전 상태를 유지한다")
    void fail_setsFailureMessageButKeepsStage() {
        RepoIndexingTask task = new RepoIndexingTask("t", 1L);
        task.markProcessing();
        task.update("일부 진행", 70);

        task.fail("파싱 실패: xxx");

        assertThat(task.getStatus()).isEqualTo(RepoIndexingTask.Status.FAILED);
        assertThat(task.getMessage()).isEqualTo("파싱 실패: xxx");
        // stage / progress 는 리셋되지 않음 — 실패 발생 위치의 단계 정보를 유지한다.
        assertThat(task.getProgress()).isEqualTo(70);
        assertThat(task.getStage()).isEqualTo("일부 진행");
    }
}
