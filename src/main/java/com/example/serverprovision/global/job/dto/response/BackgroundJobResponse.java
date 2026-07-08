package com.example.serverprovision.global.job.dto.response;

import com.example.serverprovision.global.job.BackgroundJob;
import com.example.serverprovision.global.job.enums.JobStatus;
import com.example.serverprovision.global.job.enums.JobType;
import com.example.serverprovision.global.job.enums.StageStatus;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 알림 패널 카드 렌더용 Job 스냅샷 응답.
 * <p>chunk progress bar — {@link #stages} 가 단계별 (label, status) 페어를 차례대로 담는다.
 * 프론트는 각 chunk 의 status 를 색상에 매핑한다 (PENDING=grey, RUNNING=blue, DONE=green, ERROR=red).</p>
 */
public record BackgroundJobResponse(
		String id,
		JobType type,
		String typeLabel,
		String title,
		String subtitle,
		JobStatus status,
		List<StageInfo> stages,
		String errorMessage,
		Instant createdAt,
		Instant completedAt,
		Map<String, String> metadata
) {

	/**
	 * chunk 1개 분량 — 라벨과 현재 상태.
	 */
	public record StageInfo(
			String label,
			StageStatus status
	) {

	}

	public static BackgroundJobResponse from(BackgroundJob job) {
		List<String> labels = job.getStageLabels();
		List<StageStatus> statuses = job.snapshotStageStatuses();
		List<StageInfo> stages = new ArrayList<>(labels.size());
		for (int i = 0; i < labels.size(); i++) {
			stages.add(new StageInfo(labels.get(i), statuses.get(i)));
		}
		return new BackgroundJobResponse(
				job.getId(),
				job.getType(),
				job.getType().getDisplayName(),
				job.getTitle(),
				job.getSubtitle(),
				job.getStatus(),
				stages,
				job.getErrorMessage(),
				job.getCreatedAt(),
				job.getCompletedAt(),
				mergedMetadata(job)
		);
	}

	/**
	 * R9-1 — 등록 metadata ∪ 완료 결과 metadata. 키 충돌 금지 규약(등록 = 도메인 식별자,
	 * 결과 = 완료 수치)이 전제라 순서 무관 병합. 프론트({@code background-jobs.js})는 기존
	 * {@code metadata} 필드 하나만 읽으므로 wire 계약 무변경.
	 */
	private static Map<String, String> mergedMetadata(BackgroundJob job) {
		Map<String, String> result = job.getResultMetadata();
		if (result.isEmpty()) return job.getMetadata();
		Map<String, String> merged = new java.util.HashMap<>(job.getMetadata());
		merged.putAll(result);
		return Map.copyOf(merged);
	}
}
