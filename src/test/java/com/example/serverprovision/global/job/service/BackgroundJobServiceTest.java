package com.example.serverprovision.global.job.service;

import com.example.serverprovision.global.job.BackgroundJob;
import com.example.serverprovision.global.job.JobStage;
import com.example.serverprovision.global.job.enums.JobStatus;
import com.example.serverprovision.global.job.enums.JobType;
import com.example.serverprovision.global.job.enums.StageStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * BackgroundJobService 단위 테스트 — chunk progress bar 모델.
 * register(stages) / startStage / completeStage / complete / fail / dismiss / snapshot 정렬 / prune.
 */
class BackgroundJobServiceTest {

    private BackgroundJobService service;

    /** 테스트 스테이지 — 2 단계 단순 enum. */
    enum TestStage implements JobStage {
        PREPARE("준비"),
        WORK("작업");

        private final String label;
        TestStage(String label) { this.label = label; }
        @Override public String label() { return label; }
    }

    @BeforeEach
    void setUp() {
        service = new BackgroundJobService();
        ReflectionTestUtils.setField(service, "maxCount", 100);
        ReflectionTestUtils.setField(service, "keepAfterTerminalMs", 600_000L);
    }

    private String register(String title) {
        return service.register(JobType.COMPS_EXTRACTION, title, "subtitle",
                BackgroundJobService.stagesOf(TestStage.values()));
    }

    @Test
    @DisplayName("register : 새 Job 이 PENDING + 모든 stage PENDING 상태")
    void register_storesPendingJob() {
        String id = register("추출");

        BackgroundJob j = service.find(id).orElseThrow();
        assertThat(j.getStatus()).isEqualTo(JobStatus.PENDING);
        assertThat(j.getTitle()).isEqualTo("추출");
        assertThat(j.getStageLabels()).containsExactly("준비", "작업");
        assertThat(j.snapshotStageStatuses()).containsExactly(StageStatus.PENDING, StageStatus.PENDING);
        assertThat(service.snapshot()).hasSize(1);
    }

    @Test
    @DisplayName("startStage : 해당 인덱스 RUNNING + Job RUNNING 전이")
    void startStage_transitionsToRunning() {
        String id = register("추출");

        service.startStage(id, TestStage.PREPARE);

        BackgroundJob j = service.find(id).orElseThrow();
        assertThat(j.getStatus()).isEqualTo(JobStatus.RUNNING);
        assertThat(j.snapshotStageStatuses()).containsExactly(StageStatus.RUNNING, StageStatus.PENDING);
    }

    @Test
    @DisplayName("startStage 다음 단계 : 직전 RUNNING 단계가 자동으로 DONE")
    void startStage_autoMarksPreviousAsDone() {
        String id = register("추출");

        service.startStage(id, TestStage.PREPARE);
        service.startStage(id, TestStage.WORK);

        BackgroundJob j = service.find(id).orElseThrow();
        assertThat(j.snapshotStageStatuses()).containsExactly(StageStatus.DONE, StageStatus.RUNNING);
    }

    @Test
    @DisplayName("complete : 모든 stage DONE + Job COMPLETED + completedAt")
    void completeSetsTerminal() {
        String id = register("추출");
        service.startStage(id, TestStage.PREPARE);
        service.complete(id);

        BackgroundJob j = service.find(id).orElseThrow();
        assertThat(j.getStatus()).isEqualTo(JobStatus.COMPLETED);
        assertThat(j.getCompletedAt()).isNotNull();
        assertThat(j.snapshotStageStatuses()).containsExactly(StageStatus.DONE, StageStatus.DONE);
    }

    @Test
    @DisplayName("fail : 현재 stage ERROR + 이후 stage 는 PENDING 그대로 + Job FAILED")
    void failMarksOnlyCurrentStageAsError() {
        String id = register("추출");
        service.startStage(id, TestStage.PREPARE);
        service.fail(id, "disk full");

        BackgroundJob j = service.find(id).orElseThrow();
        assertThat(j.getStatus()).isEqualTo(JobStatus.FAILED);
        assertThat(j.getErrorMessage()).isEqualTo("disk full");
        assertThat(j.getCompletedAt()).isNotNull();
        assertThat(j.snapshotStageStatuses()).containsExactly(StageStatus.ERROR, StageStatus.PENDING);
    }

