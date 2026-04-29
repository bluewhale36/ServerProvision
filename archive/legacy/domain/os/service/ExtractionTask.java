package com.example.serverprovision.domain.os.service;

import lombok.Getter;

// comps 추출 비동기 태스크의 상태 — 폴링 엔드포인트에서 JSON 직렬화되어 클라이언트에 반환된다.
// volatile 필드로 백그라운드 스레드의 진행률 업데이트를 HTTP 요청 스레드가 안전하게 읽도록 한다.
@Getter
public class ExtractionTask {

    public enum Status { PENDING, PROCESSING, COMPLETED, FAILED }

    private final String taskId;
    private final Long osMetadataId;

    private volatile Status status = Status.PENDING;
    private volatile String stage = "시작 대기 중";
    private volatile int progress = 0;
    private volatile String message;

    public ExtractionTask(String taskId, Long osMetadataId) {
        this.taskId = taskId;
        this.osMetadataId = osMetadataId;
    }

    public void markProcessing() {
        this.status = Status.PROCESSING;
    }

    // CompsExtractionService.ProgressReporter 구현체로 사용 (메서드 레퍼런스 `task::update`)
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
