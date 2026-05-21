package com.example.serverprovision.management.common.nudge;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * MK2 — 충돌이 발견되어 사용자 confirm 을 기다리는 임시 세션.
 *
 * <p>결정 박스 #1 (CP1 v3) 에 따라 단일 인스턴스 메모리 보관. {@link NudgeRegistry} 가
 * {@code ConcurrentMap<UUID, NudgeSession>} + 5분 TTL 로 관리한다. 5분 만료된 세션은
 * {@code @Scheduled} pruner 가 임시 파일 cleanup (도메인 책임) 후 제거.</p>
 *
 * <p>WAVE 2 — {@link #payload} 가 sealed {@link NudgePayload} 로 일반화되어 단계 A (intent 메타) /
 * 단계 B (해시 충돌) 두 phase 를 같은 record 로 표현. 도메인 NudgeService 가 pattern matching 으로 분기.</p>
 *
 * @param nudgeId           UUID v4
 * @param resourceType      어느 도메인 컨트롤러가 발급한 세션인지
 * @param boardId           BIOS/BMC/Subprogram 용 보드 id (OS/ISO 의 경우 osImageId 로 의미 전환). null 가능 (Subprogram common scope, OSImage / Board 메타 nudge)
 * @param conflictTargetIds 사용자에게 노출할 기존 충돌 후보 자원 id (soft-deleted / Deprecated)
 * @param payload           phase 별 pending 데이터 ({@link ContentNudgePayload} 또는 {@link IntentMetaNudgePayload})
 * @param createdAt         세션 생성 시각
 * @param expiresAt         createdAt + 5분
 */
public record NudgeSession(
		UUID nudgeId,
		NudgeResourceType resourceType,
		Long boardId,
		List<Long> conflictTargetIds,
		NudgePayload payload,
		Instant createdAt,
		Instant expiresAt
) {

	/**
	 * 세션이 만료됐는지 확인. {@code @Scheduled} pruner 와 confirm 핸들러가 함께 사용한다.
	 */
	public boolean isExpired(Instant now) {
		return now.isAfter(expiresAt);
	}
}
