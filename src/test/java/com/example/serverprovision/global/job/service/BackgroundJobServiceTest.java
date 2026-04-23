package com.example.serverprovision.global.job.service;

import com.example.serverprovision.global.job.BackgroundJob;
import com.example.serverprovision.global.job.JobStage;
import com.example.serverprovision.global.job.enums.JobStatus;
import com.example.serverprovision.global.job.enums.JobType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * BackgroundJobService 단위 테스트.
 * register / report / complete / fail / dismiss / snapshot 정렬 / prune 2 조건 시나리오를 검증한다.
 */
class BackgroundJobServiceTest {

    private BackgroundJobService service;

    /** 테스트 스테이지 — inner enum 을 써야 하는 도메인 없이 단독 검증. */
    enum TestStage implements JobStage {
        WORK("작업 중", 50);
        private final String label;
        private final int percent;
        TestStage(String label, int percent) { this.label = label; this.percent = percent; }
        @Override public String label() { return label; }
        @Override public int percent() { return percent; }
    }

    @BeforeEach
    void setUp() {
        service = new BackgroundJobService();
        // @Value 주입 대체
        ReflectionTestUtils.setField(service, "maxCount", 100);
        ReflectionTestUtils.setField(service, "keepAfterTerminalMs", 600_000L);
    }

    @Test
    @DisplayName("register: 새 Job 이 PENDING 상태로 저장되고 snapshot 에 포함된다")
    void register_storesPendingJob() {
        String id = service.register(JobType.COMPS_EXTRACTION, "추출", "/iso/a.iso");

        BackgroundJob j = service.find(id).orElseThrow();
        assertThat(j.getStatus()).isEqualTo(JobStatus.PENDING);
        assertThat(j.getTitle()).isEqualTo("추출");
        assertThat(j.getSubtitle()).isEqualTo("/iso/a.iso");
        assertThat(service.snapshot()).hasSize(1);
    }

    @Test
    @DisplayName("report(JobStage): PENDING → RUNNING 전이 + label/percent 반영")
    void reportTransitionsAndAppliesStage() {
        String id = service.register(JobType.COMPS_EXTRACTION, "추출", "/iso/a.iso");
        service.report(id, TestStage.WORK);

        BackgroundJob j = service.find(id).orElseThrow();
        assertThat(j.getStatus()).isEqualTo(JobStatus.RUNNING);
        assertThat(j.getProgress().stageLabel()).isEqualTo("작업 중");
        assertThat(j.getProgress().percent()).isEqualTo(50);
    }

    @Test
    @DisplayName("report(JobStage, int, String): percent override 가 적용된다")
    void reportWithPercentOverride() {
        String id = service.register(JobType.REPO_INDEXING, "인덱싱", "/repo/x");
        service.report(id, TestStage.WORK, 72, "5 MB / 7 MB");

        BackgroundJob j = service.find(id).orElseThrow();
        assertThat(j.getProgress().percent()).isEqualTo(72);
        assertThat(j.getProgress().message()).isEqualTo("5 MB / 7 MB");
    }

    @Test
    @DisplayName("complete: 상태 COMPLETED, completedAt 세팅, percent 100")
    void completeSetsTerminal() {
        String id = service.register(JobType.COMPS_EXTRACTION, "추출", "/iso/a.iso");
        service.complete(id);

        BackgroundJob j = service.find(id).orElseThrow();
        assertThat(j.getStatus()).isEqualTo(JobStatus.COMPLETED);
        assertThat(j.getCompletedAt()).isNotNull();
        assertThat(j.getProgress().percent()).isEqualTo(100);
    }

    @Test
    @DisplayName("fail: 상태 FAILED + errorMessage 저장, completedAt 세팅")
    void failSetsFailedAndErrorMessage() {
        String id = service.register(JobType.COMPS_EXTRACTION, "추출", "/iso/a.iso");
        service.report(id, TestStage.WORK);
        service.fail(id, "disk full");

        BackgroundJob j = service.find(id).orElseThrow();
        assertThat(j.getStatus()).isEqualTo(JobStatus.FAILED);
        assertThat(j.getErrorMessage()).isEqualTo("disk full");
        assertThat(j.getCompletedAt()).isNotNull();
    }