    @Test
    @DisplayName("snapshot : createdAt 역순 정렬")
    void snapshotOrderedByCreatedAtDesc() throws InterruptedException {
        String a = register("A");
        Thread.sleep(5);
        String b = register("B");
        Thread.sleep(5);
        String c = register("C");

        List<BackgroundJob> snap = service.snapshot();
        assertThat(snap).extracting(BackgroundJob::getId).containsExactly(c, b, a);
    }

    @Nested
    @DisplayName("dismiss")
    class Dismiss {

        @Test
        @DisplayName("종료 Job 은 목록에서 제거")
        void removesTerminalJob() {
            String id = register("추출");
            service.complete(id);

            service.dismiss(id);

            assertThat(service.find(id)).isEmpty();
        }

        @Test
        @DisplayName("활성(RUNNING) Job 은 dismiss 무시")
        void keepsActiveJob() {
            String id = register("추출");
            service.startStage(id, TestStage.PREPARE);

            service.dismiss(id);

            assertThat(service.find(id)).isPresent();
        }

        @Test
        @DisplayName("존재하지 않는 jobId 는 조용히 무시")
        void ignoresUnknownJobId() {
            service.dismiss("no-such-id");
            assertThat(service.snapshot()).isEmpty();
        }
    }

    @Nested
    @DisplayName("prune")
    class Prune {

        @Test
        @DisplayName("keep-after-terminal 초과 종료 Job 삭제")
        void removesExpiredTerminalJobs() {
            ReflectionTestUtils.setField(service, "keepAfterTerminalMs", 1_000L);

            String oldId = register("old");
            service.complete(oldId);
            BackgroundJob oldJob = service.find(oldId).orElseThrow();
            ReflectionTestUtils.setField(oldJob, "completedAt", Instant.now().minusSeconds(5));

            String freshId = register("fresh");
            service.complete(freshId);

            service.prune(Instant.now());

            assertThat(service.find(oldId)).isEmpty();
            assertThat(service.find(freshId)).isPresent();
        }

        @Test
        @DisplayName("max-count 초과 시 종료 Job 중 오래된 순으로 삭제")
        void removesOldestTerminalWhenOverMaxCount() {
            ReflectionTestUtils.setField(service, "maxCount", 2);

            String a = register("A");
            service.complete(a);
            ReflectionTestUtils.setField(service.find(a).orElseThrow(),
                    "completedAt", Instant.now().minusSeconds(30));

            String b = register("B");
            service.complete(b);
            ReflectionTestUtils.setField(service.find(b).orElseThrow(),
                    "completedAt", Instant.now().minusSeconds(20));

            String c = register("C");
            service.complete(c);
            ReflectionTestUtils.setField(service.find(c).orElseThrow(),
                    "completedAt", Instant.now().minusSeconds(10));

            service.prune(Instant.now());

            assertThat(service.find(a)).isEmpty();
            assertThat(service.find(b)).isPresent();
            assertThat(service.find(c)).isPresent();
        }

        @Test
        @DisplayName("max-count 초과해도 활성 Job 은 prune 대상에서 제외")
        void doesNotRemoveActiveJobs() {
            // max-count=1 + 활성 1 + 종료 1 — 종료 1 만 삭제되고 활성은 유지되어야 한다.
            ReflectionTestUtils.setField(service, "maxCount", 1);

            String active = register("active");
            service.startStage(active, TestStage.WORK);

            String done = register("done");
            service.complete(done);

            service.prune(Instant.now());

            assertThat(service.find(active))
                    .as("활성 Job 은 max-count 와 무관하게 보존")
                    .isPresent();
            assertThat(service.find(done))
                    .as("종료 Job 은 max-count 초과 시 제거 대상")
                    .isEmpty();
        }
    }
}
