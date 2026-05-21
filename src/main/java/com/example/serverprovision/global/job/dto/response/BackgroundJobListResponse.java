package com.example.serverprovision.global.job.dto.response;

import java.util.List;

/**
 * 알림 패널 폴링용 Job 목록 응답. 프론트가 {@code response.jobs} 로 접근한다.
 */
public record BackgroundJobListResponse(List<BackgroundJobResponse> jobs) {

}
