package com.example.serverprovision.global.job;

import com.example.serverprovision.global.job.enums.JobStatus;
import com.example.serverprovision.global.job.enums.JobType;
import com.example.serverprovision.global.job.enums.StageStatus;
import lombok.Getter;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * 백그라운드 Job 메모리 상태 컨테이너.
 *
 * <p>chunk progress bar 모델 (사용자 결정) :
 * <ul>
 *   <li>등록 시점에 단계 라벨 리스트를 받아 불변 보관 ({@link #stageLabels})</li>
 *   <li>각 단계의 상태는 {@link StageStatus} 4 종으로 표현 — PENDING(grey) / RUNNING(blue) / DONE(green) / ERROR(red)</li>
 *   <li>모든 단계 DONE → Job COMPLETED. 한 단계라도 ERROR → Job FAILED. 그 이후 단계는 PENDING 그대로</li>
 *   <li>진행률 % 계산 / 보고는 더 이상 하지 않는다 — 단계 전환만 이벤트로 보고</li>
 * </ul>
 *
 * <p>비동기 작업 스레드와 폴링 UI 스레드 사이에서 안전하게 교환되도록 가변 필드는 모두 volatile.</p>
 */
@Getter
public class BackgroundJob {

	private final String id;
	private final JobType type;
	private final String title;
	private final String subtitle;
	/**
	 * 도메인별 보조 식별자를 담는 자유 맵. 예: ISO 등록 Job 의 osId. UI 가 타겟 DOM 을 찾는 데 쓴다.
	 */
	private final Map<String, String> metadata;
	/**
	 * 등록 시점에 고정되는 단계 라벨 리스트. 비어있을 수 없다 (단계 없는 Job 은 의미 없음).
	 */
	private final List<String> stageLabels;
	private final Instant createdAt;

	/**
	 * 각 단계의 현재 상태. {@code stageLabels.size()} 와 동일 길이. CopyOnWriteArrayList 가 아닌 일반 List 를
	 * 쓰되 모든 외부 노출은 {@link #snapshotStageStatuses()} 로 방어 복사.
	 * 인덱스 갱신은 같은 스레드에서 순차 진행되므로 단순 동기화 (synchronized 메서드).
	 */
	private final List<StageStatus> stageStatuses;

	private volatile JobStatus status;
	private volatile Instant completedAt;
	private volatile String errorMessage;
	/**
	 * R9-1 — 완료 시점에만 확정되는 결과 수치(예: 스캔의 driftCount, 재발급의 성공/실패 건수).
	 * 등록 시점 {@link #metadata} 와 키가 충돌하면 안 된다 — 등록 키는 도메인 식별자(osId 등),
	 * 결과 키는 결과 수치 전용. 응답 시 {@code BackgroundJobResponse} 가 두 맵을 병합해 내려준다.
	 */
	private volatile Map<String, String> resultMetadata = Map.of();
	/**
	 * 마지막으로 처리 중인 단계 인덱스. 실패 시 그 단계가 ERROR 인 인덱스. -1 = 아직 시작 안 함.
	 */
	private volatile int currentStageIndex;

	public BackgroundJob(
			String id, JobType type, String title, String subtitle,
			List<String> stageLabels, Map<String, String> metadata
	) {
		if (stageLabels == null || stageLabels.isEmpty()) {
			throw new IllegalArgumentException("stageLabels 는 1개 이상이어야 한다.");
		}
		this.id = id;
		this.type = type;
		this.title = title;
		this.subtitle = subtitle;
		this.stageLabels = List.copyOf(stageLabels);
		this.metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
		this.createdAt = Instant.now();
		this.stageStatuses = new ArrayList<>(this.stageLabels.size());
		for (int i = 0; i < this.stageLabels.size(); i++) stageStatuses.add(StageStatus.PENDING);
		this.status = JobStatus.PENDING;
		this.currentStageIndex = -1;
	}

	public Map<String, String> getMetadata() {
		return Collections.unmodifiableMap(metadata);
	}

	/**
	 * 외부 노출용 방어 복사.
	 */
	public synchronized List<StageStatus> snapshotStageStatuses() {
		return List.copyOf(stageStatuses);
	}

	/**
	 * 특정 단계를 RUNNING 으로 진입. 이전 단계가 RUNNING 이면 자동으로 DONE 처리해 묵시적 전환을 허용.
	 */
	public synchronized void startStage(int idx) {
		if (idx < 0 || idx >= stageStatuses.size()) {
			throw new IllegalArgumentException("stage 인덱스 범위 초과 : " + idx);
		}
		// 이전 단계들이 PENDING 인 채로 점프했다면 DONE 으로 메우진 않는다 (호출자 책임).
		// 단 직전에 RUNNING 이던 단계는 자연스러운 단계 전환 시 자동 DONE.
		if (currentStageIndex >= 0 && currentStageIndex < idx
				&& stageStatuses.get(currentStageIndex) == StageStatus.RUNNING) {
			stageStatuses.set(currentStageIndex, StageStatus.DONE);
		}
		stageStatuses.set(idx, StageStatus.RUNNING);
		currentStageIndex = idx;
		if (status == JobStatus.PENDING) status = JobStatus.RUNNING;
	}

	/**
	 * 현재 RUNNING 단계를 DONE 으로 마감. complete() 와 별개 — Job 종료가 아닌 단계 종료.
	 */
	public synchronized void completeCurrentStage() {
		if (currentStageIndex < 0) return;
		if (stageStatuses.get(currentStageIndex) == StageStatus.RUNNING) {
			stageStatuses.set(currentStageIndex, StageStatus.DONE);
		}
	}

	/**
	 * 모든 단계 DONE + Job COMPLETED. 단계가 안 끝났어도 강제 완료 — 다 채워줌.
	 */
	public synchronized void complete() {
		complete(Map.of());
	}

	/**
	 * R9-1 — 완료 결과 수치를 함께 기록하는 완료. 결과 키는 등록 metadata 키와 충돌 금지
	 * ({@link #resultMetadata} 규약 참조).
	 */
	public synchronized void complete(Map<String, String> resultMetadata) {
		this.resultMetadata = resultMetadata == null ? Map.of() : Map.copyOf(resultMetadata);
		for (int i = 0; i < stageStatuses.size(); i++) {
			if (stageStatuses.get(i) != StageStatus.ERROR) {
				stageStatuses.set(i, StageStatus.DONE);
			}
		}
		status = JobStatus.COMPLETED;
		completedAt = Instant.now();
	}

	/**
	 * 현재 단계를 ERROR 로 마킹하고 Job FAILED. 이후 단계는 PENDING 그대로.
	 */
	public synchronized void fail(String message) {
		this.errorMessage = message;
		if (currentStageIndex >= 0) {
			stageStatuses.set(currentStageIndex, StageStatus.ERROR);
		}
		status = JobStatus.FAILED;
		completedAt = Instant.now();
	}
}
