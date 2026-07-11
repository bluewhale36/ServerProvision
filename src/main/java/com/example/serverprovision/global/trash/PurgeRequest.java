package com.example.serverprovision.global.trash;

import com.example.serverprovision.global.marker.ResourceType;
import com.example.serverprovision.global.trash.enums.PurgeOrigin;

/**
 * S5-2-4 — PurgeExecutor 단일 진입점에 전달되는 요청 VO.
 *
 * <p>3 진입경로 ({@link PurgeOrigin#USER_DIRECT} / {@link PurgeOrigin#NUDGE_REPLACE}
 * / {@link PurgeOrigin#TTL_AUTO}) 가 동일 형식으로 요청을 만든다. typed-name 검증은
 * PurgeExecutor 진입 전 (controller / NudgeService 단) 에서 통과 후 호출 — 본 record 의
 * {@code typedName} 은 감사용으로만 보관하고 PurgeExecutor 내부 재검증은 안 함.</p>
 *
 * @param resourceType 자원 도메인
 * @param resourceId   자원 PK
 * @param origin       진입경로
 * @param triggeredBy  사용자 진입 2 경로의 호출자 식별자 (S3 인증 통합 시 user id). TTL_AUTO 는 null
 * @param typedName    사용자가 입력한 자원명. {@link PurgeOrigin#requiresTypedName()} 가 true 인 경우 NOT NULL. TTL_AUTO 는 null
 */
public record PurgeRequest(
		ResourceType resourceType,
		Long resourceId,
		PurgeOrigin origin,
		String triggeredBy,
		String typedName
) {

	/**
	 * TTL_AUTO 시스템 진입용 팩토리 — typedName / triggeredBy 모두 null.
	 */
	public static PurgeRequest forTtlAuto(ResourceType resourceType, Long resourceId) {
		return new PurgeRequest(resourceType, resourceId, PurgeOrigin.TTL_AUTO, null, null);
	}

	/**
	 * S6-2-3 — 점검의 "휴지통 자원 소실" 정리 진입용 팩토리. 사용자 [적용] 확인 후 호출.
	 */
	public static PurgeRequest forDriftTrashLost(ResourceType resourceType, Long resourceId) {
		return new PurgeRequest(resourceType, resourceId, PurgeOrigin.DRIFT_TRASH_LOST, null, null);
	}

	/**
	 * 사용자 직접 진입용 팩토리.
	 */
	public static PurgeRequest forUserDirect(
			ResourceType resourceType, Long resourceId,
			String triggeredBy, String typedName
	) {
		return new PurgeRequest(resourceType, resourceId, PurgeOrigin.USER_DIRECT, triggeredBy, typedName);
	}

	/**
	 * nudge REPLACE 진입용 팩토리 (v3-1 typed-name 추가).
	 */
	public static PurgeRequest forNudgeReplace(
			ResourceType resourceType, Long resourceId,
			String triggeredBy, String typedName
	) {
		return new PurgeRequest(resourceType, resourceId, PurgeOrigin.NUDGE_REPLACE, triggeredBy, typedName);
	}
}
