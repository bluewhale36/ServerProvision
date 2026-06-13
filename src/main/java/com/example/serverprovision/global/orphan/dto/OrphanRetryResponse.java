package com.example.serverprovision.global.orphan.dto;

/**
 * 오펀 자원 재시도 결과 — 새로 시작된 등록 background job 과 추적용 redirect.
 *
 * <p>R1-4-4 — {@code management/os/dto/response} 에서 {@code global/orphan/dto} 로 승격(도메인 무관).</p>
 */
public record OrphanRetryResponse(String jobId, String redirect) {
}
