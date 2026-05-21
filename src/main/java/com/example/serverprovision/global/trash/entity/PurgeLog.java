package com.example.serverprovision.global.trash.entity;

import com.example.serverprovision.global.marker.ResourceType;
import com.example.serverprovision.global.trash.PurgeLogDetails;
import com.example.serverprovision.global.trash.PurgeRequest;
import com.example.serverprovision.global.trash.enums.PurgeOrigin;
import com.example.serverprovision.global.trash.enums.PurgeOutcome;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * S5-2-4 — 단일 hard-delete 사건 로그 (시간순 누적). 자원의 lifecycle 회고 + 감사 + 실패 자원 추적
 * 1테이블 통합. plan v3 + v3-1 사용자 결정.
 *
 * <p><strong>NotBaseTimeEntity</strong> : {@code created_at}/{@code updated_at} 보다
 * {@link #occurredAt} / {@link #purgedAt} 가 도메인 의미를 직접 표현하고, INSERT only 라
 * modified_at 도 무의미하므로 {@code BaseTimeEntity} 미상속.</p>
 *
 * <p><strong>FK 없음</strong> : {@link #resourceId} 는 자원 테이블에 FK 를 걸지 않는다 —
 * 자원 hard-delete 후에도 로그 보존되도록 의도된 비-FK 설계.</p>
 *
 * <table>
 *   <caption>인덱스 3종</caption>
 *   <tr><th>이름</th><th>컬럼</th><th>용도</th></tr>
 *   <tr><td>idx_purge_log_resource</td><td>(resource_type, resource_id, occurred_at)</td><td>자원별 회고</td></tr>
 *   <tr><td>idx_purge_log_outcome_occurred</td><td>(outcome, occurred_at)</td><td>실패 자원 목록 / Pageable 정렬</td></tr>
 *   <tr><td>idx_purge_log_origin</td><td>(origin)</td><td>진입경로 필터 (선택)</td></tr>
 * </table>
 */
@Entity
@Table(
		name = "purge_log",
		indexes = {
				@Index(
						name = "idx_purge_log_resource",
						columnList = "resource_type, resource_id, occurred_at"
				),
				@Index(
						name = "idx_purge_log_outcome_occurred",
						columnList = "outcome, occurred_at"
				),
				@Index(
						name = "idx_purge_log_origin",
						columnList = "origin"
				)
		}
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PurgeLog {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Enumerated(EnumType.STRING)
	@Column(name = "resource_type", nullable = false, length = 32)
	private ResourceType resourceType;

	@Column(name = "resource_id", nullable = false)
	private Long resourceId;

	/**
	 * v3-1 — JSON 에서 컬럼으로 발탁된 항상-존재 자원명. {@code Markable.displayName()} 스냅샷.
	 */
	@Column(name = "display_name", nullable = false, length = 256)
	private String displayName;

	@Enumerated(EnumType.STRING)
	@Column(name = "origin", nullable = false, length = 16)
	private PurgeOrigin origin;

	@Enumerated(EnumType.STRING)
	@Column(name = "outcome", nullable = false, length = 8)
	private PurgeOutcome outcome;

	/**
	 * PurgeExecutor 진입 시각. v3-1 — 명시 TIMESTAMP(6) (µs 정밀도).
	 */
	@Column(name = "occurred_at", nullable = false, columnDefinition = "TIMESTAMP(6)")
	private Instant occurredAt;

	/**
	 * 실제 hard-delete 완료 시각. SUCCESS 일 때만 NOT NULL. v3-1 사용자 결정.
	 */
	@Column(name = "purged_at", columnDefinition = "TIMESTAMP(6)")
	private Instant purgedAt;

	/**
	 * outcome 별로 다른 키 셋. sealed PurgeLogDetails + Jackson 3 type discriminator.
	 */
	@Convert(converter = PurgeLogDetailsConverter.class)
	@Column(name = "details", nullable = false, columnDefinition = "JSON")
	private PurgeLogDetails details;

	@Builder
	private PurgeLog(
			ResourceType resourceType, Long resourceId, String displayName,
			PurgeOrigin origin, PurgeOutcome outcome,
			Instant occurredAt, Instant purgedAt, PurgeLogDetails details
	) {
		this.resourceType = resourceType;
		this.resourceId = resourceId;
		this.displayName = displayName;
		this.origin = origin;
		this.outcome = outcome;
		this.occurredAt = occurredAt;
		this.purgedAt = purgedAt;
		this.details = details;
	}

	/**
	 * 성공 행 합성 팩토리. PurgeExecutor 가 한 cron tick / 1 사용자 호출이 성공으로 끝났을 때 호출.
	 *
	 * @param occurredAt PurgeExecutor 진입 시각
	 * @param purgedAt   scanner.purgeFromTrash 완료 시각 (NOT NULL)
	 */
	public static PurgeLog success(
			PurgeRequest req, String displayName,
			Instant occurredAt, Instant purgedAt,
			PurgeLogDetails.Success details
	) {
		return PurgeLog.builder()
				.resourceType(req.resourceType())
				.resourceId(req.resourceId())
				.displayName(displayName)
				.origin(req.origin())
				.outcome(PurgeOutcome.SUCCESS)
				.occurredAt(occurredAt)
				.purgedAt(purgedAt)
				.details(details)
				.build();
	}

	/**
	 * 실패 행 합성 팩토리. cron tick 내 retry 모두 실패 또는 사용자 진입 1회 실패 시.
	 * purgedAt 은 NULL.
	 */
	public static PurgeLog failure(
			PurgeRequest req, String displayName,
			Instant occurredAt,
			PurgeLogDetails.Failed details
	) {
		return PurgeLog.builder()
				.resourceType(req.resourceType())
				.resourceId(req.resourceId())
				.displayName(displayName)
				.origin(req.origin())
				.outcome(PurgeOutcome.FAILED)
				.occurredAt(occurredAt)
				.purgedAt(null)
				.details(details)
				.build();
	}
}
