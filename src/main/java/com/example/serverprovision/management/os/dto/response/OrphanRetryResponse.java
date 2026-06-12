package com.example.serverprovision.management.os.dto.response;

/**
 * 오펀 ISO 재시도 결과 — 새로 시작된 등록 background job 과 추적용 redirect.
 */
public record OrphanRetryResponse(String jobId, String redirect) {
}
