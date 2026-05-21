package com.example.serverprovision.management.common.nudge;

import com.example.serverprovision.management.common.nudge.exception.NudgeNotFoundException;
import com.example.serverprovision.management.common.nudge.exception.NudgeSessionExpiredException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * MK2 — nudge_session 메모리 저장소 (결정 #1 권장값).
 *
 * <p>{@link NudgeSession} 인스턴스를 5분 TTL 로 {@link ConcurrentMap} 에 보관한다.
 * {@link Scheduled} pruner 가 1분 간격으로 만료 세션을 회수하며, 만료 시 임시 파일 cleanup 책임은
 * 도메인 Service 가 진다 (본 Registry 는 단순 메모리 진실원만).</p>
 *
 * <p>결정 #3 정합 — PENDING_NUDGE DB row 미생성. confirm 시점에 본 record 의 {@code pendingPayload}
 * 만으로 자원을 ACTIVE 로 영속화한다.</p>
 */
@Slf4j
@Component
public class NudgeRegistry {

	/**
	 * 결정 #1 — 메모리 ConcurrentMap. JVM 재시작 시 모든 nudge 세션 소실 (재업로드 필요).
	 */
	private final ConcurrentMap<UUID, NudgeSession> sessions = new ConcurrentHashMap<>();

	private static final Duration TTL = Duration.ofMinutes(5);

	/**
	 * 새 세션 등록. 호출자는 충돌 후보 발견 시점에 본 메서드 호출 후 응답에 nudgeId 동봉.
	 */
	public NudgeSession register(
			NudgeResourceType resourceType,
			Long boardId,
			java.util.List<Long> conflictTargetIds,
			NudgePayload payload
	) {
		Instant now = Instant.now();
		NudgeSession session = new NudgeSession(
				UUID.randomUUID(),
				resourceType,
				boardId,
				conflictTargetIds,
				payload,
				now,
				now.plus(TTL)
		);
		sessions.put(session.nudgeId(), session);
		return session;
	}

	/**
	 * 세션 조회. 만료 / 부재 시 명시적 도메인 예외.
	 */
	public NudgeSession require(UUID nudgeId) {
		NudgeSession session = sessions.get(nudgeId);
		if (session == null) {
			throw new NudgeNotFoundException(nudgeId);
		}
		if (session.isExpired(Instant.now())) {
			sessions.remove(nudgeId);
			throw new NudgeSessionExpiredException(nudgeId);
		}
		return session;
	}

	/**
	 * confirm 완료 후 세션 제거. {@code resolveNudge} 의 멱등성 차단을 위해 반환값으로 제거 여부 알림.
	 */
	public boolean remove(UUID nudgeId) {
		return sessions.remove(nudgeId) != null;
	}

	/**
	 * TTL pruner — 1분 간격으로 만료 세션 회수. 도메인 Service 가 임시 파일 cleanup 책임.
	 */
	@Scheduled(fixedDelay = 60_000L)
	public void pruneExpired() {
		Instant now = Instant.now();
		int before = sessions.size();
		sessions.entrySet().removeIf(e -> e.getValue().isExpired(now));
		int removed = before - sessions.size();
		if (removed > 0) {
			log.info("[nudge] 만료 세션 {} 건 회수 (활성 {} 건 잔존)", removed, sessions.size());
		}
	}
}
