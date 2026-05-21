package com.example.serverprovision.global.job.dto.response;

/**
 * Job 시작을 트리거한 엔드포인트의 공통 응답.
 * 프론트엔드는 이 jobId 를 들고 알림 패널에서 해당 Job 을 추적하거나, 업로드 XHR 의 progress 보고에 동봉한다.
 */
public record JobStartResponse(String jobId) {

}
