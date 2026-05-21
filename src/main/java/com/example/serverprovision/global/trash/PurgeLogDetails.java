package com.example.serverprovision.global.trash;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

/**
 * S5-2-4 — purge_log.details JSON 컬럼의 polymorphic payload.
 *
 * <p>outcome 별로 다른 키 셋을 응집 — sealed interface + 2 record + Jackson 3 의
 * {@link JsonTypeInfo} type discriminator. CLAUDE.md §기술 스택 : 어노테이션은
 * {@code com.fasterxml.jackson.annotation.*}, 런타임은 {@code tools.jackson.*}.</p>
 *
 * <p>{@code displayName} 은 v3-1 결정에 따라 entity 의 독립 컬럼으로 발탁되어
 * 본 record 에서 제외.</p>
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes(
		{
				@JsonSubTypes.Type(value = PurgeLogDetails.Success.class, name = "SUCCESS"),
				@JsonSubTypes.Type(value = PurgeLogDetails.Failed.class, name = "FAILED")
		}
)
public sealed interface PurgeLogDetails permits PurgeLogDetails.Success, PurgeLogDetails.Failed {

	/**
	 * SUCCESS 행의 상세. snapshot 값들이 자원 hard-delete 후에도 회고 가능하도록 응집.
	 *
	 * @param triggeredBy     사용자 진입 시 호출자 id. TTL_AUTO 는 null
	 * @param trashedPath     trash root 내 실제 파일 경로 스냅샷 (메타 자원은 null)
	 * @param originalPath    soft-delete 직전 원래 경로 스냅샷
	 * @param manifestHash    마커 보유 자원의 SHA-256 (없으면 null)
	 * @param markerSignature 마커 HMAC 서명 (없으면 null)
	 * @param attemptCount    이번 cron tick 안의 retry 횟수 (1~retry_max_attempts)
	 */
	record Success(
			String triggeredBy,
			String trashedPath,
			String originalPath,
			String manifestHash,
			String markerSignature,
			int attemptCount
	) implements PurgeLogDetails {

	}


	/**
	 * FAILED 행의 상세. retry 누적 카운트는 attemptNumber, 본 cron tick 의 시도 횟수는 tickAttemptCount.
	 *
	 * @param triggeredBy      사용자 진입 시 호출자 id. TTL_AUTO 는 null
	 * @param attemptNumber    자원의 누적 FAILED count + 1 (cron tick 단위로 누적)
	 * @param tickAttemptCount 이번 cron tick 안의 retry 시도 횟수 (1~retry_max_attempts)
	 * @param exceptionClass   원인 Exception class simpleName
	 * @param failureReason    Exception message (절단됨, 512자 한도는 entity 단에서)
	 * @param stackHash        동일 exception 그룹핑 보조 (선택, null 가능)
	 */
	record Failed(
			String triggeredBy,
			int attemptNumber,
			int tickAttemptCount,
			String exceptionClass,
			String failureReason,
			String stackHash
	) implements PurgeLogDetails {

	}
}
