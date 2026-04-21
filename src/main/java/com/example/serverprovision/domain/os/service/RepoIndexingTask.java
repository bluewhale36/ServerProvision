package com.example.serverprovision.domain.os.service;

import lombok.Getter;

/**
 * 저장소 인덱싱 비동기 태스크의 상태. 폴링 엔드포인트에서 JSON 직렬화되어 반환된다.
 * 동일한 인터페이스를 {@link RepoIndexingService.ProgressReporter} 로 제공한다.
 */
@Getter
public class RepoIndexingTask {

    public enum Status { PENDING, PROCESSING, COMPLETED, FAILED }

    private final String taskId;
    private final Long osMetadataId;

    private volatile Status status = Status.PENDING;
    private volatile String stage = "시작 대기 중";
    private volatile int progress = 0;
    private volatile String message;

    public RepoIndexingTask(String taskId, Long osMetadataId) {
        this.taskId = taskId;
        this.osMetadataId = osMetadataId;
    }

    public void markProcessing() {
        this.status = Status.PROCESSING;
    }

    public void update(String stage, int progress) {
        this.stage = stage;
        this.progress = progress;
    }

    public void complete(String message) {
        this.status = Status.COMPLETED;
        this.stage = "완료";
        this.progress = 100;
        this.message = message;
    }

    public void fail(String message) {
        this.status = Status.FAILED;
        this.message = message;
    }
}