    @Test
    @DisplayName("snapshot: createdAt 역순(최신 먼저) 으로 정렬된다")
    void snapshotOrderedByCreatedAtDesc() throws InterruptedException {
        String a = service.register(JobType.COMPS_EXTRACTION, "A", null);
        Thread.sleep(5);
        String b = service.register(JobType.COMPS_EXTRACTION, "B", null);
        Thread.sleep(5);
        String c = service.register(JobType.COMPS_EXTRACTION, "C", null);

        List<BackgroundJob> snap = service.snapshot();
        assertThat(snap).extracting(BackgroundJob::getId).containsExactly(c, b, a);
    }

    @Nested
    @DisplayName("dismiss")
    class Dismiss {

        @Test
        @DisplayName("종료 Job 은 목록에서 제거된다")
        void removesTerminalJob() {
            String id = service.register(JobType.COMPS_EXTRACTION, "추출", null);
            service.complete(id);

            service.dismiss(id);

            assertThat(service.find(id)).isEmpty();
        }

        @Test
        @DisplayName("활성(RUNNING) Job 은 dismiss 해도 제거되지 않는다")
        void keepsActiveJob() {
            String id = service.register(JobType.COMPS_EXTRACTION, "추출", null);
            service.report(id, TestStage.WORK);

            service.dismiss(id);

            assertThat(service.find(id)).isPresent();
        }

        @Test
        @DisplayName("존재하지 않는 jobId 는 조용히 무시된다")
        void ignoresUnknownJobId() {
            service.dismiss("no-such-id"); // 예외 없이 반환
            assertThat(service.snapshot()).isEmpty();
        }
    }

    @Nested
    @DisplayName("prune")
    class Prune {

        @Test
        @DisplayName("keep-after-terminal 을 넘긴 종료 Job 은 삭제된다")
        void removesExpiredTerminalJobs() {
            ReflectionTestUtils.setField(service, "keepAfterTerminalMs", 1_000L);

            String oldId = service.register(JobType.COMPS_EXTRACTION, "old", null);
            service.complete(oldId);

            // 강제로 completedAt 을 오래전으로 조작
            BackgroundJob oldJob = service.find(oldId).orElseThrow();
            ReflectionTestUtils.setField(oldJob, "completedAt", Instant.now().minusSeconds(5));

            String freshId = service.register(JobType.COMPS_EXTRACTION, "fresh", null);
            service.complete(freshId);

            service.prune(Instant.now());

            assertThat(service.find(oldId)).isEmpty();
            assertThat(service.find(freshId)).isPresent();
        }

        @Test
        @DisplayName("max-count 초과 시 종료 Job 중 오래된 순으로 삭제된다")
        void removesOldestTerminalWhenOverMaxCount() {
            ReflectionTestUtils.setField(service, "maxCount", 2);

            String a = service.register(JobType.COMPS_EXTRACTION, "A", null);
            service.complete(a);
            ReflectionTestUtils.setField(service.find(a).orElseThrow(),
                    "completedAt", Instant.now().minusSeconds(30));

            String b = service.register(JobType.COMPS_EXTRACTION, "B", null);
            service.complete(b);
            ReflectionTestUtils.setField(service.find(b).orElseThrow(),
                    "completedAt", Instant.now().minusSeconds(20));

            String c = service.register(JobType.COMPS_EXTRACTION, "C", null);
            service.complete(c);
            ReflectionTestUtils.setField(service.find(c).orElseThrow(),
                    "completedAt", Instant.now().minusSeconds(10));

            // 3 개 중 max-count 2 초과 → 가장 오래된 a 가 제거된다
            service.prune(Instant.now());

            assertThat(service.find(a)).isEmpty();
            assertThat(service.find(b)).isPresent();
            assertThat(service.find(c)).isPresent();
        }

        @Test
        @DisplayName("max-count 초과해도 활성 Job 은 건드리지 않는다")
        void doesNotRemoveActiveJobs() {
            ReflectionTestUtils.setField(service, "maxCount", 1);

            String active = service.register(JobType.COMPS_EXTRACTION, "active", null);
            service.report(active, TestStage.WORK);

            String done = service.register(JobType.COMPS_EXTRACTION, "done", null);
            service.complete(done);

            // 2 개 > max 1 → 종료된 done 1 개만 제거, active 는 유지
            service.prune(Instant.now());

            assertThat(service.find(active)).isPresent();
            assertThat(service.find(done)).isEmpty();
        }
    }
}
