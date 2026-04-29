package com.example.serverprovision.global.job.exception;

import com.example.serverprovision.global.exception.NotFoundException;

/**
 * 주어진 jobId 로 Job 을 찾지 못했을 때 던진다.
 * 완료 후 pruner 가 메모리에서 정리했거나, 서버 재시작으로 유실됐을 가능성 — 둘 다 프론트엔드 입장에선 폴링을 멈춰야 하는 상태다.
 */
public class BackgroundJobNotFoundException extends NotFoundException {

    public BackgroundJobNotFoundException(String jobId) {
        super("배경 작업을 찾을 수 없습니다. jobId=" + jobId);
    }
}
